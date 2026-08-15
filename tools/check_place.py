#!/usr/bin/env python3
"""Fuzzy-search the Overture places extract by name.

    python3 tools/check_place.py "veronicas"
    python3 tools/check_place.py "jio world drive" "colaba market" -n 5
    python3 tools/check_place.py "juhu" --db data/mumbai_places.db

Queries the prebuilt SQLite DB by default: FTS5 narrows 271k rows to a handful
of candidates in about a millisecond, then the candidates are fuzzy-scored so a
typo still lands. Reading the raw GeoJSON instead is still supported (pass a
`.geojson` path) but is ~100x slower — it has to parse 309 MB before it can
score anything.

FTS alone cannot find a misspelling, so each token is also searched as a
truncated prefix: "internation" finds "International", "veron" finds "Veronica".
"""
from __future__ import annotations

import argparse
import os
import re
import sqlite3
import sys
import time
from difflib import SequenceMatcher
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from overture_common import (  # noqa: E402
    build_address,
    iter_features,
    normalize_name,
    place_name,
    raw_category,
)

DEFAULT_DB = os.environ.get("PLACES_DB", "data/mumbai_places.db")
PREFIX_CHARS = 5   # how much of a token to keep when hunting for a misspelling


def score(query_norm, cand_norm):
    """0-1 similarity, boosted for containment and token-subset hits."""
    if not cand_norm:
        return 0.0
    if query_norm == cand_norm:
        return 1.0
    ratio = SequenceMatcher(None, query_norm, cand_norm).ratio()
    # Containment only counts when the two are comparable in length. Ungated, a
    # 2-3 char name scores a false 0.90 on any query containing it ("na" inside
    # "one internation center", "ark" inside "colaba market").
    if query_norm in cand_norm or cand_norm in query_norm:
        cover = min(len(query_norm), len(cand_norm)) / max(len(query_norm), len(cand_norm))
        if cover >= 0.45:
            ratio = max(ratio, 0.55 + 0.40 * cover)
    q, c = set(query_norm.split()), set(cand_norm.split())
    if q and q <= c:
        ratio = max(ratio, 0.88)
    elif q and c:
        ratio = max(ratio, (len(q & c) / len(q)) * 0.80)
    return ratio


def fts_variants(text):
    """Progressively looser FTS5 queries: exact AND, prefix AND, prefix OR."""
    tokens = [t for t in re.findall(r"\w+", text, flags=re.UNICODE) if t]
    if not tokens:
        return []
    exact_and = " ".join(f'"{t}"' for t in tokens)
    prefix_and = " ".join(f'"{t}"*' for t in tokens)
    stems = [t[:PREFIX_CHARS] for t in tokens if len(t) >= 3]
    prefix_or = " OR ".join(f'"{s}"*' for s in stems) if stems else None
    out = [exact_and, prefix_and]
    if prefix_or:
        out.append(prefix_or)
    return out


def search_db(db, query, limit, threshold, pool):
    """FTS5 for candidates, fuzzy scoring for the ranking."""
    qn = normalize_name(query)
    seen, candidates = set(), []
    for variant in fts_variants(query):
        try:
            rows = db.execute(
                "SELECT p.name, p.raw_category, p.display_category, p.address, "
                "       p.confidence, p.lat, p.lng "
                "FROM places_fts f JOIN places p ON p.rowid = f.rowid "
                "WHERE places_fts MATCH ? ORDER BY bm25(places_fts) LIMIT ?",
                (variant, pool)).fetchall()
        except sqlite3.OperationalError:
            continue          # a variant can be invalid FTS syntax; try the next
        for r in rows:
            if r[0] and r[0] not in seen:
                seen.add(r[0])
                candidates.append(r)
        if len(candidates) >= pool:
            break

    hits = []
    for name, raw_cat, disp, addr, conf, lat, lng in candidates:
        s = score(qn, normalize_name(name))
        if s >= threshold:
            hits.append((s, name, raw_cat or disp or "-", addr, conf, lat, lng))
    hits.sort(key=lambda h: (-h[0], h[1]))
    return hits[:limit]


def search_geojson(path, query, limit, threshold):
    qn = normalize_name(query)
    hits = []
    for feat in iter_features(path):
        props = feat.get("properties") or {}
        name = place_name(props)
        if not name:
            continue
        s = score(qn, normalize_name(name))
        if s >= threshold:
            coords = (feat.get("geometry") or {}).get("coordinates") or [None, None]
            hits.append((s, name, raw_category(props) or "-", build_address(props),
                         props.get("confidence"), coords[1], coords[0]))
    hits.sort(key=lambda h: (-h[0], h[1]))
    return hits[:limit]


def render(query, hits, elapsed_ms):
    print(f"\n=== {query!r}   ({elapsed_ms:.1f} ms) ===")
    if not hits:
        print("  no match")
        return
    for s, name, cat, addr, conf, lat, lng in hits:
        conf_s = f"{conf:.2f}" if isinstance(conf, (int, float)) else "n/a"
        print(f"  [{s:.2f}] {name}")
        print(f"         category:   {cat}")
        print(f"         address:    {addr or '-'}")
        if lat is not None and lng is not None:
            print(f"         coords:     {lat:.5f}, {lng:.5f}")
        print(f"         confidence: {conf_s}")
        print()


def main():
    ap = argparse.ArgumentParser(description="Fuzzy-match place names in the Overture extract.")
    ap.add_argument("name", nargs="+", help="name(s) to look up")
    ap.add_argument("--db", default=DEFAULT_DB,
                    help="prebuilt SQLite DB, or a .geojson to scan instead")
    ap.add_argument("-n", "--limit", type=int, default=10, help="max matches per name")
    ap.add_argument("-t", "--threshold", type=float, default=0.55, help="min score 0-1")
    ap.add_argument("--pool", type=int, default=400,
                    help="FTS candidates to fuzzy-score per query")
    args = ap.parse_args()

    if not os.path.exists(args.db):
        sys.exit(f"error: {args.db} not found.\n"
                 f"Build it with: python3 tools/build_places_db.py")

    use_geojson = args.db.endswith((".geojson", ".json"))
    if use_geojson:
        print(f"scanning {args.db} (slow path — no index)")
        for name in args.name:
            t = time.perf_counter()
            hits = search_geojson(args.db, name, args.limit, args.threshold)
            render(name, hits, (time.perf_counter() - t) * 1000)
        return

    db = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    total = db.execute("SELECT COUNT(*) FROM places").fetchone()[0]
    print(f"searching {total:,} places in {args.db}")
    for name in args.name:
        t = time.perf_counter()
        hits = search_db(db, name, args.limit, args.threshold, args.pool)
        render(name, hits, (time.perf_counter() - t) * 1000)
    db.close()


if __name__ == "__main__":
    main()
