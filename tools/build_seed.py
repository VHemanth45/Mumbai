#!/usr/bin/env python3
"""
Pass 0b of the seed pipeline: candidate POIs -> assets/mumbai-places.tsv

Two inputs, and a hard line between what each is allowed to decide:

  * `build/mumbai-pois.json` (from `dump_pois.py`, i.e. the OSM extract) —
    every name, every coordinate, every address and every fact in every
    description;
  * `boundary.geojson` — which postal area each place falls in, so a place can
    say where it is without anyone guessing.

**No coordinate is ever written from memory**, and that is the point. The seed
this replaced was typed by hand and put Cafe Mondegar 250 m from where it is,
Mahesh Lunch Home 563 m, and Grandmama's Cafe four kilometres.

`curated.tsv` is the third input and it no longer decides membership, because
nothing does: the catalog is every candidate the extract carries. It still
earns its place, because resolving those names against OSM is what keeps a
handful of entries that the postal boundary would otherwise drop, and the
`CURATED_BONUS` puts the places a person chose at the top of their category.

What is left here is data hygiene and ordering. See `CATEGORY_ORDER` for why
the caps went, and `score()` for what still ranks a category's list.

Usage:  .venv-osm/bin/python3 tools/build_seed.py [--report]
"""

from __future__ import annotations

import difflib
import hashlib
import json
import math
import re
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from mumbai_boundary import Areas  # noqa: E402

POIS_PATH = Path("build/mumbai-pois.json")
CURATED_PATH = Path("tools/curated.tsv")
OUT_PATH = Path("app/src/main/assets/mumbai-places.tsv")

# How far from a curated entry's hint coordinate an OSM record may be and still
# be the same place. Generous, because the hints are what this pipeline exists
# to replace — they were out by up to 2.6 km — but tight enough that it cannot
# match a same-named place in another suburb.
CURATED_MATCH_METRES = 3_000.0

# Added to a curated place's score. Only decides ordering and which copy of a
# double-mapped place survives dedupe — being curated is what gets it in.
CURATED_BONUS = 1_000.0

# Shortest name either side of a loose containment match may be.
LOOSE_MATCH_CHARS = 6

# For a containment match, how much of the longer name the shorter one has to
# be. "Girgaon" is 44% of "Girgaon Chowpatty" and is a different place.
LOOSE_CONTAINMENT_RATIO = 0.7

# The catalog is uncapped: every POI candidate the extract carries is shipped.
#
# This used to be a curated list of 177 with a per-category cap behind it, and
# the reasoning was that OSM cannot tell a great Mumbai restaurant from an
# adequate one, so shipping all of them turns the map into wallpaper. That is
# still true of *ranking*. It stopped being the right trade once the app let
# people add their own places: if the catalog is missing the place you went to,
# you should find it already there rather than have to type it in.
#
# So what survives below is data hygiene — no nameless rows, no place mapped
# twice, nothing outside Mumbai — and nothing that is a judgement about whether
# a place is worth visiting. `score()` still runs, because it decides which copy
# of a double-mapped place is kept and what order a category is listed in.
#
# The order here is the order categories appear in the shipped file.
CATEGORY_ORDER = ["TOURIST", "RESTAURANT", "CAFE", "CULTURE", "PARK", "HIDDEN_GEM"]

# Two POIs with the same name this close together are the same place mapped
# twice — typically a node inside its own building polygon.
#
# Distance is the whole rule now. The old one kept a single entry per name
# across the entire city, on the grounds that a second "Shiv Mandir" three
# suburbs away is not a different destination. With chains in the catalog that
# rule deletes the city: it would keep one Starbucks and drop the other
# thirty-five, and the one it kept would be wherever the score landed.
DEDUPE_METRES = 150.0

# How far outside the postal polygons still counts as Mumbai. Sized to close
# the gaps between delivery rounds, not to annex the next district.
BOUNDARY_TOLERANCE_M = 250.0

# ---------------------------------------------------------------------------
# Chains
# ---------------------------------------------------------------------------
#
# A franchise outlet is not somewhere you explore a city to find. Domino's is
# mapped 37 times inside the box, Starbucks 36, Cafe Coffee Day 34, and a
# catalog that lists all of them is a directory of the same shop.
#
# Repetition alone cannot be the rule, and this is the trap: OSM also maps 15
# "Hanuman Mandir", 4 "BMC Park" and 3 "Anthill", and none of those is a branch
# of anything. They repeat because the name is generic, which is the opposite of
# a franchise — each one is a different place that happens to share a word.
#
# So repetition only counts as evidence where a franchise is the thing it is
# evidence *of*: somewhere that sells you something.
CHAIN_CATEGORIES = {"CAFE", "RESTAURANT"}

# A name at this many separate sites, within [CHAIN_CATEGORIES], is a chain.
CHAIN_REPEATS = 3

# Occurrences closer together than this are the same site mapped more than
# once, not another branch. Elephanta Caves is mapped three times over — a node
# and two ways for one rock — which a naive count reads as a three-branch chain.
CHAIN_SITE_METRES = 500.0

