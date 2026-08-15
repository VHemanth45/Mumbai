#!/usr/bin/env python3
"""Generate data/category_map.json: Overture primary category -> display category.

Overture ships a second, cleaner taxonomy alongside `categories.primary`:
`taxonomy.hierarchy`, a 1-3 level path like
["food_and_drink", "casual_eatery", "cafe"]. Every one of the 1,306 primary
categories resolves to exactly one hierarchy root (verified, zero conflicts),
so the hierarchy drives the mapping instead of keyword-guessing on 1,306 names.

The generated JSON is the editable artifact - build_places_db.py reads it and
never re-derives anything. Re-run this only to pick up a new Overture release,
and expect to re-apply local edits.

Usage: python3 tools/gen_category_map.py [places.geojson] [-o out.json]
"""
import argparse
import json
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from overture_common import iter_features, raw_category, taxonomy_hierarchy  # noqa: E402, defaultdict

DISPLAY_CATEGORIES = [
    "food", "cafe", "shopping", "culture", "nature", "nightlife",
    "hotel", "transport", "religious", "services", "health", "other",
]

# Level-2 of the hierarchy wins when present - this is where the splits live
# that the root is too coarse to express (drink venues, worship, parks).
LEVEL2 = {
    # food_and_drink
    "restaurant": "food",
    "non_alcoholic_beverage_venue": "cafe",
    "alcoholic_beverage_venue": "nightlife",
    # cultural_and_historic
    "place_of_worship": "religious",
    "religious_organization": "religious",
    "historic_site": "culture",
    "memorial_site": "culture",
    "cultural_center": "culture",
    # arts_and_entertainment
    "nightlife_venue": "nightlife",
    "gaming_venue": "nightlife",
    "spiritual_advising": "services",
    # sports_and_recreation - parks are nature, gyms are services
    "park": "nature",
    "recreational_trail_or_path": "nature",
    # geographic_entities - bridges/piers/marinas read as outdoor landmarks
    "land_feature": "nature",
    "water_feature": "nature",
    "built_feature": "nature",
    # travel_and_transportation - agencies and garages are not transport
    "travel_service": "services",
    "vehicle_service": "services",
    "ground_transport_facility_or_service": "transport",
    "air_transport_facility_or_service": "transport",
    "transport_interchange": "transport",
    "parking": "transport",
    "fueling_station": "transport",
}

# Hierarchy root -> display, used when level-2 has no specific rule.
LEVEL1 = {
    "food_and_drink": "food",
    "shopping": "shopping",
    "cultural_and_historic": "culture",
    "arts_and_entertainment": "culture",
    "sports_and_recreation": "services",
    "geographic_entities": "nature",
    "travel_and_transportation": "transport",
    "lodging": "hotel",
    "health_care": "health",
    "education": "services",
    "services_and_business": "services",
    "lifestyle_services": "services",
    "community_and_government": "services",
}

# `casual_eatery` mixes sit-down food with coffee/bakery/dessert counters, so
# it is split on the primary category name.
CAFE_WORDS = (
    "cafe", "coffee", "tea_", "_tea", "tea_room", "bakery", "bakeries",
    "patisserie", "dessert", "ice_cream", "creamery", "cupcake", "chocolat",
    "candy", "juice", "smoothie", "bubble_tea", "boba", "donut", "doughnut",
    "milk_bar", "frozen_yogurt", "confection",
)

# Last resort for the ~8k rows Overture ships with no taxonomy at all.
KEYWORD_FALLBACK = [
    (("doctor", "clinic", "hospital", "dentist", "pharmac", "medical",
      "physician", "surgeon", "diagnostic", "health"), "health"),
    (("temple", "church", "mosque", "masjid", "gurudwara", "synagogue",
      "shrine", "religio", "worship"), "religious"),
    (("hotel", "hostel", "resort", "guest_house", "lodging"), "hotel"),
    (("restaurant", "eatery", "dhaba", "food"), "food"),
    (("cafe", "coffee", "bakery", "tea"), "cafe"),
    (("bar", "pub", "nightclub", "lounge"), "nightlife"),
    (("store", "shop", "market", "retail", "boutique", "mall"), "shopping"),
    (("park", "garden", "beach", "lake", "hill", "river"), "nature"),
    (("station", "airport", "transport", "parking", "bus", "railway",
      "metro", "taxi"), "transport"),
    (("museum", "gallery", "theatre", "theater", "cinema", "monument",
      "heritage", "historic"), "culture"),
]


# ---------------------------------------------------------------------------
# Second map: Overture category -> one of the app's six PlaceCategory constants
#
# These rules deliberately mirror the OSM tag sets in tools/build_seed.py
# (CULTURE_TOURISM, TOURIST_HISTORIC, PARK_LEISURE, CAFE_SHOP, ...) so an
# Overture-sourced place lands in the same bucket an OSM-sourced one would.
# `null` means "do not ship this to the app at all".
# ---------------------------------------------------------------------------
PLACE_CATEGORIES = ["TOURIST", "RESTAURANT", "CAFE", "CULTURE", "PARK", "HIDDEN_GEM"]

