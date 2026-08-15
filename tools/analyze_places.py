#!/usr/bin/env python3
"""Report coverage stats for an Overture `place` GeoJSON extract.

The overturemaps writer emits one Feature per line, so this streams the file
instead of loading ~300 MB of JSON into memory.
"""
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from overture_common import iter_features  # noqa: E402

PATH = sys.argv[1] if len(sys.argv) > 1 else "data/mumbai_places.geojson"

# Rough sub-areas of the Mumbai metro region, checked in order; a place lands in
# the first box that contains it. Boxes are (lon_min, lat_min, lon_max, lat_max).
SUBAREAS = [
    ("South Mumbai (Island City)", 72.75, 18.85, 72.92, 19.04),
    ("Western Suburbs",            72.75, 19.04, 72.90, 19.33),
    ("Eastern Suburbs",            72.90, 19.04, 72.98, 19.18),
    ("Navi Mumbai",                72.98, 18.85, 73.20, 19.18),
    ("Thane / Kalyan belt",        72.90, 19.18, 73.20, 19.50),
]


def subarea(lon, lat):
    for name, x0, y0, x1, y1 in SUBAREAS:
        if x0 <= lon < x1 and y0 <= lat < y1:
            return name
    return "Other (rest of bbox)"


def main():
    total = 0
    cats = Counter()
    areas = Counter()
    conf_hi = conf_present = 0
    conf_buckets = Counter()
    named = with_address = with_phone = with_website = with_brand = 0
    no_category = 0

    for feat in iter_features(PATH):
        total += 1
        props = feat.get("properties") or {}
        coords = (feat.get("geometry") or {}).get("coordinates") or [None, None]
        lon, lat = coords[0], coords[1]

        if lon is not None:
            areas[subarea(lon, lat)] += 1

        categories = props.get("categories") or {}
        primary = categories.get("primary") if isinstance(categories, dict) else None
        if primary:
            cats[primary] += 1
        else:
            no_category += 1

        conf = props.get("confidence")
        if conf is not None:
            conf_present += 1
            if conf > 0.8:
                conf_hi += 1
            if conf < 0.5:
                conf_buckets["< 0.50"] += 1
            elif conf < 0.7:
                conf_buckets["0.50 - 0.70"] += 1
            elif conf < 0.8:
                conf_buckets["0.70 - 0.80"] += 1
            elif conf < 0.9:
                conf_buckets["0.80 - 0.90"] += 1
            else:
                conf_buckets[">= 0.90"] += 1

        names = props.get("names") or {}
        if isinstance(names, dict) and names.get("primary"):
            named += 1

        for a in props.get("addresses") or []:
            if isinstance(a, dict) and a.get("freeform"):
                with_address += 1
                break
        if props.get("phones"):
            with_phone += 1
        if props.get("websites"):
            with_website += 1
        # `brand` is usually present but all-null ({"wikidata": null, "names":
        # {"primary": null, ...}}), so a plain truthiness test overcounts.
        brand = props.get("brand") or {}
        if isinstance(brand, dict):
            bnames = brand.get("names") or {}
            if brand.get("wikidata") or (isinstance(bnames, dict) and bnames.get("primary")):
                with_brand += 1

    def pct(n):
        return f"{100.0 * n / total:5.1f}%" if total else "  n/a"

    print("=" * 64)
    print("OVERTURE PLACES - MUMBAI METRO   bbox 72.75,18.85,73.20,19.50")
    print("release 2026-07-22.0")
    print("=" * 64)
    print(f"\nTOTAL PLACES: {total:,}\n")

    print("-" * 64)
    print("TOP 20 PRIMARY CATEGORIES")
    print("-" * 64)
    for name, n in cats.most_common(20):
        print(f"  {name:<40} {n:>7,}  {pct(n)}")
    print(f"\n  distinct primary categories:      {len(cats):>7,}")
    print(f"  places with NO primary category:  {no_category:>7,}  ({pct(no_category)})")

    print("\n" + "-" * 64)
    print("CONFIDENCE")
    print("-" * 64)
    print(f"  confidence > 0.8:        {conf_hi:>7,}  ({pct(conf_hi)})")
    print(f"  has a confidence score:  {conf_present:>7,}  ({pct(conf_present)})")
    print("  distribution:")
    for bucket in ["< 0.50", "0.50 - 0.70", "0.70 - 0.80", "0.80 - 0.90", ">= 0.90"]:
        n = conf_buckets.get(bucket, 0)
        print(f"    {bucket:<14} {n:>7,}  {pct(n)}")

    print("\n" + "-" * 64)
    print("PLACES BY SUB-AREA (rough boxes, first match wins)")
    print("-" * 64)
    for name, *_ in SUBAREAS:
        n = areas.get(name, 0)
        print(f"  {name:<30} {n:>7,}  {pct(n)}")
    n = areas.get("Other (rest of bbox)", 0)
    print(f"  {'Other (rest of bbox)':<30} {n:>7,}  {pct(n)}")

    print("\n" + "-" * 64)
    print("ATTRIBUTE COMPLETENESS (matters for a journal app)")
    print("-" * 64)
    print(f"  has a primary name:      {named:>7,}  ({pct(named)})")
    print(f"  has a freeform address:  {with_address:>7,}  ({pct(with_address)})")
    print(f"  has a phone number:      {with_phone:>7,}  ({pct(with_phone)})")
    print(f"  has a website:           {with_website:>7,}  ({pct(with_website)})")
    print(f"  has a brand:             {with_brand:>7,}  ({pct(with_brand)})")


if __name__ == "__main__":
    main()