# Shortest chain key that may be matched loosely. "kfc" is three characters and
# half the alphabet is within an edit or two of it, so short keys only ever
# match exactly — which costs nothing, because every real KFC in the extract
# carries a `brand` tag anyway.
MIN_LOOSE_CHAIN_CHARS = 5

# How alike a name has to be to a chain key to be the same chain.
#
# This exists because the long tail of a franchise is misspelled. The extract
# has thirty-four `Domino's` carrying a brand tag and then, with no brand tag
# and one occurrence each: `Dominos`, `Dominoes`, `Dominos pizza` and
# `Domino's Piza`. Same for `Caffe Coffee Day`, `Baskin Robins`, `McDonalds`
# and `Starbucks coffee`. Counting occurrences never finds those; comparing
# them to a name the count *did* find does.
CHAIN_NAME_RATIO = 0.87

# Only compare names of comparable length. Without this, a long name that
# happens to contain a chain key scores well against it.
CHAIN_NAME_SLACK = 3

# ---------------------------------------------------------------------------
# Classification
# ---------------------------------------------------------------------------

CULTURE_TOURISM = {"museum", "gallery"}
CULTURE_AMENITY = {"theatre", "arts_centre", "library", "cinema", "planetarium"}
TOURIST_TOURISM = {"attraction", "viewpoint", "zoo", "aquarium", "theme_park"}
TOURIST_HISTORIC = {"monument", "memorial", "castle", "fort"}
TOURIST_MANMADE = {"lighthouse", "obelisk"}
PARK_LEISURE = {"park", "garden", "nature_reserve"}
CAFE_AMENITY = {"cafe", "ice_cream"}
CAFE_SHOP = {"bakery", "coffee", "tea", "confectionery", "chocolate"}
RESTAURANT_AMENITY = {"restaurant", "fast_food", "bar", "pub", "biergarten"}


def has_article(tags) -> bool:
    """Does anyone consider this notable enough to write an encyclopaedia entry?"""
    return bool(tags.get("wikidata") or tags.get("wikipedia"))


def chain_key(name: str | None) -> str:
    """A name reduced to the letters and digits in it, for comparing."""
    text = unicodedata.normalize("NFKD", name or "").encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]", "", text.lower())


def chain_keys(pois) -> set[str]:
    """
    The franchises in the extract, found two ways and from the data alone.

    **Branded.** `brand` and `brand:wikidata` are the strongest signal there is,
    because a mapper sets them to say exactly this: the shop in front of me is
    an outlet of a named brand. Both the brand and the name go in, so the set
    holds `starbucks` whether the mapper wrote it in one field or the other.

    **Repeated.** A name at [CHAIN_REPEATS] separate sites is a chain even
    where nobody tagged a brand. Counting rows would be wrong — a place mapped
    as a node inside its own building polygon is two rows and one place — so
    occurrences are clustered by distance first and only distinct sites count.

    Both are scoped to the food categories before counting, so a generic temple
    name can never enter this set at all. See [CHAIN_CATEGORIES].
    """
    keys: set[str] = set()
    by_name: dict[str, list[tuple[float, float]]] = defaultdict(list)

    for poi in pois:
        tags = poi["tags"]
        if categorize(tags) not in CHAIN_CATEGORIES:
            continue

        if tags.get("brand") or tags.get("brand:wikidata"):
            for value in (tags.get("brand"), tags.get("name"), tags.get("name:en")):
                key = chain_key(value)
                if key:
                    keys.add(key)

        name = chain_key(tags.get("name") or tags.get("name:en"))
        if name:
            by_name[name].append((poi["lat"], poi["lon"]))

    for name, points in by_name.items():
        if len(points) < CHAIN_REPEATS:
            continue
        sites: list[tuple[float, float]] = []
        for lat, lon in points:
            if not any(
                metres_between(lat, lon, s_lat, s_lon) < CHAIN_SITE_METRES
                for s_lat, s_lon in sites
            ):
                sites.append((lat, lon))
            if len(sites) >= CHAIN_REPEATS:
                keys.add(name)
                break

    return keys


def is_chain(name: str, tags, category: str | None, keys: set[str]) -> bool:
    """
    Whether [name] is an outlet of one of the franchises in [keys].

    Exact first, then a prefix, then near-alike — see [CHAIN_NAME_RATIO] for
    why the last one has to exist. A name that merely *contains* a chain key is
    deliberately not matched: "Crafters above KFC" is a bar above a KFC and is
    somewhere to go.
    """
    if category not in CHAIN_CATEGORIES:
        return False
    if tags.get("brand") or tags.get("brand:wikidata"):
        return True

    key = chain_key(name)
    if not key:
        return False
    if key in keys:
        return True

    for chain in keys:
        if len(chain) < MIN_LOOSE_CHAIN_CHARS:
            continue
        # "Starbucks Hiranandani Meadows", "Domino's Pizza", "CCD Express" —
        # the brand, then which one. Still the brand.
        if key.startswith(chain):
            return True
        if abs(len(key) - len(chain)) <= CHAIN_NAME_SLACK and (
            difflib.SequenceMatcher(None, key, chain).ratio() >= CHAIN_NAME_RATIO
        ):
            return True
    return False