# Exact raw-category wins first.
SEED_RAW = {
    # CULTURE_TOURISM {museum, gallery} + CULTURE_AMENITY {theatre, arts_centre,
    # library, cinema, planetarium}
    "museum": "CULTURE", "art_museum": "CULTURE", "history_museum": "CULTURE",
    "science_museum": "CULTURE", "art_gallery": "CULTURE", "gallery": "CULTURE",
    "library": "CULTURE", "public_library": "CULTURE", "cinema": "CULTURE",
    "movie_theater": "CULTURE", "theater": "CULTURE", "theatre": "CULTURE",
    "performing_arts": "CULTURE", "performing_arts_venue": "CULTURE",
    "opera_house": "CULTURE", "concert_hall": "CULTURE", "planetarium": "CULTURE",
    "arts_and_entertainment": "CULTURE", "cultural_center": "CULTURE",
    # TOURIST_TOURISM {attraction, viewpoint, zoo, aquarium, theme_park}
    "tourist_attraction": "TOURIST", "zoo": "TOURIST", "aquarium": "TOURIST",
    "theme_park": "TOURIST", "amusement_park": "TOURIST", "water_park": "TOURIST",
    "scenic_lookout": "TOURIST", "lookout": "TOURIST", "viewpoint": "TOURIST",
    # TOURIST_HISTORIC {monument, memorial, castle, fort} + man_made
    "monument": "TOURIST", "memorial": "TOURIST", "castle": "TOURIST",
    "fort": "TOURIST", "lighthouse": "TOURIST", "obelisk": "TOURIST",
    "historic_site": "TOURIST",
    # NOT a landmark bucket despite the name: sampled at random it is housing
    # societies and apartment towers ("Navnit Co-op Housing Society",
    # "Shubh Angan Apartment"), 10,229 of them, and confidence does not separate
    # them because confidence scores whether a place exists, not whether it is
    # worth visiting. Shipping it would bury TOURIST under residential blocks.
    "landmark_and_historical_building": None,
    "heritage_building": "TOURIST", "heritage_museum": "CULTURE",
    # natural == beach, amenity == marketplace, leisure == stadium
    "beach": "TOURIST", "marketplace": "TOURIST", "public_market": "TOURIST",
    "farmers_market": "TOURIST", "flea_market": "TOURIST", "market": "TOURIST",
    "shopping_mall": "TOURIST", "stadium": "TOURIST", "stadium_arena": "TOURIST",
    # PARK_LEISURE {park, garden, nature_reserve}
    "park": "PARK", "garden": "PARK", "botanical_garden": "PARK",
    "national_park": "PARK", "state_park": "PARK", "nature_reserve": "PARK",
    "nature_preserve": "PARK", "picnic_site": "PARK", "playground": "PARK",
}

# Then the hierarchy's second level.
SEED_LEVEL2 = {
    "museum": "CULTURE", "performing_arts_venue": "CULTURE",
    "movie_theater": "CULTURE", "arts_and_crafts_space": "CULTURE",
    "cultural_center": "CULTURE",
    "historic_site": "TOURIST", "memorial_site": "TOURIST",
    "animal_attraction": "TOURIST", "amusement_attraction": "TOURIST",
    "stadium_arena": "TOURIST", "built_feature": "TOURIST",
    "land_feature": "TOURIST", "water_feature": "TOURIST",
    "park": "PARK", "recreational_trail_or_path": "PARK",
}

# Finally the 12-bucket display category. None = drop.
SEED_FROM_DISPLAY = {
    "cafe": "CAFE",
    "food": "RESTAURANT",
    "nightlife": "RESTAURANT",   # RESTAURANT_AMENITY includes bar, pub, biergarten
    "culture": "CULTURE",
    "nature": "PARK",
    # build_seed.py sends a place of worship to TOURIST only when the data says
    # it draws visitors, and to HIDDEN_GEM otherwise. Overture has no article
    # flag, so the default here is the conservative one and build_seed_overture.py
    # promotes the high-confidence ones per row.
    "religious": "HIDDEN_GEM",
    "shopping": None,
    "hotel": None,
    "transport": None,
    "services": None,
    "health": None,
    "other": None,
}


def write_if_changed(path, payload):
    """Leave the file's mtime alone when the content is identical.

    The pipeline decides what to rebuild from modification times, so rewriting
    an unchanged map would mark the 71.7 MB database stale for no reason.
    """
    text = json.dumps(payload, indent=1, sort_keys=False, ensure_ascii=False) + "\n"
    p = Path(path)
    if p.exists() and p.read_text(encoding="utf-8") == text:
        print(f"  {path} unchanged")
        return False
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")
    return True


