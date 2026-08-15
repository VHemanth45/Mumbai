#!/usr/bin/env python3
"""Shared helpers for the Overture places pipeline.

This module exists because four scripts had each grown their own copy of the
same GeoJSON line reader and address builder. The CMPL catalog writer here is a
deliberate mirror of `build_seed.py:write_catalog` — the two must stay
byte-compatible, because `PlaceCatalogCodec.kt` reads whatever either produces.
"""
from __future__ import annotations

import hashlib
import json
import math
import re
import unicodedata
from pathlib import Path

# --------------------------------------------------------------------------
# CMPL catalog format - must match tools/build_seed.py and PlaceCatalogCodec.kt
# --------------------------------------------------------------------------
CATALOG_MAGIC = "CMPL"
CATALOG_VERSION = 1
CATALOG_COLUMNS = ("id", "category", "name", "lat", "lon", "address", "description")

# The six PlaceCategory constants, in the order the app lists them.
CATEGORY_ORDER = ["TOURIST", "RESTAURANT", "CAFE", "CULTURE", "PARK", "HIDDEN_GEM"]

# Two places this close with the same name are the same place mapped twice.
# Same value build_seed.py uses, for the same reason.
DEDUPE_METRES = 150.0


def clean_field(text) -> str:
    """One line, no tabs — the format has no escape character."""
    return re.sub(r"\s+", " ", text or "").strip()


def write_cmpl_catalog(path, places) -> str:
    """Write a CMPL catalog and return its stamp.

    `places` is a sequence of dicts with id/category/name/lat/lon/address/
    description. Byte-for-byte the same layout as build_seed.py writes:
    a header line, a column-name line, then one line per place.
    """
    rows = ["\t".join(CATALOG_COLUMNS)]
    for place in places:
        row = [
            place["id"],
            place["category"],
            clean_field(place["name"]),
            f"{place['lat']:.6f}",
            f"{place['lon']:.6f}",
            clean_field(place.get("address")),
            clean_field(place.get("description")),
        ]
        if any("\t" in f for f in row):
            raise ValueError(f"tab in a field of {place['id']}")
        rows.append("\t".join(row))

    body = "\n".join(rows) + "\n"
    stamp = hashlib.sha256(body.encode("utf-8")).hexdigest()[:16]
    header = f"{CATALOG_MAGIC}\t{CATALOG_VERSION}\t{len(places)}\t{stamp}\n"

    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(header + body, encoding="utf-8")
    return stamp


def read_cmpl_catalog(path):
    """Read a CMPL catalog into (stamp, [dict, ...]). Raises on a bad file."""
    lines = Path(path).read_text(encoding="utf-8").splitlines()
    if len(lines) < 2:
        raise ValueError(f"{path}: catalog is empty")
    header = lines[0].split("\t")
    if header[0] != CATALOG_MAGIC:
        raise ValueError(f"{path}: not a place catalog: {header[0]!r}")
    if int(header[1]) != CATALOG_VERSION:
        raise ValueError(f"{path}: unsupported version {header[1]}")
    declared, stamp = int(header[2]), header[3]

    places = []
    for line in lines[2:]:
        if not line.strip():
            continue
        f = line.split("\t")
        if len(f) != len(CATALOG_COLUMNS):
            raise ValueError(f"{path}: row has {len(f)} fields, expected 7")
        places.append({
            "id": f[0], "category": f[1], "name": f[2],
            "lat": float(f[3]), "lon": float(f[4]),
            "address": f[5], "description": f[6],
        })
    if len(places) != declared:
        raise ValueError(f"{path}: header says {declared}, found {len(places)}")
    return stamp, places


# --------------------------------------------------------------------------
# Overture GeoJSON
# --------------------------------------------------------------------------
def iter_features(path):
    """Yield Feature dicts from an Overture GeoJSON extract.

    The overturemaps writer emits one Feature per line; these files run to
    hundreds of MB, so stream rather than json.load(). Falls back to a whole
    file parse for ordinary pretty-printed GeoJSON.
    """
    with open(path, "r", encoding="utf-8") as fh:
        first = fh.readline()
        if not first.rstrip().endswith("["):
            fh.seek(0)
            for feat in (json.load(fh) or {}).get("features", []):
                yield feat
            return
        for line in fh:
            line = line.strip().rstrip(",")
            if not line or line == "]}":
                continue
            if line.endswith("]}"):
                line = line[:-2]
            try:
                yield json.loads(line)
            except json.JSONDecodeError:
                continue


def place_name(props):
    return (props.get("names") or {}).get("primary")


def raw_category(props):
    return (props.get("categories") or {}).get("primary")


def taxonomy_hierarchy(props):
    return (props.get("taxonomy") or {}).get("hierarchy") or []


def build_address(props):
    """freeform, with locality appended when it adds something."""
    freeform = locality = None
    for a in props.get("addresses") or []:
        if isinstance(a, dict):
            freeform = freeform or a.get("freeform")
            locality = locality or a.get("locality")
    if freeform and locality and locality.lower() not in freeform.lower():
        return f"{freeform}, {locality}"
    return freeform or locality


def address_locality(props):
    """Just the locality, for building a description."""
    for a in props.get("addresses") or []:
        if isinstance(a, dict) and a.get("locality"):
            return a["locality"]
    return None


def load_json(path):
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)


# --------------------------------------------------------------------------
# Matching
# --------------------------------------------------------------------------
def normalize_name(s):
    """Lowercase, strip accents, drop apostrophes, collapse punctuation."""
    s = unicodedata.normalize("NFKD", s or "")
    s = "".join(c for c in s if not unicodedata.combining(c))
    s = s.lower()
    s = re.sub(r"['’ʼ`]", "", s)
    s = re.sub(r"[^a-z0-9\s]", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def haversine_m(lat1, lon1, lat2, lon2):
    """Great-circle distance in metres."""
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


class SpatialGrid:
    """Cheap grid index for 'is there a same-named place within N metres'.

    A full pairwise scan of the OSM catalog against 271k Overture rows is
    ~10^9 comparisons; bucketing by a ~150 m cell makes it linear.
    """

    def __init__(self, cell_metres=DEDUPE_METRES):
        self.cell = cell_metres
        self.buckets = {}

    def _key(self, lat, lon):
        # ~111 km per degree of latitude; longitude shrinks by cos(lat) but at
        # Mumbai's 19N that is a 5% error on the cell size, which does not
        # matter because neighbours are always checked too.
        return (int(lat * 111_000 / self.cell), int(lon * 105_000 / self.cell))

    def add(self, lat, lon, payload):
        self.buckets.setdefault(self._key(lat, lon), []).append((lat, lon, payload))

    def near(self, lat, lon, radius_m):
        kx, ky = self._key(lat, lon)
        span = int(radius_m / self.cell) + 1
        for dx in range(-span, span + 1):
            for dy in range(-span, span + 1):
                for plat, plon, payload in self.buckets.get((kx + dx, ky + dy), ()):
                    if haversine_m(lat, lon, plat, plon) <= radius_m:
                        yield payload