def categorize(tags) -> str | None:
    """
    The app category for a POI. Never None for a candidate any more.

    The catalog ships everything the extract flagged, so this can no longer be
    a filter as well as a classifier — a POI that matches none of the rules
    below used to be dropped as "uncategorised", and now falls through to
    HIDDEN_GEM. The six categories are a fixed enum on the Kotlin side, so
    everything has to land in one of them.
    """
    amenity = tags.get("amenity")
    tourism = tags.get("tourism")
    leisure = tags.get("leisure")
    historic = tags.get("historic")
    natural = tags.get("natural")
    shop = tags.get("shop")
    man_made = tags.get("man_made")

    # Unambiguous cultural venues first — a theatre is a theatre even if
    # someone has also tagged it an attraction.
    if tourism in CULTURE_TOURISM or amenity in CULTURE_AMENITY:
        return "CULTURE"

    # Public art is a thing you go and look at, and Mumbai has 76 of it mapped.
    if tourism == "artwork":
        return "CULTURE"

    if tourism in TOURIST_TOURISM:
        return "TOURIST"
    if historic in TOURIST_HISTORIC:
        return "TOURIST"
    if natural == "beach" or man_made in TOURIST_MANMADE:
        return "TOURIST"
    if amenity == "marketplace" or leisure == "stadium":
        return "TOURIST"

    # All 831 mapped places of worship ship now. The ones the data says draw
    # visitors stay in TOURIST; the rest are the neighbourhood shrine on a lane
    # you would only find by walking down it, which is what HIDDEN_GEM is for —
    # and which keeps TOURIST from becoming nine-tenths temples.
    if amenity == "place_of_worship":
        return "TOURIST" if (has_article(tags) or historic) else "HIDDEN_GEM"

    if (
        leisure in PARK_LEISURE
        or tourism == "picnic_site"
        or tags.get("boundary") in ("national_park", "protected_area")
    ):
        return "PARK"

    if amenity in CAFE_AMENITY or shop in CAFE_SHOP:
        return "CAFE"
    if amenity in RESTAURANT_AMENITY:
        return "RESTAURANT"

    # What is left of `historic`, plus natural oddities and everything the
    # rules above did not name. A hidden gem is something mapped as heritage or
    # as a landform that nobody has written an article about — anything with an
    # article went to TOURIST above, which is what stops this category from
    # filling up with the famous forts.
    #
    # GEM_HISTORIC used to be an allowlist because `historic=industrial` marks
    # Mumbai's mill lands, and the malls and towers built on them are not
    # heritage. They are still places you can walk into, so with the catalog
    # uncapped they come too, described by what the tag actually says.
    return "HIDDEN_GEM"


# ---------------------------------------------------------------------------
# Scoring
# ---------------------------------------------------------------------------

def score(poi, category: str, in_city: bool) -> float:
    """
    How strong a candidate this is, from evidence in the data alone.

    The weights say: an encyclopaedia entry is worth more than any amount of
    mapper detail; detail is worth more than nothing; and for a park, size is
    worth more than either, because 717 mapped parks run from a traffic island
    to a nature reserve and nothing else in the data separates them.
    """
    tags = poi["tags"]
    total = 0.0

    if tags.get("wikidata"):
        total += 40
    if tags.get("wikipedia"):
        total += 40
    if tags.get("heritage") or tags.get("heritage:operator"):
        total += 25
    if tags.get("tourism") == "attraction":
        total += 15
    if tags.get("start_date"):
        total += 8

    # Mapper effort: someone standing in front of the place filled these in.
    for key, points in (
        ("website", 8), ("opening_hours", 6), ("phone", 5), ("cuisine", 5),
        ("operator", 4), ("wheelchair", 3), ("addr:street", 3), ("description", 2),
    ):
        if tags.get(key):
            total += points

    total += min(10.0, len(tags) / 2.0)

    if category == "PARK":
        total += min(35.0, math.log10(max(poi["area_m2"], 1)) * 7.0)
    elif poi["area_m2"] > 0:
        # Elsewhere, being mapped as a building at all is mild evidence of care.
        total += 3

    # Inside the postal boundary means unambiguously in Mumbai rather than in
    # the strip of Thane and Navi Mumbai the map's bounding box also covers.
    if in_city:
        total += 10

    return total


# ---------------------------------------------------------------------------
# Naming and description
# ---------------------------------------------------------------------------

