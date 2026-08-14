#!/usr/bin/env python3
"""
Pass 2 of the map pipeline: intermediate pickle -> app/src/main/assets/mumbai.map

Simplifies each shape (Douglas-Peucker, tolerance chosen per kind so buildings
keep their corners while long roads shed redundant vertices), clips stray
geometry back to the bounding box, and writes a compact binary the app decodes
directly into `CityGeometry`.

Encoding — all integers little-endian where fixed width, LEB128 where varint:

    magic      "CMAP"                       4 bytes
    version    u8 = 1
    bounds     4 x i32   min lat, min lng, max lat, max lng   (degrees * 1e6)
    shapes     varint count
      kind       u8
      points     varint count
      lat0,lng0  zigzag varint, absolute (degrees * 1e6)
      then (count-1) pairs of zigzag varint deltas from the previous point

Delta coding is what makes this small: consecutive vertices of a way are metres
apart, so each delta lands in one or two bytes instead of the eight a pair of
doubles would cost.

Usage:  .venv-osm/bin/python3 tools/build_map_asset.py
"""

from __future__ import annotations

import math
import pickle
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from extract_osm import (  # noqa: E402
    AREA_KINDS,
    K_BUILDING,
    K_COASTLINE,
    K_SERVICE,
    KIND_NAMES,
)

PRECISION = 1e6  # ~0.11 m — finer than the ~0.9 m/px of the deepest zoom.

# Simplification tolerance in metres. The map zooms to roughly 1 m/px, so
# anything under a metre is invisible; buildings get a tighter budget because
# a 10 m facade cannot afford the same error a 2 km road can.
TOLERANCE_M = {
    K_BUILDING: 0.7,
}
DEFAULT_TOLERANCE_M = 1.5

# Padding beyond the extract bbox that geometry is allowed to reach before it
# gets clipped. Ways were kept whole if *any* vertex was inside, so a trunk road
# can otherwise trail halfway across Maharashtra.
CLIP_PAD_DEG = 0.02

IN_PATH = Path("build/osm-mumbai.pickle")
OUT_PATH = Path("app/src/main/assets/mumbai.map")


# --------------------------------------------------------------------------
# Geometry
# --------------------------------------------------------------------------

def simplify(points: list[tuple[int, int]], tol2: float, sx: float) -> list[tuple[int, int]]:
    """
    Iterative Douglas-Peucker. `sx` scales longitude units onto latitude units
    so the perpendicular distance test is in real ground distance, not degrees.
    """
    n = len(points)
    if n < 3:
        return points

    keep = [False] * n
    keep[0] = keep[n - 1] = True
    stack = [(0, n - 1)]

    while stack:
        first, last = stack.pop()
        if last <= first + 1:
            continue

        ay, ax = points[first]
        by, bx = points[last]
        ax *= sx
        bx *= sx
        dx = bx - ax
        dy = by - ay
        denom = dx * dx + dy * dy

        far_i = -1
        far_d = tol2
        for i in range(first + 1, last):
            py, px = points[i]
            px *= sx
            if denom == 0:
                d = (px - ax) ** 2 + (py - ay) ** 2
            else:
                t = ((px - ax) * dx + (py - ay) * dy) / denom
                t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
                d = (px - ax - t * dx) ** 2 + (py - ay - t * dy) ** 2
            if d > far_d:
                far_d = d
                far_i = i

        if far_i >= 0:
            keep[far_i] = True
            stack.append((first, far_i))
            stack.append((far_i, last))

    return [p for p, k in zip(points, keep) if k]


def clip_polyline(
    points: list[tuple[int, int]], box: tuple[int, int, int, int]
) -> list[list[tuple[int, int]]]:
    """
    Split an open way into the runs of it that lie inside `box`, keeping one
    vertex of slack on each side so a road visibly leaves the frame instead of
    stopping short of it.
    """
    lo_lat, lo_lng, hi_lat, hi_lng = box
    runs: list[list[tuple[int, int]]] = []
    current: list[tuple[int, int]] = []
    prev: tuple[int, int] | None = None

    for p in points:
        inside = lo_lat <= p[0] <= hi_lat and lo_lng <= p[1] <= hi_lng
        if inside:
            if not current and prev is not None:
                current.append(prev)  # one step of lead-in
            current.append(p)
        else:
            if current:
                current.append(p)  # one step of lead-out
                runs.append(current)
                current = []
        prev = p

    if current:
        runs.append(current)
    return [r for r in runs if len(r) >= 2]


