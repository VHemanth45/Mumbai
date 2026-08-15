#!/usr/bin/env python3
"""
Pass 0c of the pipeline: boundary.geojson + the catalog -> assets/mumbai-labels.tsv

The map draws no text at all, which makes it a shape you recognise rather than
one you can read. This writes the names it should carry, in two tiers:

  * **areas** — the 89 postal localities from `boundary.geojson`. These are the
    names Mumbai actually navigates by, and they are the right thing to show
    when the whole city is on screen: Bandra, Colaba, Andheri East.
  * **places** — the head of each category in the shipped catalog, which is
    score-ordered, so these are the places OpenStreetMap has the most to say
    about: an encyclopaedia entry, a heritage listing. They appear once you are
    close enough for a single building to mean something.

Positions are the hard part and are the reason this is a build step rather than
something computed on the device. A postal area's label has to sit *inside* its
own polygon, and a centroid does not: Mumbai's areas wrap around creeks and the
coastline, so the average of the vertices of Mahim or Trombay lands in the
water. What is computed instead is the pole of inaccessibility — the interior
point furthest from any edge — which is both inside the polygon and in the
widest part of it, which is where a label wants to be anyway.

Usage:  .venv-osm/bin/python3 tools/build_labels.py
"""

from __future__ import annotations

import hashlib
import heapq
import math
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from mumbai_boundary import Areas, _in_rings  # noqa: E402

CATALOG_PATH = Path("app/src/main/assets/mumbai-places.tsv")
OUT_PATH = Path("app/src/main/assets/mumbai-labels.tsv")

MAGIC = "CMLB"
VERSION = 1
COLUMNS = ("tier", "name", "lat", "lon", "detail")

# Tier ids are wire values, read back by `PlaceLabelCodec`. Do not renumber.
TIER_AREA = 0
TIER_PLACE = 1

# How many of each category's head becomes a label.
#
# The catalog is written score-ordered within a category, so taking the head
# takes the places the data ranks highest — which for forts, museums and
# beaches is a good proxy for "major", and is exactly what `score()` was built
# to decide. Sized to what fits: at street zoom you see a few of these at once,
# and past a couple of hundred the collision pass is throwing most of them away
# anyway.
PLACE_LABELS = {"TOURIST": 60, "CULTURE": 30, "PARK": 25, "HIDDEN_GEM": 15}

# Stop refining the pole search when a better answer could only be this much
# further from the edge. 25 m is far below what a label position needs.
POLE_PRECISION_M = 25.0

METRES_PER_DEG_LAT = 111_320.0


# ---------------------------------------------------------------------------
# Pole of inaccessibility
# ---------------------------------------------------------------------------

def _segment_distance(px, py, ax, ay, bx, by) -> float:
    """Distance from a point to a segment, all in metres."""
    dx, dy = bx - ax, by - ay
    if dx == 0.0 and dy == 0.0:
        return math.hypot(px - ax, py - ay)
    t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
    t = max(0.0, min(1.0, t))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def _signed_distance(px, py, rings) -> float:
    """Distance to the nearest edge, negative outside the polygon."""
    best = float("inf")
    inside = False
    for ring in rings:
        n = len(ring)
        j = n - 1
        for i in range(n):
            xi, yi = ring[i]
            xj, yj = ring[j]
            if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
                inside = not inside
            best = min(best, _segment_distance(px, py, xi, yi, xj, yj))
            j = i
    return best if inside else -best