def english_name(tags) -> str | None:
    """
    The name to show. Latin script only — the UI and its search box are English.

    Also the crudest quality filter there is, and the most effective one: a
    name that does not begin with a capital is, in this dataset, almost always
    a half-finished entry ("tastress", "cafe coffee", "vidhi'") rather than a
    place someone would recognise.
    """
    for key in ("name:en", "int_name", "name"):
        value = (tags.get(key) or "").strip()
        if not value or any(ord(ch) > 0x24F for ch in value):
            continue
        # "Asiatic Society Town Hall;Asiatic Society Museum" is two names in
        # one field; the first is the one to use.
        value = re.sub(r"\s+", " ", value.split(";")[0]).strip()
        if len(value) < 3 or not value[0].isupper():
            continue
        return value
    return None


NOUNS = [
    # (tag key, value, noun) — first match wins, so the specific ones lead.
    ("boundary", "national_park", "National park"),
    ("leisure", "nature_reserve", "Nature reserve"),
    ("tourism", "museum", "Museum"),
    ("tourism", "gallery", "Gallery"),
    ("tourism", "zoo", "Zoo"),
    ("tourism", "aquarium", "Aquarium"),
    ("tourism", "theme_park", "Theme park"),
    ("tourism", "viewpoint", "Viewpoint"),
    ("tourism", "artwork", "Public artwork"),
    ("amenity", "theatre", "Theatre"),
    ("amenity", "cinema", "Cinema"),
    ("amenity", "library", "Library"),
    ("amenity", "arts_centre", "Arts centre"),
    ("amenity", "planetarium", "Planetarium"),
    ("amenity", "marketplace", "Market"),
    ("amenity", "place_of_worship", "Place of worship"),
    ("amenity", "cafe", "Cafe"),
    ("amenity", "ice_cream", "Ice cream parlour"),
    ("amenity", "restaurant", "Restaurant"),
    ("amenity", "fast_food", "Fast food"),
    ("amenity", "bar", "Bar"),
    ("amenity", "pub", "Pub"),
    ("shop", "bakery", "Bakery"),
    ("shop", "coffee", "Coffee shop"),
    ("shop", "tea", "Tea shop"),
    ("shop", "confectionery", "Confectioner"),
    ("shop", "chocolate", "Chocolatier"),
    ("leisure", "park", "Park"),
    ("leisure", "garden", "Garden"),
    ("leisure", "stadium", "Stadium"),
    ("natural", "beach", "Beach"),
    ("natural", "cave_entrance", "Cave"),
    ("natural", "spring", "Spring"),
    ("natural", "peak", "Hill"),
    ("natural", "cliff", "Cliff"),
    ("man_made", "lighthouse", "Lighthouse"),
    ("man_made", "pier", "Pier"),
    ("man_made", "tower", "Tower"),
    ("man_made", "obelisk", "Obelisk"),
    ("historic", "monument", "Monument"),
    ("historic", "memorial", "Memorial"),
    ("historic", "fort", "Fort"),
    # OSM files Mumbai's forts under `historic=castle`; every one of them is
    # called a fort by its own name and by everyone who lives here.
    ("historic", "castle", "Fort"),
    ("historic", "ruins", "Ruins"),
    ("historic", "archaeological_site", "Archaeological site"),
    ("historic", "tomb", "Tomb"),
    ("historic", "industrial", "Industrial heritage site"),
    ("historic", "wayside_shrine", "Wayside shrine"),
    ("historic", "wayside_cross", "Wayside cross"),
    ("historic", "building", "Historic building"),
    ("tourism", "attraction", "Attraction"),
]

RELIGIONS = {
    "hindu": "Hindu", "muslim": "Muslim", "christian": "Christian",
    "jewish": "Jewish", "buddhist": "Buddhist", "jain": "Jain",
    "sikh": "Sikh", "zoroastrian": "Zoroastrian", "parsi": "Parsi",
}


def noun_for(tags) -> str:
    for key, value, noun in NOUNS:
        if tags.get(key) == value:
            if noun == "Place of worship":
                religion = RELIGIONS.get((tags.get("religion") or "").lower())
                return f"{religion} place of worship" if religion else noun
            return noun
    if tags.get("historic"):
        return tags["historic"].replace("_", " ").capitalize()
    return "Place"


# `cuisine` is also used for the kind of venue, so it arrives carrying values
# like "cafe" and "wine_bar". Those belong to the noun, not to what is served —
# "serves cafe and wine bar" is not a sentence.
NOT_A_CUISINE = {
    "cafe", "coffee shop", "wine bar", "bar", "pub", "restaurant", "fine dining",
    "fast food", "food court", "bistro", "diner", "buffet", "takeaway",
    "international", "regional", "local", "beverages", "drinks",
}
CUISINE_RENAMES = {"coffee shop": "coffee", "ice cream": "ice cream", "bbq": "barbecue"}


def pretty_cuisine(value: str) -> str:
    parts = [p.strip().replace("_", " ").lower() for p in value.split(";") if p.strip()]
    parts = [CUISINE_RENAMES.get(p, p) for p in parts]
    seen: list[str] = []
    for part in parts:
        if part not in NOT_A_CUISINE and part not in seen:
            seen.append(part)
    parts = seen[:3]
    if not parts:
        return ""
    if len(parts) == 1:
        return parts[0]
    return ", ".join(parts[:-1]) + " and " + parts[-1]


