#!/usr/bin/env python3
"""Overture places -> the CMPL catalog asset the app already reads.

This is the seam between the new Overture pipeline and the existing app. It
produces exactly the format `tools/build_seed.py` writes and
`PlaceCatalogCodec.kt` reads, so adopting it is a data-only change: no Kotlin,
no Room migration, no new asset type.

By default it MERGES: every place from the existing OSM catalog is kept
verbatim, ids untouched, and Overture places are appended where they are not
already there. Keeping the ids matters — `DatabaseSeeder` upserts and never
deletes, so a catalog that renamed an existing id would not replace that place,
it would duplicate it, and the user's visits and ratings would stay attached to
the orphan.

Output goes to a side path, NOT app/src/main/assets/, so the new catalog can be
diffed against the shipped one before anything is adopted.

  python3 tools/build_seed_overture.py
  python3 tools/build_seed_overture.py --min-confidence 0.5 --max-places 20000
  python3 tools/build_seed_overture.py --overture-only
"""
from __future__ import annotations

import argparse
import sqlite3
import sys
import uuid
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from overture_common import (  # noqa: E402
    CATEGORY_ORDER,
    DEDUPE_METRES,
    SpatialGrid,
    clean_field,
    load_json,
    normalize_name,
    read_cmpl_catalog,
    write_cmpl_catalog,
)

# A place of worship is TOURIST only when the data says it draws visitors.
# build_seed.py asks OSM for a wikidata/wikipedia tag; Overture has no such
# flag, so this is the stand-in. It is deliberately high: Overture confidence
# measures "does this place exist", not "is it worth going to", so anything
# lower promotes neighbourhood shrines the app wants in HIDDEN_GEM.
WORSHIP_TOURIST_CONFIDENCE = 0.95

# The box the app considers Mumbai, asserted by PlaceCatalogAssetTest
# ("every place is inside Mumbai"). It is the OSM extract's box, and it is much
# smaller than the Overture download box — the Overture bbox reaches Kalyan and
# Vasai, which this one does not.
#
# This is not about passing a test. `mumbai.map` only carries geometry inside
# this box, and a place is lit by drawing the streets around it, so a catalogued
# place outside it is one the user could never light up.
APP_BBOX = (72.75, 18.86, 73.01, 19.30)   # lon_min, lat_min, lon_max, lat_max


def humanize(raw_category):
    """'indian_restaurant' -> 'Indian restaurant'."""
    if not raw_category:
        return "Place"
    words = raw_category.replace("_", " ").strip()
    return words[:1].upper() + words[1:]


OVERTURE_ID_PREFIX = "ov-"