def pole_of_inaccessibility(rings, precision: float) -> tuple[float, float, float]:
    """
    The interior point furthest from any edge, and how far that is.

    Mapbox's polylabel, which is a best-first quadtree search: a cell's *best
    possible* answer is its centre's distance plus its own half-diagonal, so a
    cell whose best possible is worse than an answer already found can be
    discarded without ever looking inside it. That is what makes this cheap
    enough to be exact rather than sampled.

    Everything here is in the local metric space the caller set up, so
    "distance" is metres and `precision` is metres.
    """
    xs = [x for ring in rings for x, _ in ring]
    ys = [y for ring in rings for _, y in ring]
    min_x, max_x = min(xs), max(xs)
    min_y, max_y = min(ys), max(ys)
    width, height = max_x - min_x, max_y - min_y
    cell = min(width, height)
    if cell == 0.0:
        return min_x, min_y, 0.0

    half = cell / 2.0
    # A max-heap on "best possible", via negation.
    queue: list[tuple[float, int, float, float, float, float]] = []
    counter = 0

    def push(cx, cy, h):
        nonlocal counter
        d = _signed_distance(cx, cy, rings)
        heapq.heappush(queue, (-(d + h * math.sqrt(2)), counter, cx, cy, h, d))
        counter += 1

    x = min_x
    while x < max_x:
        y = min_y
        while y < max_y:
            push(x + half, y + half, half)
            y += cell
        x += cell

    # The centroid of the bounding box is a serviceable starting answer, and
    # gives the search something to prune against immediately.
    best_x, best_y = min_x + width / 2.0, min_y + height / 2.0
    best_d = _signed_distance(best_x, best_y, rings)

    while queue:
        neg_potential, _, cx, cy, h, d = heapq.heappop(queue)
        if d > best_d:
            best_x, best_y, best_d = cx, cy, d
        if -neg_potential - best_d <= precision:
            continue
        h /= 2.0
        for ox, oy in ((-h, -h), (h, -h), (-h, h), (h, h)):
            push(cx + ox, cy + oy, h)

    return best_x, best_y, best_d


def area_label_point(rings) -> tuple[float, float, float]:
    """
    The pole of a lat/lng ring, back in lat/lng.

    Projected to metres first, because a degree of longitude in Mumbai is 5%
    shorter than a degree of latitude and a "furthest from any edge" computed
    in raw degrees would lean east-west.
    """
    lat0 = sum(y for ring in rings for _, y in ring) / sum(len(r) for r in rings)
    m_per_lon = METRES_PER_DEG_LAT * math.cos(math.radians(lat0))

    projected = [
        [((x * m_per_lon), (y * METRES_PER_DEG_LAT)) for x, y in ring] for ring in rings
    ]
    px, py, radius = pole_of_inaccessibility(projected, POLE_PRECISION_M)
    return py / METRES_PER_DEG_LAT, px / m_per_lon, radius


# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------

def read_catalog() -> list[dict]:
    lines = CATALOG_PATH.read_text(encoding="utf-8").splitlines()
    if not lines or not lines[0].startswith("CMPL\t"):
        raise SystemExit(f"{CATALOG_PATH} is not a catalog — run tools/build_seed.py")
    out = []
    for line in lines[2:]:
        if not line.strip():
            continue
        f = line.split("\t")
        out.append({"category": f[1], "name": f[2], "lat": float(f[3]), "lon": float(f[4])})
    return out


def clean(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


def main() -> None:
    labels: list[tuple[int, str, float, float, str]] = []

    areas = Areas()
    outside = []
    for locality, pincode, rings in areas.areas:
        lat, lon, radius = area_label_point(rings)
        if not _in_rings(lon, lat, rings):
            # Should not happen — the pole is interior by construction — but a
            # label in the sea is worth failing loudly over rather than shipping.
            outside.append(locality)
            continue
        labels.append((TIER_AREA, clean(locality), lat, lon, pincode))

    if outside:
        raise SystemExit(f"pole landed outside its own polygon: {outside}")

    catalog = read_catalog()
    for category, limit in PLACE_LABELS.items():
        taken = [p for p in catalog if p["category"] == category][:limit]
        for place in taken:
            labels.append((TIER_PLACE, clean(place["name"]), place["lat"], place["lon"], ""))

    rows = ["\t".join(COLUMNS)]
    for tier, name, lat, lon, detail in labels:
        row = [str(tier), name, f"{lat:.6f}", f"{lon:.6f}", detail]
        if any("\t" in field for field in row):
            raise ValueError(f"tab in a field of {name}")
        rows.append("\t".join(row))

    body = "\n".join(rows) + "\n"
    stamp = hashlib.sha256(body.encode("utf-8")).hexdigest()[:16]
    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(f"{MAGIC}\t{VERSION}\t{len(labels)}\t{stamp}\n" + body, encoding="utf-8")

    areas_n = sum(1 for label in labels if label[0] == TIER_AREA)
    print(f"wrote {OUT_PATH} ({OUT_PATH.stat().st_size / 1e3:.1f} kB)")
    print(f"  {areas_n} postal areas")
    for category, limit in PLACE_LABELS.items():
        n = sum(1 for p in catalog if p["category"] == category)
        print(f"  {min(limit, n):3} of {n:,} {category.lower()} places")
    print(f"  {len(labels)} labels, stamp {stamp}")


if __name__ == "__main__":
    main()