def address_for(tags, area: tuple[str, str] | None) -> str:
    """
    A postal address, from `addr:*` where OSM has it and from the postal
    boundary where it does not.

    Only ~1 in 8 POIs carries a street address, so the boundary fallback is
    what most places get: the locality and pin code the description already
    names, formatted as an address line. That is deliberately not nothing —
    with chains in the catalog, "Starbucks" appears thirty-six times and the
    line under the name is the only thing that says which one you are looking
    at.

    Components are dropped when absent rather than filled in, and never
    repeated: a place tagged `addr:suburb=Colaba` in the Colaba postal area
    says Colaba once.
    """
    parts: list[str] = []
    seen: set[str] = set()

    def add(value: str | None) -> None:
        text = re.sub(r"\s+", " ", (value or "")).strip(" ,")
        if text and text.lower() not in seen:
            seen.add(text.lower())
            parts.append(text)

    street = " ".join(
        p for p in (tags.get("addr:housenumber"), tags.get("addr:street")) if p
    )
    # `addr:place` is what OSM uses where the address hangs off a named area
    # rather than a street, which in Mumbai is most of the older city.
    add(street or tags.get("addr:place"))
    add(tags.get("addr:suburb") or tags.get("addr:neighbourhood"))

    locality, pincode = area if area else ("", "")
    add(locality)

    city = tags.get("addr:city") or "Mumbai"
    postcode = tags.get("addr:postcode") or pincode
    add(f"{city} {postcode}".strip() if postcode else city)

    return ", ".join(parts)


def describe(poi, area: tuple[str, str] | None) -> str:
    """
    A description assembled only from tags. Dry by design — every clause is
    something the data actually says, and nothing fills the gaps where it
    says nothing.
    """
    tags = poi["tags"]
    sentence = noun_for(tags)

    if area:
        locality, pincode = area
        sentence += f" in {locality}"
        if pincode:
            sentence += f" ({pincode})"
    sentence += "."

    extras: list[str] = []

    cuisine = pretty_cuisine(tags.get("cuisine", ""))
    if cuisine:
        extras.append(f"Serves {cuisine}")

    if tags.get("leisure") in PARK_LEISURE or tags.get("boundary") == "national_park":
        hectares = poi["area_m2"] / 10_000.0
        if hectares >= 0.5:
            extras.append(f"{hectares:.1f} hectares")

    year = (tags.get("start_date") or "")[:4]
    if year.isdigit():
        extras.append(f"dates from {year}")

    if tags.get("heritage") or tags.get("heritage:operator"):
        extras.append("heritage listed")

    if tags.get("wikipedia") or tags.get("wikidata"):
        extras.append("has an encyclopaedia entry")

    if extras:
        sentence += " " + "; ".join(extras).capitalize() + "."

    return sentence


# ---------------------------------------------------------------------------
# Ids
# ---------------------------------------------------------------------------

def slugify(name: str) -> str:
    text = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode()
    text = re.sub(r"[^a-zA-Z0-9]+", "-", text).strip("-").lower()
    return re.sub(r"-{2,}", "-", text)[:48] or "place"


# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------

def load_curated() -> list[dict]:
    """The human-chosen names, with the hint coordinate used only to disambiguate."""
    if not CURATED_PATH.exists():
        return []
    entries = []
    for line in CURATED_PATH.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        name, category, lat, lon = line.split("\t")
        entries.append({
            "name": name,
            "category": category,
            "hint": (float(lat), float(lon)),
        })
    return entries


def match_key(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.lower())