def overture_id(blob):
    """Stable short id from a GERS uuid. GERS ids are stable across releases,
    so re-running produces the same id for the same place and the upsert
    updates rather than duplicates."""
    return OVERTURE_ID_PREFIX + uuid.UUID(bytes=blob).hex[:12]


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--db", default="data/mumbai_places.db")
    ap.add_argument("--seed-map", default="data/seed_category_map.json")
    ap.add_argument("--base", default="app/src/main/assets/mumbai-places.tsv",
                    help="existing catalog to merge onto (ids preserved)")
    ap.add_argument("--out", default="build/mumbai-places-merged.tsv")
    ap.add_argument("--min-confidence", type=float, default=0.0,
                    help="drop Overture places below this (ranking only by default)")
    ap.add_argument("--max-places", type=int, default=0,
                    help="cap total Overture additions, best-scoring first (0 = no cap)")
    ap.add_argument("--overture-only", action="store_true",
                    help="do not merge the existing catalog")
    ap.add_argument("--dedupe-metres", type=float, default=DEDUPE_METRES)
    ap.add_argument("--bbox", default=",".join(str(v) for v in APP_BBOX),
                    help="lon_min,lat_min,lon_max,lat_max the app has map geometry for")
    ap.add_argument("--keep-addressless", action="store_true",
                    help="keep places with no address (fails PlaceCatalogAssetTest)")
    args = ap.parse_args()

    bbox = tuple(float(v) for v in args.bbox.split(","))
    if len(bbox) != 4:
        sys.exit("--bbox needs 4 comma-separated numbers")
    lon_min, lat_min, lon_max, lat_max = bbox

    seed_map = load_json(args.seed_map)["categories"]

    # ---- base catalog -----------------------------------------------------
    base = []
    base_stamp = None
    if not args.overture_only:
        base_stamp, base = read_cmpl_catalog(args.base)
        print(f"base catalog  {args.base}")
        print(f"              {len(base):,} places, stamp {base_stamp}")

        # The default base is the shipped asset, which may already BE a merged
        # catalog from a previous adoption. Merging onto that would treat the
        # previous Overture rows as untouchable base and add nothing. Strip them
        # so this is idempotent: the pristine source catalog is whatever is left
        # once the rows this tool wrote are removed.
        previously_merged = [p for p in base if p["id"].startswith(OVERTURE_ID_PREFIX)]
        if previously_merged:
            base = [p for p in base if not p["id"].startswith(OVERTURE_ID_PREFIX)]
            print(f"              {len(previously_merged):,} rows from a previous merge "
                  f"stripped, {len(base):,} source places remain")

    # A same-named place within DEDUPE_METRES of one of these is the same place.
    grid = SpatialGrid(args.dedupe_metres)
    for p in base:
        grid.add(p["lat"], p["lon"], normalize_name(p["name"]))

    # ---- overture candidates ---------------------------------------------
    db = sqlite3.connect(args.db)
    total_rows = db.execute("SELECT COUNT(*) FROM places").fetchone()[0]
    rows = db.execute(
        "SELECT id, name, raw_category, lat, lng, address, confidence "
        "FROM places WHERE name IS NOT NULL AND name != '' "
        "ORDER BY confidence DESC")

    stats = Counter()
    seen_ids = set()
    candidates = []
    for pid, name, raw_cat, lat, lng, address, conf in rows:
        category = seed_map.get(raw_cat)
        if category is None:
            stats["dropped: category not shipped"] += 1
            continue
        conf = conf if conf is not None else 0.0
        if conf < args.min_confidence:
            stats["dropped: below min-confidence"] += 1
            continue

        # Outside the box the map covers, this place could never be lit up.
        if not (lon_min <= lng <= lon_max and lat_min <= lat <= lat_max):
            stats["dropped: outside the app's map box"] += 1
            continue

        # The catalog has 15 Hanuman Mandirs; the address line is the only thing
        # that tells them apart, so a place without one is not shippable.
        if not args.keep_addressless and not (address or "").strip():
            stats["dropped: no address"] += 1
            continue

        # A worship place only reaches TOURIST when the data is very sure of it.
        if category == "HIDDEN_GEM" and conf >= WORSHIP_TOURIST_CONFIDENCE:
            category = "TOURIST"
            stats["promoted worship -> TOURIST"] += 1

        clean = clean_field(name)
        if not clean:
            stats["dropped: empty name after cleaning"] += 1
            continue
        norm = normalize_name(clean)
        if not norm:
            stats["dropped: name normalises to nothing"] += 1
            continue

        # Against the base catalog, and against Overture places already taken.
        if any(other == norm for other in grid.near(lat, lng, args.dedupe_metres)):
            stats["dropped: duplicate of an existing place"] += 1
            continue

        oid = overture_id(pid)
        if oid in seen_ids:
            stats["dropped: id collision"] += 1
            continue
        seen_ids.add(oid)

        grid.add(lat, lng, norm)
        candidates.append({
            "id": oid,
            "category": category,
            "name": clean,
            "lat": lat,
            "lon": lng,
            "address": address or "",
            "description": humanize(raw_cat) + ".",
            "score": conf,
        })
        stats[f"kept: {category}"] += 1

    db.close()

    # Rows arrive best-confidence-first, so a cap keeps the best ones.
    if args.max_places and len(candidates) > args.max_places:
        stats["dropped: over --max-places"] = len(candidates) - args.max_places
        candidates = candidates[:args.max_places]

    # ---- order ------------------------------------------------------------
    # The existing catalog keeps its exact order, and Overture is appended after
    # all of it. This is not cosmetic. Explore's search floats names that start
    # with the query, then keeps file order and takes the first 8 — so file
    # order decides what a search returns. Interleaving Overture by category
    # buried "Chhatrapati Shivaji Maharaj Vastu Sangrahalaya" under 133 other
    # Chhatrapati-somethings and broke ExploreViewModelTest. Appending instead
    # means every query that returned a curated place still returns it, in the
    # same position, and Overture only fills slots the curated set left empty.
    # Which is also the right product answer: a place a person chose outranks
    # one that arrived in a bulk import.
    out = list(base)
    for category in CATEGORY_ORDER:
        out.extend(sorted((p for p in candidates if p["category"] == category),
                          key=lambda p: -p["score"]))

    stamp = write_cmpl_catalog(args.out, out)
    size = Path(args.out).stat().st_size

    # ---- report -----------------------------------------------------------
    print(f"\noverture      {args.db}")
    print(f"              {total_rows:,} rows considered")
    for key in sorted(stats, key=lambda k: -stats[k]):
        print(f"                {stats[key]:>8,}  {key}")

    print(f"\nwrote {args.out}")
    print(f"  places      {len(out):,}   ({len(base):,} existing + "
          f"{len(out)-len(base):,} from Overture)")
    print(f"  size        {size/1e6:.2f} MB")
    print(f"  stamp       {stamp}")
    if base_stamp:
        print(f"  base stamp  {base_stamp}  {'(unchanged!)' if stamp == base_stamp else '(differs, as expected)'}")

    print(f"\n{'category':<12} {'existing':>9} {'overture':>9} {'total':>9}")
    print("-" * 42)
    for category in CATEGORY_ORDER:
        b = sum(1 for p in base if p["category"] == category)
        o = sum(1 for p in out if p["category"] == category) - b
        print(f"{category:<12} {b:>9,} {o:>9,} {b+o:>9,}")
    print("-" * 42)
    print(f"{'TOTAL':<12} {len(base):>9,} {len(out)-len(base):>9,} {len(out):>9,}")

    # The codec reads the whole file with readLines() and builds one PlaceEntity
    # per row before a single upsert, so row count is the real constraint here,
    # not APK size.
    if len(out) > 60_000:
        print(f"\n!! {len(out):,} rows. PlaceCatalogCodec.decode() reads every line into "
              f"memory and builds one PlaceEntity each before a single upsert; it was "
              f"written for 3,191. Consider --max-places.")


if __name__ == "__main__":
    main()
