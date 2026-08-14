#!/usr/bin/env python3
"""
Pass 1 of the map pipeline: OSM PBF -> a cropped, classified intermediate.

Reads the western-India extract, crops to the Mumbai bounding box, keeps only
the ways whose tags matter to the renderer, and classifies each one into a
`Kind`. The result is written as a pickle of raw int32 lat/lng (degrees * 1e7)
so pass 2 (`build_map_asset.py`) can iterate on simplification and encoding in
seconds instead of re-reading 225 MB every time.

Two classes of feature are treated differently:

  * City-wide kinds (coastline, water, green, rail, roads down to residential)
    are kept for the whole bbox. These are what the map draws — very dimly —
    everywhere, so the city has a silhouette even when nothing is explored.

  * Detail kinds (buildings, service roads, footpaths) are kept only within
    DETAIL_RADIUS_M of a seeded place. They are *only* ever drawn inside a
    reveal, so keeping them city-wide would multiply the asset size for
    geometry no one can see. Restricting them also stops the dim base layer
    from leaking the location of unexplored places.

Usage:  .venv-osm/bin/python3 tools/extract_osm.py western-zone-260813.osm.pbf
"""

from __future__ import annotations

import math
import pickle
import re
import sys
import time
from pathlib import Path

import osmium

# --------------------------------------------------------------------------
# Area of interest
# --------------------------------------------------------------------------

# Greater Mumbai plus the harbour islands. The seeded places span
# 18.906..19.234 N, 72.794..72.932 E; this leaves room for the coastline and
# Sanjay Gandhi National Park to close properly at the edges.
MIN_LAT, MAX_LAT = 18.860, 19.300
MIN_LNG, MAX_LNG = 72.750, 73.010

# How far around a place the map is allowed to light up. Detail geometry is
# only kept inside this radius. Must stay >= the renderer's reveal radius.
DETAIL_RADIUS_M = 520.0

SEED_KT = Path("app/src/main/java/com/citymemory/data/local/seed/MumbaiSeed.kt")

# --------------------------------------------------------------------------
# Classification
# --------------------------------------------------------------------------

# Kind ids are shared with the Kotlin decoder (ui/map/MapKind.kt). Keep in sync.
K_COASTLINE = 0
K_WATER = 1
K_GREEN = 2
K_RAIL = 3
K_MOTORWAY = 4
K_PRIMARY = 5
K_SECONDARY = 6
K_TERTIARY = 7
K_RESIDENTIAL = 8
K_SERVICE = 9
K_BUILDING = 10

KIND_NAMES = {
    K_COASTLINE: "coastline",
    K_WATER: "water",
    K_GREEN: "green",
    K_RAIL: "rail",
    K_MOTORWAY: "motorway",
    K_PRIMARY: "primary",
    K_SECONDARY: "secondary",
    K_TERTIARY: "tertiary",
    K_RESIDENTIAL: "residential",
    K_SERVICE: "service",
    K_BUILDING: "building",
}

# Kinds that are only kept near a place.
DETAIL_KINDS = {K_SERVICE, K_BUILDING}

# Kinds drawn as filled polygons rather than stroked lines.
AREA_KINDS = {K_WATER, K_GREEN, K_BUILDING}

HIGHWAY_KIND = {
    "motorway": K_MOTORWAY,
    "motorway_link": K_MOTORWAY,
    "trunk": K_MOTORWAY,
    "trunk_link": K_MOTORWAY,
    "primary": K_PRIMARY,
    "primary_link": K_PRIMARY,
    "secondary": K_SECONDARY,
    "secondary_link": K_SECONDARY,
    "tertiary": K_TERTIARY,
    "tertiary_link": K_TERTIARY,
    "unclassified": K_TERTIARY,
    "residential": K_RESIDENTIAL,
    "living_street": K_RESIDENTIAL,
    "service": K_SERVICE,
    "pedestrian": K_SERVICE,
    "footway": K_SERVICE,
    "path": K_SERVICE,
    "steps": K_SERVICE,
    "track": K_SERVICE,
}

RAILWAY_VALUES = {"rail", "light_rail", "subway", "narrow_gauge", "monorail"}

GREEN_LANDUSE = {
    "forest", "grass", "meadow", "recreation_ground", "village_green",
    "greenfield", "cemetery", "allotments",
}
GREEN_LEISURE = {"park", "garden", "golf_course", "nature_reserve", "pitch"}
GREEN_NATURAL = {"wood", "scrub", "heath", "grassland"}

WATER_NATURAL = {"water", "bay", "strait", "wetland"}
WATER_LANDUSE = {"reservoir", "basin", "salt_pond", "aquaculture"}
WATER_WATERWAY = {"riverbank", "dock", "canal", "river", "stream"}


def classify(tags) -> int | None:
    """Map an OSM way's tags onto a render kind, or None to drop it."""
    if tags.get("natural") == "coastline":
        return K_COASTLINE

    if tags.get("natural") in WATER_NATURAL:
        return K_WATER
    if tags.get("landuse") in WATER_LANDUSE:
        return K_WATER
    if tags.get("waterway") in WATER_WATERWAY:
        return K_WATER
    if tags.get("water") is not None:
        return K_WATER

    if tags.get("leisure") in GREEN_LEISURE:
        return K_GREEN
    if tags.get("landuse") in GREEN_LANDUSE:
        return K_GREEN
    if tags.get("natural") in GREEN_NATURAL:
        return K_GREEN

    if tags.get("railway") in RAILWAY_VALUES:
        return K_RAIL

    highway = tags.get("highway")
    if highway is not None:
        kind = HIGHWAY_KIND.get(highway)
        if kind is not None:
            return kind
        return None

    # `building:part` deliberately excluded — it double-draws over the footprint.
    if tags.get("building") is not None:
        return K_BUILDING

    return None