def resolve_curated_pois(
    entries: list[dict], pois: list[dict]
) -> tuple[dict[str, dict], list[dict]]:
    """
    Pairs each curated name with the OSM record it refers to.

    Matching is by name, loosely — OSM calls Hanging Gardens "Pherozeshah Mehta
    Gardens (Hanging Gardens)" and Mani Bhavan Gandhi Museum just "Mani Bhavan"
    — and then by which of the same-named records lies nearest the hint. The
    winner keeps OSM's name and, crucially, OSM's coordinate. The curated entry
    only ever decides that the place belongs in the catalog at all.

    Returns a map from OSM object id to the curated entry it satisfies, and the
    curated entries the extract has no answer for.
    """
    by_key: dict[str, list[dict]] = defaultdict(list)
    for poi in pois:
        for key in ("name", "name:en"):
            value = poi["tags"].get(key)
            if value:
                by_key[match_key(value)].append(poi)

    # Every plausible pairing, scored, then assigned exclusively. One POI can
    # satisfy only one curated name: "Kala Ghoda" and "Kala Ghoda Cafe" are two
    # entries and the loose match offers the cafe node to both, so without this
    # the district silently takes the cafe's record and the cafe disappears.
    # Exact name matches are assigned before loose ones, and nearer before
    # further, so each name ends up on the record that fits it best.
    by_token: dict[str, list[dict]] = defaultdict(list)
    for poi in pois:
        for key in ("name", "name:en"):
            value = poi["tags"].get(key)
            if value:
                for token in name_tokens(value):
                    by_token[token].append(poi)

    pairings: list[tuple[int, float, int, str]] = []
    for index, entry in enumerate(entries):
        key = match_key(entry["name"])
        tokens = name_tokens(entry["name"])
        hint_lat, hint_lon = entry["hint"]
        seen: set[str] = set()
        pools = (
            (0, by_key.get(key, ())),          # the same name
            (1, _loose(by_key, key)),          # one name inside the other
            (2, _by_words(by_token, tokens)),  # enough words in common
        )
        for exactness, pool in pools:
            for poi in pool:
                if poi["osm"] in seen:
                    continue
                seen.add(poi["osm"])
                found = poi["tags"].get("name") or poi["tags"].get("name:en") or ""
                if exactness and _wrong_thing(entry["name"], found):
                    continue
                distance = metres_between(hint_lat, hint_lon, poi["lat"], poi["lon"])
                if distance <= CURATED_MATCH_METRES:
                    pairings.append((exactness, distance, index, poi["osm"]))

    pairings.sort()
    resolved: dict[str, dict] = {}
    claimed: set[int] = set()
    for _, _, index, osm in pairings:
        if index in claimed or osm in resolved:
            continue
        claimed.add(index)
        resolved[osm] = entries[index]

    unresolved = [e for i, e in enumerate(entries) if i not in claimed]
    return resolved, unresolved


# Words that carry no identity, so two names sharing only these share nothing.
NAME_STOPWORDS = {
    "the", "of", "and", "co", "mumbai", "bombay", "shri", "sri", "shree", "dr",
    "st", "saint", "cafe", "restaurant", "hotel", "bar", "kitchen", "house",
    "garden", "gardens", "park", "temple", "church", "museum", "gallery", "beach",
    "road", "marg", "new", "old", "centre", "center", "society", "club", "hall",
}


def name_tokens(name: str) -> set[str]:
    """The words in a name that could identify it."""
    words = re.sub(r"[^a-z0-9 ]", " ", name.lower()).split()
    return {w for w in words if len(w) >= 4 and w not in NAME_STOPWORDS}


def _by_words(by_token: dict[str, list[dict]], tokens: set[str]) -> list[dict]:
    """
    Records sharing enough identifying words with the curated name.

    This is what pairs "Asiatic Society Library" with OSM's "Asiatic Society
    Town Hall", and "Mani Bhavan Gandhi Museum" with "Mani Bhavan" — the same
    building under a different description, which substring matching cannot see
    because neither string contains the other.
    """
    if not tokens:
        return []
    shared: dict[str, set[str]] = defaultdict(set)
    records: dict[str, dict] = {}
    for token in tokens:
        for poi in by_token.get(token, ()):
            shared[poi["osm"]].add(token)
            records[poi["osm"]] = poi
    needed = 1 if len(tokens) == 1 else 2
    return [
        records[osm] for osm, words in shared.items()
        # Enough words in common, and at least one of them has to actually name
        # the place rather than describe every second cafe in the city.
        if len(words) >= needed and (words - GENERIC_TOKENS)
    ]


# Words that turn a place into the infrastructure named after it. "Kanheri
# Caves" and "Kanheri Caves Road" share every word that matters and are 800 m
# and one category apart; the same goes for Colaba Causeway and Colaba Bus
# Station. A candidate that introduces one of these is a different thing.
NEARBY_NOT_THE_PLACE = {
    "road", "rd", "marg", "street", "lane", "station", "bus", "depot", "junction",
    "bridge", "flyover", "subway", "crossing", "signal", "gate", "entrance",
    "parking", "toilet", "police", "hospital", "school", "college", "office",
    "racecourse",
}

# Words too common in this dataset to identify anything on their own. Two names
# sharing only these share nothing: "Koinonia Coffee Roasters" and "Bombay
# Coffee House" have "coffee" in common and are different cafes.
GENERIC_TOKENS = {
    "coffee", "roasters", "bakery", "bakers", "sweets", "juice", "snacks",
    "grand", "royal", "national", "central", "public", "municipal", "city",
    "modern", "international", "palace", "plaza", "tower", "towers", "point",
    "view", "corner", "world", "star", "gold", "golden", "green", "blue",
}


def _wrong_thing(curated: str, candidate: str) -> bool:
    """True when the candidate's name adds a word that makes it something else."""
    words = lambda s: set(re.sub(r"[^a-z0-9 ]", " ", s.lower()).split())
    return bool((words(candidate) - words(curated)) & NEARBY_NOT_THE_PLACE)


