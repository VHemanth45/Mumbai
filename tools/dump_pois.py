#!/usr/bin/env python3
"""
Pass 0 of the seed pipeline: OSM PBF -> every candidate place, with its tags.

Scans nodes and ways inside the map's bounding box, keeps anything carrying a
tag that could make it a place worth visiting, and writes the lot to JSON with
all its tags intact. Curation happens later, in `build_seed.py`, so that
choosing what makes the cut never means re-reading 219 MB.

A way's position is the centroid of its nodes — for a museum or a park mapped
as a polygon that is the middle of the building or the grounds, which is what
you want to drop a pin on.

Usage:  .venv-osm/bin/python3 tools/dump_pois.py western-zone-260813.osm.pbf
"""

from __future__ import annotations

import json
import math
import sys
import time
from pathlib import Path

import osmium

sys.path.insert(0, str(Path(__file__).parent))
from extract_osm import MAX_LAT, MAX_LNG, MIN_LAT, MIN_LNG  # noqa: E402

OUT_PATH = Path("build/mumbai-pois.json")

# A tag key/value is a candidate if it appears here. Deliberately wide — this
# pass is about not having to scan the PBF again, not about taste.
CANDIDATE = {
    "amenity": {
        "cafe", "restaurant", "fast_food", "bar", "pub", "ice_cream", "biergarten",
        "place_of_worship", "theatre", "cinema", "library", "arts_centre",
        "marketplace", "fountain", "clock", "planetarium",
    },
    "tourism": {
        "attraction", "museum", "gallery", "artwork", "viewpoint", "zoo",
        "aquarium", "theme_park", "picnic_site",
    },
    "leisure": {"park", "garden", "nature_reserve", "beach_resort", "stadium"},
    "historic": None,     # any value
    "heritage": None,     # any value
    "natural": {"beach", "cave_entrance", "peak", "cliff", "spring"},
    "man_made": {"lighthouse", "pier", "tower", "obelisk", "watermill"},
    "shop": {"bakery", "coffee", "tea", "confectionery", "chocolate"},
    "boundary": {"national_park", "protected_area"},
    "waterway": {"waterfall"},
}


def is_candidate(tags) -> bool:
    for key, values in CANDIDATE.items():
        value = tags.get(key)
        if value is None:
            continue
        if values is None or value in values:
            return True
    return False


def _worth_keeping(tags) -> bool:
    """
    Candidates, plus anything else that has a name.

    The curated list in `build_seed.py` names places OSM does not always file as
    points of interest — Chor Bazaar and Khotachiwadi are `place=locality`,
    Worli Seaface likewise, and Cafe Zoe carries no category tag at all. Those
    records still hold the one thing worth having, which is where the place
    actually is, so everything named is kept and `candidate` marks which of them
    the generated catalog is allowed to draw from.
    """
    return bool(tags.get("name") or tags.get("name:en"))


def in_box(lat: float, lon: float) -> bool:
    return MIN_LAT <= lat <= MAX_LAT and MIN_LNG <= lon <= MAX_LNG


def _area_m2(lats: list[float], lons: list[float]) -> float:
    """Shoelace area of a ring, in square metres. 0 for anything not closed-ish."""
    if len(lats) < 4:
        return 0.0
    mid = sum(lats) / len(lats)
    m_lat = 111_320.0
    m_lon = 111_320.0 * math.cos(math.radians(mid))
    xs = [lon * m_lon for lon in lons]
    ys = [lat * m_lat for lat in lats]
    total = 0.0
    for i in range(len(xs)):
        j = (i + 1) % len(xs)
        total += xs[i] * ys[j] - xs[j] * ys[i]
    return abs(total) / 2.0


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    pbf = sys.argv[1]
    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)

    found: list[dict] = []
    seen_nodes = seen_ways = 0
    started = time.time()

    fp = osmium.FileProcessor(pbf, osmium.osm.NODE | osmium.osm.WAY).with_locations(
        "flex_mem"
    )

    for obj in fp:
        kind = obj.type_str()
        if kind == "n":
            seen_nodes += 1
            if not obj.tags or not _worth_keeping(obj.tags):
                continue
            loc = obj.location
            if not loc.valid() or not in_box(loc.lat, loc.lon):
                continue
            lat, lon = loc.lat, loc.lon
        elif kind == "w":
            seen_ways += 1
            if seen_ways % 1_000_000 == 0:
                print(f"  {seen_ways:>10,} ways, {len(found):>6,} kept, "
                      f"{time.time() - started:4.0f}s")
            if not obj.tags or not _worth_keeping(obj.tags):
                continue
            lats, lons = [], []
            for n in obj.nodes:
                if n.location.valid():
                    lats.append(n.location.lat)
                    lons.append(n.location.lon)
            if not lats:
                continue
            lat = sum(lats) / len(lats)
            lon = sum(lons) / len(lons)
            if not in_box(lat, lon):
                continue
            # Extent, for ranking things that come in wildly different sizes —
            # 717 mapped parks range from a traffic island to a national park,
            # and how big it is, is the only thing in the data that separates
            # them.
            area_m2 = _area_m2(lats, lons)
        else:
            continue

        found.append({
            "osm": f"{kind}{obj.id}",
            "lat": round(lat, 7),
            "lon": round(lon, 7),
            "area_m2": round(area_m2) if kind == "w" else 0,
            # Only a candidate may be picked by the generator. Everything else
            # in here exists so a curated name has something to resolve against.
            "candidate": is_candidate(obj.tags),
            "tags": {k: v for k, v in obj.tags},
        })

    print(f"\nscanned {seen_nodes:,} nodes and {seen_ways:,} ways in "
          f"{time.time() - started:.0f}s")
    candidates = sum(1 for f in found if f["candidate"])
    print(f"named objects in box: {len(found):,}")
    print(f"  POI candidates:     {candidates:,}")
    print(f"  name-only records:  {len(found) - candidates:,}  (curated lookups only)")

    OUT_PATH.write_text(json.dumps(found))
    print(f"wrote {OUT_PATH} ({OUT_PATH.stat().st_size / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