def classify_seed(primary, hierarchy, display):
    """Overture category -> PlaceCategory constant, or None to drop it."""
    if primary in SEED_RAW:
        return SEED_RAW[primary]
    h2 = hierarchy[1] if len(hierarchy) > 1 else None
    if h2 and h2 in SEED_LEVEL2:
        return SEED_LEVEL2[h2]
    return SEED_FROM_DISPLAY.get(display)


def classify(primary, hierarchy):
    """Return (display_category, rule_that_decided_it)."""
    h1 = hierarchy[0] if len(hierarchy) > 0 else None
    h2 = hierarchy[1] if len(hierarchy) > 1 else None

    if h1 == "food_and_drink" and h2 == "casual_eatery":
        hit = any(w in primary for w in CAFE_WORDS)
        return ("cafe" if hit else "food", "casual_eatery split")
    if h2 and h2 in LEVEL2:
        return LEVEL2[h2], f"level2:{h2}"
    if h1 and h1 in LEVEL1:
        return LEVEL1[h1], f"level1:{h1}"
    for words, display in KEYWORD_FALLBACK:
        if any(w in primary for w in words):
            return display, "keyword"
    return "other", "default"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("geojson", nargs="?", default="data/mumbai_places.geojson")
    ap.add_argument("-o", "--out", default="data/category_map.json")
    ap.add_argument("--seed-out", default="data/seed_category_map.json")
    args = ap.parse_args()

    counts = Counter()
    hier = {}
    for feat in iter_features(args.geojson):
        props = feat.get("properties") or {}
        primary = raw_category(props)
        if not primary:
            continue
        counts[primary] += 1
        if primary not in hier:
            hier[primary] = taxonomy_hierarchy(props)

    mapping, rules, per_display = {}, {}, Counter()
    rows_per_display = Counter()
    seed_map, per_seed, rows_per_seed = {}, Counter(), Counter()
    for primary in sorted(counts):
        h = hier.get(primary) or []
        display, rule = classify(primary, h)
        mapping[primary] = display
        rules[rule] = rules.get(rule, 0) + 1
        per_display[display] += 1
        rows_per_display[display] += counts[primary]

        seed = classify_seed(primary, h, display)
        seed_map[primary] = seed
        per_seed[seed] += 1
        rows_per_seed[seed] += counts[primary]

    out = {
        "_comment": (
            "Overture categories.primary -> display category. Edit freely; "
            "tools/build_places_db.py reads this file and does not re-derive. "
            "Unknown or missing categories fall back to `default`."
        ),
        "_generated_from": args.geojson,
        "display_categories": DISPLAY_CATEGORIES,
        "default": "other",
        "categories": mapping,
    }
    write_if_changed(args.out, out)

    seed_out = {
        "_comment": (
            "Overture categories.primary -> one of the app's six PlaceCategory "
            "constants, or null to not ship the place at all. Mirrors the OSM tag "
            "sets in tools/build_seed.py. Read by tools/build_seed_overture.py. "
            "Note: 'religious' categories default to HIDDEN_GEM here and are "
            "promoted to TOURIST per row above a confidence threshold, mirroring "
            "build_seed.py's has_article() rule."
        ),
        "_generated_from": args.geojson,
        "place_categories": PLACE_CATEGORIES,
        "default": None,
        "categories": seed_map,
    }
    write_if_changed(args.seed_out, seed_out)

    total = sum(counts.values())
    print(f"\n{args.out}: {len(mapping):,} categories -> {len(per_display)} display buckets\n")
    print(f"{'display':<12} {'categories':>11} {'rows':>10} {'share':>7}")
    print("-" * 44)
    for d in DISPLAY_CATEGORIES:
        print(f"{d:<12} {per_display[d]:>11,} {rows_per_display[d]:>10,} "
              f"{100.0*rows_per_display[d]/total:>6.1f}%")
    print("-" * 44)
    print(f"{'TOTAL':<12} {len(mapping):>11,} {total:>10,}")
    print("\ndecided by:", dict(sorted(rules.items(), key=lambda kv: -kv[1])))

    print(f"\n{args.seed_out}: Overture -> PlaceCategory (null = not shipped)\n")
    print(f"{'PlaceCategory':<14} {'categories':>11} {'rows':>10} {'share':>7}")
    print("-" * 46)
    for c in PLACE_CATEGORIES + [None]:
        label = c if c else "(dropped)"
        print(f"{label:<14} {per_seed[c]:>11,} {rows_per_seed[c]:>10,} "
              f"{100.0*rows_per_seed[c]/total:>6.1f}%")
    print("-" * 46)
    shipped = sum(rows_per_seed[c] for c in PLACE_CATEGORIES)
    print(f"{'SHIPPABLE':<14} {'':>11} {shipped:>10,} {100.0*shipped/total:>6.1f}%")


if __name__ == "__main__":
    main()