def centroid_inside(points: list[tuple[int, int]], box: tuple[int, int, int, int]) -> bool:
    lo_lat, lo_lng, hi_lat, hi_lng = box
    lat = sum(p[0] for p in points) / len(points)
    lng = sum(p[1] for p in points) / len(points)
    return lo_lat <= lat <= hi_lat and lo_lng <= lng <= hi_lng


# --------------------------------------------------------------------------
# Encoding
# --------------------------------------------------------------------------

def put_varint(out: bytearray, value: int) -> None:
    while True:
        chunk = value & 0x7F
        value >>= 7
        if value:
            out.append(chunk | 0x80)
        else:
            out.append(chunk)
            return


def put_zigzag(out: bytearray, value: int) -> None:
    put_varint(out, (value << 1) ^ (value >> 63))


# --------------------------------------------------------------------------

def main() -> None:
    with IN_PATH.open("rb") as fh:
        data = pickle.load(fh)

    min_lat, min_lng, max_lat, max_lng = data["bbox"]
    shapes: list[tuple[int, list[tuple[int, int]]]] = data["shapes"]
    print(f"read {len(shapes):,} shapes from {IN_PATH}")

    # The extract stored degrees * 1e7; the asset stores degrees * 1e6.
    box = (
        int((min_lat - CLIP_PAD_DEG) * 1e7),
        int((min_lng - CLIP_PAD_DEG) * 1e7),
        int((max_lat + CLIP_PAD_DEG) * 1e7),
        int((max_lng + CLIP_PAD_DEG) * 1e7),
    )
    mid_lat = (min_lat + max_lat) / 2.0
    # Metres per unit of the 1e-7-degree grid, latitude and longitude.
    m_per_unit_lat = 111_320.0 / 1e7
    lng_squash = math.cos(math.radians(mid_lat))

    out_shapes: list[tuple[int, list[tuple[int, int]]]] = []
    stats: dict[int, list[int]] = {}

    for kind, pts in shapes:
        tol_m = TOLERANCE_M.get(kind, DEFAULT_TOLERANCE_M)
        tol_units = tol_m / m_per_unit_lat
        tol2 = tol_units * tol_units

        if kind in AREA_KINDS:
            parts = [pts] if centroid_inside(pts, box) else []
        else:
            parts = clip_polyline(pts, box)

        for part in parts:
            simple = simplify(part, tol2, lng_squash)
            if kind in AREA_KINDS:
                if len(simple) < 3:
                    continue
                # Closing is the renderer's job; a repeated last vertex is waste.
                if simple[0] == simple[-1]:
                    simple = simple[:-1]
                if len(simple) < 3:
                    continue
            elif len(simple) < 2:
                continue

            out_shapes.append((kind, simple))
            slot = stats.setdefault(kind, [0, 0])
            slot[0] += 1
            slot[1] += len(simple)

    print(f"\n{'kind':<12}{'shapes':>9}{'points':>11}")
    total_shapes = total_points = 0
    for kind in sorted(stats):
        n, p = stats[kind]
        total_shapes += n
        total_points += p
        print(f"{KIND_NAMES[kind]:<12}{n:>9,}{p:>11,}")
    print(f"{'TOTAL':<12}{total_shapes:>9,}{total_points:>11,}")

    # Group by kind so the decoder builds one path bucket at a time and the
    # renderer's per-kind arrays stay contiguous.
    out_shapes.sort(key=lambda s: s[0])

    buf = bytearray()
    buf += b"CMAP"
    buf.append(1)
    buf += struct.pack(
        "<4i",
        round(min_lat * PRECISION),
        round(min_lng * PRECISION),
        round(max_lat * PRECISION),
        round(max_lng * PRECISION),
    )
    put_varint(buf, len(out_shapes))

    for kind, pts in out_shapes:
        buf.append(kind)
        put_varint(buf, len(pts))
        prev_lat = prev_lng = 0
        for i, (lat7, lng7) in enumerate(pts):
            lat = round(lat7 / 10)  # 1e-7 deg -> 1e-6 deg
            lng = round(lng7 / 10)
            if i == 0:
                put_zigzag(buf, lat)
                put_zigzag(buf, lng)
            else:
                put_zigzag(buf, lat - prev_lat)
                put_zigzag(buf, lng - prev_lng)
            prev_lat, prev_lng = lat, lng

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_bytes(buf)
    print(
        f"\nwrote {OUT_PATH} — {len(buf) / 1e6:.2f} MB "
        f"({len(buf) / max(total_points, 1):.2f} bytes/point)"
    )


if __name__ == "__main__":
    main()