def _loose(by_key: dict[str, list[dict]], key: str) -> list[dict]:
    """
    Records whose name contains the curated name, or is contained by it, and is
    most of it.

    Containment alone is far too generous. A shop mapped as "G" is contained in
    "gajalee"; "Girgaon" is contained in "Girgaon Chowpatty" and is the
    neighbourhood rather than the beach. Requiring the shorter side to be most
    of the longer drops both. Genuine renamings that fail here — "Mani Bhavan"
    for "Mani Bhavan Gandhi Museum" — are caught by the shared-word tier below.
    """
    if len(key) <= LOOSE_MATCH_CHARS:
        return []
    out = []
    for k, group in by_key.items():
        if len(k) <= LOOSE_MATCH_CHARS or not (key in k or k in key):
            continue
        if min(len(k), len(key)) >= LOOSE_CONTAINMENT_RATIO * max(len(k), len(key)):
            out.extend(group)
    return out


def metres_between(a_lat, a_lon, b_lat, b_lon) -> float:
    m_lat = 111_320.0
    m_lon = 111_320.0 * math.cos(math.radians((a_lat + b_lat) / 2))
    dy = (a_lat - b_lat) * m_lat
    dx = (a_lon - b_lon) * m_lon
    return math.hypot(dx, dy)


def main() -> None:
    report = "--report" in sys.argv

    pois = json.loads(POIS_PATH.read_text())
    areas = Areas()

    # Curated names are resolved against the raw extract, before any filtering.
    # Almost nothing is filtered any more, but the boundary rule still is, and a
    # place a person put on the list has already cleared a better test than it.
    # Resolving first is what lets Elephanta Caves back in: it is outside the
    # postal boundary and that rule would otherwise have dropped it before
    # anything could ask whether it was wanted.
    curated_entries = load_curated()
    curated_by_osm, unresolved = resolve_curated_pois(curated_entries, pois)

    chains = chain_keys([p for p in pois if p.get("candidate", True)])

    candidates = []
    dropped_unnamed = dropped_name = dropped_outside = dropped_chain = 0

    for poi in pois:
        tags = poi["tags"]
        curated = curated_by_osm.get(poi["osm"])

        # The dump also carries every other named object in the box, so a
        # curated name has something to resolve against. Those are lookups
        # only — the generator picks from POI candidates.
        if not curated and not poi.get("candidate", True):
            continue
        # `name:en` alone is enough — Royal Opera House, Swati Snacks and
        # Highway Gomantak are all mapped with an English name and no `name`.
        name = english_name(tags)
        if not name:
            if not (tags.get("name") or tags.get("name:en")):
                dropped_unnamed += 1
            else:
                dropped_name += 1
            continue
        category = categorize(tags) or (curated["category"] if curated else None)
        # A curated name is exempt: someone chose it, which beats any rule here.
        # Nothing on the list is a franchise, but Candies and Prithvi Cafe both
        # have several outlets and both are places people go to on purpose.
        if not curated and is_chain(name, tags, category, chains):
            dropped_chain += 1
            continue
        # The postal areas are what "Mumbai" means here, with a small tolerance
        # for the slivers they leave unclaimed between rounds.
        #
        # This is a real decision and not a technicality: the app's bounding box
        # reaches into Thane and across the harbour, and being notable is not
        # the same as being in Mumbai. Ghodbandar Fort and Elephanta Caves both
        # have encyclopaedia entries and both are outside the city — Elephanta
        # is the further of the two, in Raigad — so no distance rule separates
        # them and nothing else in the data does either. The boundary decides,
        # and it costs the catalog Elephanta. Widen BOUNDARY_TOLERANCE_M or
        # drop the check to have it back.
        area = areas.locate_near(poi["lat"], poi["lon"], BOUNDARY_TOLERANCE_M)
        if area is None and not curated:
            dropped_outside += 1
            continue
        candidates.append({
            "name": name,
            "category": category,
            "lat": poi["lat"],
            "lon": poi["lon"],
            "osm": poi["osm"],
            "area": area,
            # The bonus only settles ordering and which copy of a
            # double-mapped place survives dedupe; inclusion is decided by the
            # curated flag, not by the score.
            "score": score(poi, category, area is not None) + (CURATED_BONUS if curated else 0),
            "description": describe(poi, area),
            "address": address_for(tags, area),
            "has_street": bool(tags.get("addr:street") or tags.get("addr:place")),
            "curated": curated is not None,
        })

    # Best first, so the dedupe below keeps the richer copy of a place that was
    # mapped twice — the way with the tags rather than the bare node.
    candidates.sort(key=lambda c: -c["score"])
    kept: list[dict] = []
    by_name: dict[str, list[dict]] = defaultdict(list)
    duplicates = 0
    for candidate in candidates:
        # Trailing numbers are stripped before comparing, so "Danda Pier 1",
        # "Danda Pier 2" and "Danda Pier 3" are one entry rather than three.
        key = re.sub(r"[^a-z0-9]", "", re.sub(r"\s+\d+$", "", candidate["name"].lower()))
        # Same name, and close enough to be the same building: a node sitting
        # inside its own polygon, mapped once as each. Compared only against
        # entries sharing the name, so this stays a handful of distance checks
        # per candidate rather than 4,700.
        if any(
            metres_between(candidate["lat"], candidate["lon"], other["lat"], other["lon"])
            < DEDUPE_METRES
            for other in by_name[key]
        ):
            duplicates += 1
            continue
        by_name[key].append(candidate)
        kept.append(candidate)

    # Everything that survived hygiene ships. `score` still orders each
    # category, so the places the data has most to say about are the ones a
    # category list opens with.
    chosen = sorted(
        kept,
        key=lambda c: (CATEGORY_ORDER.index(c["category"]), -c["score"]),
    )
    if report:
        for category in CATEGORY_ORDER:
            pool = [c for c in chosen if c["category"] == category]
            print(f"\n=== {category}  ({len(pool)})")
            for c in pool[:40]:
                flag = "" if c["area"] else "   [outside postal boundary]"
                print(f"  {c['score']:6.1f}  {c['name'][:44]:46} {c['osm']:>12}{flag}")
            if len(pool) > 40:
                print(f"  ... and {len(pool) - 40:,} more")

    # Ids have to be unique and stable; a city has more than one "Shiv Mandir",
    # and now they are all in here.
    seen_ids: dict[str, int] = {}
    for place in chosen:
        base = slugify(place["name"])
        count = seen_ids.get(base, 0)
        seen_ids[base] = count + 1
        place["id"] = base if count == 0 else f"{base}-{count + 1}"

    write_catalog(chosen)

    print(f"\ncandidates: {len(pois):,} scanned")
    for label, n in (
        ("unnamed", dropped_unnamed),
        ("no usable English name", dropped_name),
        ("franchise outlets", dropped_chain),
        ("outside Mumbai", dropped_outside),
        ("same place mapped twice", duplicates),
    ):
        print(f"  dropped {label:24} {n:6,}")
    print(f"selected {len(chosen):,} places:")
    for category in CATEGORY_ORDER:
        n = sum(1 for c in chosen if c["category"] == category)
        print(f"  {category:12} {n:6,}")
    with_address = sum(1 for c in chosen if c["address"])
    with_street = sum(1 for c in chosen if c.get("has_street"))
    print(f"  {'with an address':14} {with_address:6,}")
    print(f"  {'of those, a street address from OSM':14} {with_street:6,}")
    outside = sum(1 for c in chosen if not c["area"])
    curated_in = sum(1 for c in chosen if c["curated"])
    print(f"  {'from the curated list':14} {curated_in:6,}")
    print(f"outside the postal boundary (kept because curated): {outside}")
    if unresolved:
        print(f"\ncurated names the extract has no record of ({len(unresolved)}):")
        for entry in unresolved:
            print(f"  {entry['name']}")
    print(f"\nwrote {OUT_PATH} ({OUT_PATH.stat().st_size / 1e6:.2f} MB)")


