#!/usr/bin/env python3
"""
Mumbai's postal-area boundary, as a point -> (locality, pincode) lookup.

`boundary.geojson` is 89 polygons of Greater Mumbai, each carrying the area
name and pin code the postal service uses. Two jobs here:

  * deciding whether a place is in Mumbai at all — a real boundary rather than
    a rectangle, so the harbour and the district edges are handled properly;
  * naming where a place is, from data rather than from anyone's memory.

Lookups go through a uniform grid over the polygons' bounding boxes, so a
point test only ray-casts against the handful of areas whose box covers it,
not all 89.
"""

from __future__ import annotations

import json
import math
from pathlib import Path

BOUNDARY_PATH = Path("boundary.geojson")

# Postal decoration in the area names: "MARINE LINES POST OFFICE" is the office,
# the locality is "Marine Lines". Longest first so "HEAD POST OFFICE" is tried
# before "POST OFFICE". Typos ("OFFOCE") are in the source data, not here.
_SUFFIXES = [
    "HEAD POST OFFOCE", "HEAD POST OFFICE", "POST OFFICE", "DELIVERY S.O.",
    "DELY PO", "P & T COLONY", "P.O.", "S.O.", "P.O", "S.O", "R.S.PO",
    "HO", "PO", "MDG", "COMPLEX PO",
]

# Left upper-case when the name is title-cased.
ACRONYMS = {"BARC", "IIT", "SEEPZ", "MIDC", "GPO", "FCI", "NITIE", "BMC",
            "VJB", "AM", "DN", "MIG", "TF", "T"}


def clean_locality(name: str) -> str:
    """'MARINE LINES POST OFFICE' -> 'Marine Lines'."""
    out = " ".join(name.strip().upper().split())
    for suffix in _SUFFIXES:
        if out.endswith(" " + suffix) or out == suffix:
            out = out[: -len(suffix)].strip()
            break
    out = out.strip(" .,")
    if not out:
        out = " ".join(name.strip().split())
    # Title case, except for the handful of genuine acronyms. An allowlist
    # rather than "short and upper-case", which turned Nariman Point into
    # "Nariman POINT".
    return " ".join(w if w in ACRONYMS else w.title() for w in out.split())


class Areas:
    """Point-in-polygon over the postal areas, indexed by a uniform grid."""

    CELL = 0.01  # degrees, ~1.1 km

    def __init__(self, path: Path = BOUNDARY_PATH):
        data = json.loads(Path(path).read_text())
        self.areas: list[tuple[str, str, list[list[tuple[float, float]]]]] = []
        self.grid: dict[tuple[int, int], list[int]] = {}

        for feature in data["features"]:
            props = feature.get("properties", {})
            locality = clean_locality(str(props.get("name", "")))
            pincode = str(props.get("pin_code", "")).strip()
            rings = _rings(feature["geometry"])
            if not rings:
                continue
            index = len(self.areas)
            self.areas.append((locality, pincode, rings))

            lons = [x for ring in rings for x, _ in ring]
            lats = [y for ring in rings for _, y in ring]
            for i in range(int(min(lons) / self.CELL), int(max(lons) / self.CELL) + 1):
                for j in range(int(min(lats) / self.CELL), int(max(lats) / self.CELL) + 1):
                    self.grid.setdefault((i, j), []).append(index)

        lons = [x for _, _, rings in self.areas for ring in rings for x, _ in ring]
        lats = [y for _, _, rings in self.areas for ring in rings for _, y in ring]
        self.bbox = (min(lats), min(lons), max(lats), max(lons))

    def locate(self, lat: float, lon: float) -> tuple[str, str] | None:
        """The (locality, pincode) containing this point, or None if outside Mumbai."""
        for index in self.grid.get((int(lon / self.CELL), int(lat / self.CELL)), ()):
            locality, pincode, rings = self.areas[index]
            if _in_rings(lon, lat, rings):
                return locality, pincode
        return None

    def locate_near(
        self, lat: float, lon: float, tolerance_m: float = 250.0
    ) -> tuple[str, str] | None:
        """
        As [locate], but also accepts a point just outside the polygons.

        The 89 areas are postal delivery rounds, not a city outline, so they
        leave slivers unclaimed between neighbours — Khada Parsi sits 30 m
        outside every one of them while standing in the middle of Byculla.
        Probing a ring of points around the query closes those gaps without
        stretching the boundary far enough to swallow Thane.
        """
        hit = self.locate(lat, lon)
        if hit is not None:
            return hit
        d_lat = tolerance_m / 111_320.0
        d_lon = tolerance_m / (111_320.0 * max(math.cos(math.radians(lat)), 1e-6))
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1),
                       (1, 1), (1, -1), (-1, 1), (-1, -1)):
            hit = self.locate(lat + dy * d_lat, lon + dx * d_lon)
            if hit is not None:
                return hit
        return None


def _rings(geometry) -> list[list[tuple[float, float]]]:
    kind = geometry["type"]
    coords = geometry["coordinates"]
    if kind == "Polygon":
        return [[(float(x), float(y)) for x, y, *_ in ring] for ring in coords]
    if kind == "MultiPolygon":
        return [
            [(float(x), float(y)) for x, y, *_ in ring]
            for polygon in coords
            for ring in polygon
        ]
    return []


def _in_rings(x: float, y: float, rings: list[list[tuple[float, float]]]) -> bool:
    """Even-odd ray casting. Outer rings and holes both flip, which is what we want."""
    inside = False
    for ring in rings:
        n = len(ring)
        j = n - 1
        for i in range(n):
            xi, yi = ring[i]
            xj, yj = ring[j]
            if (yi > y) != (yj > y):
                if x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                    inside = not inside
            j = i
    return inside


if __name__ == "__main__":
    areas = Areas()
    print(f"{len(areas.areas)} postal areas")
    print("bbox lat %.4f..%.4f lon %.4f..%.4f" % (areas.bbox[0], areas.bbox[2],
                                                  areas.bbox[1], areas.bbox[3]))
    for lat, lon, what in [
        (18.9220, 72.8347, "Gateway of India"),
        (19.0968, 72.8265, "Juhu Beach"),
        (18.9633, 72.9315, "Elephanta Caves"),
        (19.2147, 72.9106, "Sanjay Gandhi NP"),
    ]:
        print(f"  {what:22} -> {areas.locate(lat, lon)}")