# --------------------------------------------------------------------------
# Place proximity grid
# --------------------------------------------------------------------------

def load_places() -> list[tuple[float, float]]:
    src = SEED_KT.read_text()
    pts = re.findall(
        r"PlaceCategory\.[A-Z_]+,\s*([0-9]+\.[0-9]+),\s*([0-9]+\.[0-9]+)", src
    )
    if not pts:
        raise SystemExit(f"no places parsed out of {SEED_KT}")
    return [(float(a), float(b)) for a, b in pts]


class ProximityGrid:
    """
    Cheap 'is this point within R of any place' test.

    Buckets places into a lat/lng grid whose cell is one radius across, so a
    lookup only has to check the nine cells around the query point.
    """

    def __init__(self, places: list[tuple[float, float]], radius_m: float):
        self.radius_m = radius_m
        mid_lat = sum(p[0] for p in places) / len(places)
        self.m_per_deg_lat = 111_320.0
        self.m_per_deg_lng = 111_320.0 * math.cos(math.radians(mid_lat))
        self.cell_lat = radius_m / self.m_per_deg_lat
        self.cell_lng = radius_m / self.m_per_deg_lng
        self.cells: dict[tuple[int, int], list[tuple[float, float]]] = {}
        for lat, lng in places:
            key = (int(lat / self.cell_lat), int(lng / self.cell_lng))
            self.cells.setdefault(key, []).append((lat, lng))

    def near(self, lat: float, lng: float) -> bool:
        ci = int(lat / self.cell_lat)
        cj = int(lng / self.cell_lng)
        r2 = self.radius_m * self.radius_m
        for di in (-1, 0, 1):
            for dj in (-1, 0, 1):
                for plat, plng in self.cells.get((ci + di, cj + dj), ()):
                    dy = (lat - plat) * self.m_per_deg_lat
                    dx = (lng - plng) * self.m_per_deg_lng
                    if dx * dx + dy * dy <= r2:
                        return True
        return False


# --------------------------------------------------------------------------
# Extraction
# --------------------------------------------------------------------------

def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    pbf = sys.argv[1]
    out = Path(sys.argv[2] if len(sys.argv) > 2 else "build/osm-mumbai.pickle")
    out.parent.mkdir(parents=True, exist_ok=True)

    places = load_places()
    grid = ProximityGrid(places, DETAIL_RADIUS_M)
    print(f"{len(places)} places, detail radius {DETAIL_RADIUS_M:.0f} m")
    print(f"bbox lat {MIN_LAT}..{MAX_LAT}  lng {MIN_LNG}..{MAX_LNG}")

    shapes: list[tuple[int, list[tuple[int, int]]]] = []
    counts: dict[int, int] = {}
    seen = 0
    started = time.time()

    fp = osmium.FileProcessor(pbf, osmium.osm.NODE | osmium.osm.WAY).with_locations(
        "flex_mem"
    )

    for obj in fp:
        if obj.type_str() != "w":
            continue
        seen += 1
        if seen % 500_000 == 0:
            print(
                f"  {seen:>10,} ways scanned, {len(shapes):>7,} kept, "
                f"{time.time() - started:5.0f}s"
            )

        tags = obj.tags
        kind = classify(tags)
        if kind is None:
            continue

        # Crop first on the way's own nodes; anything wholly outside is dropped
        # before we pay for building a coordinate list.
        pts: list[tuple[int, int]] = []
        inside = False
        for n in obj.nodes:
            if not n.location.valid():
                continue
            lat = n.location.lat
            lng = n.location.lon
            if MIN_LAT <= lat <= MAX_LAT and MIN_LNG <= lng <= MAX_LNG:
                inside = True
            pts.append((int(round(lat * 1e7)), int(round(lng * 1e7))))

        if not inside or len(pts) < 2:
            continue

        if kind in DETAIL_KINDS:
            # Any vertex near a place is enough — a road that runs into the
            # reveal should be kept whole so it does not stop at the edge.
            if not any(grid.near(a / 1e7, b / 1e7) for a, b in pts):
                continue

        shapes.append((kind, pts))
        counts[kind] = counts.get(kind, 0) + 1

    elapsed = time.time() - started
    print(f"\nscanned {seen:,} ways in {elapsed:.0f}s")
    print(f"kept {len(shapes):,} shapes:")
    total_pts = 0
    for kind in sorted(counts):
        n_pts = 0
        print(f"  {KIND_NAMES[kind]:<12} {counts[kind]:>8,}")
    total_pts = sum(len(p) for _, p in shapes)
    print(f"  {'points':<12} {total_pts:>8,}")

    with out.open("wb") as fh:
        pickle.dump(
            {
                "bbox": (MIN_LAT, MIN_LNG, MAX_LAT, MAX_LNG),
                "detail_radius_m": DETAIL_RADIUS_M,
                "shapes": shapes,
            },
            fh,
            protocol=pickle.HIGHEST_PROTOCOL,
        )
    print(f"\nwrote {out} ({out.stat().st_size / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