CATALOG_MAGIC = "CMPL"
CATALOG_VERSION = 1
CATALOG_COLUMNS = ("id", "category", "name", "lat", "lon", "address", "description")


def clean_field(text: str) -> str:
    """
    One line, no tabs, for a format that has no escape character.

    Collapsing whitespace rather than escaping it is what lets the reader be a
    `split('\\t')` and nothing else. Names and descriptions are already built
    this way; addresses go through it too so a stray newline in `addr:street`
    cannot corrupt every row after it.
    """
    return re.sub(r"\s+", " ", text or "").strip()


def write_catalog(places: list[dict]) -> None:
    """
    Writes the catalog as a shipped asset rather than as generated Kotlin.

    It used to be `MumbaiSeed.kt`, a `listOf(place(...), ...)` per category, and
    at 177 places that was the simplest thing that worked. At 4,000 it does not
    work at all: every one of those property initialisers compiles into the
    object's `<clinit>`, and a single JVM method caps out at 64 KB of bytecode.
    The catalog crosses that at around 2,300 places, and the failure is a
    `MethodTooLargeException` at build time.

    So the catalog goes where the map already went — an asset with a codec, read
    once on first launch. Same shape as `mumbai.map`: a magic number, a version,
    and a body that a plain JVM test can read straight off disk.
    """
    rows: list[str] = ["\t".join(CATALOG_COLUMNS)]
    for place in places:
        row = [
            place["id"],
            place["category"],
            clean_field(place["name"]),
            f"{place['lat']:.6f}",
            f"{place['lon']:.6f}",
            clean_field(place["address"]),
            clean_field(place["description"]),
        ]
        if any("\t" in field for field in row):
            raise ValueError(f"tab in a field of {place['id']}")
        rows.append("\t".join(row))

    body = "\n".join(rows) + "\n"
    # A stamp over the body, so an app that has already seeded can tell this
    # catalog from the one it seeded without reading all of it. Any edit to any
    # field changes it; regenerating an unchanged catalog does not.
    stamp = hashlib.sha256(body.encode("utf-8")).hexdigest()[:16]
    header = f"{CATALOG_MAGIC}\t{CATALOG_VERSION}\t{len(places)}\t{stamp}\n"

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(header + body, encoding="utf-8")
    print(f"catalog stamp {stamp}")


if __name__ == "__main__":
    main()
