#!/usr/bin/env python3
"""Build a prebuilt SQLite database from the Overture places GeoJSON extract.

  python3 tools/build_places_db.py
  python3 tools/build_places_db.py --fts-mode contentless-delete

Every row is kept. `confidence` is stored for ranking only and is never used
to filter.

FTS modes
---------
external (default)
    fts5(..., content='places', content_rowid='rowid')
    Works on Android API 24+, which is why it is the default.
contentless-delete
    fts5(..., content='', contentless_delete=1)
    Needs SQLite >= 3.43, i.e. Android API 35+ (Android 15 ships 3.44.3;
    Android 14 ships 3.39.2 and cannot even open the table). Marginally
    larger here, not smaller.
"""
import argparse
import os
import re
import sqlite3
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from overture_common import build_address, iter_features, load_json  # noqa: E402

SCHEMA = """
CREATE TABLE places (
    id               BLOB PRIMARY KEY,
    name             TEXT,
    display_category TEXT,
    raw_category     TEXT,
    lat              REAL,
    lng              REAL,
    address          TEXT,
    confidence       REAL,
    source           TEXT DEFAULT 'overture'
);
"""

FTS_CONTENTLESS = """
CREATE VIRTUAL TABLE places_fts USING fts5(
    name, address, content='', contentless_delete=1
);
"""

FTS_EXTERNAL = """
CREATE VIRTUAL TABLE places_fts USING fts5(
    name, address, content='places', content_rowid='rowid'
);
"""

INDEXES = """
CREATE INDEX idx_places_lat ON places(lat);
"""

# Kept in sync by triggers so later edits to `places` do not desync the index.
TRIGGERS = """
CREATE TRIGGER places_ai AFTER INSERT ON places BEGIN
    INSERT INTO places_fts(rowid, name, address)
    VALUES (new.rowid, new.name, new.address);
END;
CREATE TRIGGER places_ad AFTER DELETE ON places BEGIN
    {delete_stmt}
END;
CREATE TRIGGER places_au AFTER UPDATE ON places BEGIN
    {delete_stmt}
    INSERT INTO places_fts(rowid, name, address)
    VALUES (new.rowid, new.name, new.address);
END;
"""

# Contentless-delete deletes by rowid; external-content needs the old values
# echoed back through the special 'delete' command.
DELETE_CONTENTLESS = "DELETE FROM places_fts WHERE rowid = old.rowid;"
DELETE_EXTERNAL = (
    "INSERT INTO places_fts(places_fts, rowid, name, address) "
    "VALUES ('delete', old.rowid, old.name, old.address);"
)

TEST_QUERIES = ["colaba market", "cafe bandra", "chhatrapati", "juhu"]


def fts_query(text):
    """Quote each token so punctuation cannot break FTS5 syntax (implicit AND)."""
    tokens = re.findall(r"\w+", text, flags=re.UNICODE)
    return " ".join(f'"{t}"' for t in tokens)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--geojson", default="data/mumbai_places.geojson")
    ap.add_argument("--map", default="data/category_map.json")
    ap.add_argument("--out", default="data/mumbai_places.db")
    # external is the default: contentless_delete needs SQLite >= 3.43, i.e.
    # Android API 35+, which is too high a floor to ship against.
    ap.add_argument("--fts-mode", choices=["contentless-delete", "external"],
                    default="external")
    ap.add_argument("--size-limit-mb", type=float, default=80.0)
    ap.add_argument("--min-rows", type=int, default=1000,
                    help="fail rather than publish a suspiciously small DB")
    args = ap.parse_args()

    cmap = load_json(args.map)
    categories = cmap["categories"]
    default_display = cmap.get("default", "other")

    # Build beside the target and rename at the end, so a failed or interrupted
    # run leaves the previous good database in place.
    final = args.out
    args.out = final + ".part"
    for path in (args.out, args.out + "-wal", args.out + "-shm"):
        if os.path.exists(path):
            os.remove(path)

    db = sqlite3.connect(args.out)
    db.executescript("PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF;")
    db.executescript(SCHEMA)
    db.executescript(FTS_CONTENTLESS if args.fts_mode == "contentless-delete"
                     else FTS_EXTERNAL)

    print(f"building {args.out}  (fts mode: {args.fts_mode})")
    t0 = time.perf_counter()

    rows, fts_rows = [], []
    odd_ids = []
    n = skipped = 0
    db.execute("BEGIN")
    for feat in iter_features(args.geojson):
        props = feat.get("properties") or {}
        pid = props.get("id") or feat.get("id")
        coords = (feat.get("geometry") or {}).get("coordinates") or [None, None]
        lng, lat = coords[0], coords[1]
        if not pid or lat is None or lng is None:
            skipped += 1
            continue

        name = (props.get("names") or {}).get("primary")
        raw_category = (props.get("categories") or {}).get("primary")
        display = categories.get(raw_category, default_display) if raw_category \
            else default_display
        address = build_address(props)

        # Overture GERS ids are canonical 36-char UUIDs (verified: all 271,071).
        # Stored as 16-byte blobs - saves ~10 MB across the row data and the
        # primary-key index. Round-trip with uuid.UUID(bytes=row_id).
        try:
            pid_blob = bytes.fromhex(pid.replace("-", ""))
            if len(pid_blob) != 16:
                raise ValueError
        except ValueError:
            pid_blob = pid.encode("utf-8")
            odd_ids.append(pid)

        n += 1
        rows.append((n, pid_blob, name, display, raw_category, lat, lng,
                     address, props.get("confidence")))
        fts_rows.append((n, name, address))

        if len(rows) >= 20000:
            db.executemany(
                "INSERT INTO places(rowid,id,name,display_category,raw_category,"
                "lat,lng,address,confidence) VALUES (?,?,?,?,?,?,?,?,?)", rows)
            db.executemany(
                "INSERT INTO places_fts(rowid,name,address) VALUES (?,?,?)", fts_rows)
            rows, fts_rows = [], []
    if rows:
        db.executemany(
            "INSERT INTO places(rowid,id,name,display_category,raw_category,"
            "lat,lng,address,confidence) VALUES (?,?,?,?,?,?,?,?,?)", rows)
        db.executemany(
            "INSERT INTO places_fts(rowid,name,address) VALUES (?,?,?)", fts_rows)
    db.commit()
    if n < args.min_rows:
        db.close()
        os.remove(args.out)
        sys.exit(f"\nonly {n:,} rows parsed from {args.geojson} (expected at least "
                 f"{args.min_rows:,}). Refusing to publish; the previous database, "
                 f"if any, is untouched. Pass --min-rows to override.")
    load_s = time.perf_counter() - t0
    print(f"  inserted {n:,} rows in {load_s:.1f}s"
          + (f"  ({skipped:,} skipped: no id or no geometry)" if skipped else ""))
    if odd_ids:
        print(f"  !! {len(odd_ids):,} ids were not 16-byte UUIDs and were stored "
              f"as utf-8 blobs, e.g. {odd_ids[0]!r}")

    db.executescript(INDEXES)
    db.commit()

    delete_stmt = (DELETE_CONTENTLESS if args.fts_mode == "contentless-delete"
                   else DELETE_EXTERNAL)
    db.executescript(TRIGGERS.format(delete_stmt=delete_stmt))
    db.executescript("INSERT INTO places_fts(places_fts) VALUES('optimize');")
    db.commit()

    # Ship a single self-contained file (no -wal sidecar) and stamp the version
    # Room compares against its @Database(version = ...).
    db.execute("PRAGMA journal_mode=DELETE")
    db.execute("PRAGMA user_version=1")
    db.commit()
    print("  running VACUUM...")
    db.execute("VACUUM")
    db.commit()
    db.close()

    # Only now does the new database replace the old one.
    os.replace(args.out, final)
    args.out = final
    db = sqlite3.connect(args.out)

    # ---------------- report ----------------
    size = os.path.getsize(args.out)
    size_mb = size / (1024 * 1024)
    total = db.execute("SELECT COUNT(*) FROM places").fetchone()[0]
    fts_total = db.execute("SELECT COUNT(*) FROM places_fts").fetchone()[0]

    print("\n" + "=" * 58)
    print(f"{'FILE':<22} {os.path.basename(args.out)}")
    print(f"{'SIZE':<22} {size_mb:,.1f} MB  ({size:,} bytes)")
    print(f"{'ROWS (places)':<22} {total:,}")
    print(f"{'ROWS (places_fts)':<22} {fts_total:,}")
    print(f"{'sqlite build version':<22} {sqlite3.sqlite_version}")
    print(f"{'user_version':<22} {db.execute('PRAGMA user_version').fetchone()[0]}")
    print(f"{'journal_mode':<22} {db.execute('PRAGMA journal_mode').fetchone()[0]}")
    print(f"{'fts mode':<22} {args.fts_mode}")
    idx = [r[0] for r in db.execute(
        "SELECT name FROM sqlite_master WHERE type='index' AND sql IS NOT NULL")]
    print(f"{'explicit indexes':<22} {', '.join(idx) if idx else '(none)'}")
    id_type = db.execute("SELECT typeof(id), LENGTH(id) FROM places LIMIT 1").fetchone()
    print(f"{'id storage':<22} {id_type[0]}({id_type[1]} bytes)")
    print("=" * 58)

    print("\nROWS PER DISPLAY_CATEGORY")
    print("-" * 44)
    for cat, cnt in db.execute(
            "SELECT display_category, COUNT(*) c FROM places "
            "GROUP BY display_category ORDER BY c DESC"):
        print(f"  {cat:<14} {cnt:>8,}  {100.0*cnt/total:>5.1f}%")
    print("-" * 44)
    print(f"  {'TOTAL':<14} {total:>8,}")

    print("\nSEARCH TESTS  (top 5, ordered by bm25 then confidence)")
    print("=" * 58)
    sql = ("SELECT p.name, p.display_category, p.address, p.confidence, "
           "       bm25(places_fts) AS rank "
           "FROM places_fts f JOIN places p ON p.rowid = f.rowid "
           "WHERE places_fts MATCH ? "
           "ORDER BY rank, p.confidence DESC LIMIT 5")
    for q in TEST_QUERIES:
        match = fts_query(q)
        t = time.perf_counter()
        hits = db.execute(sql, (match,)).fetchall()
        first_ms = (time.perf_counter() - t) * 1000
        best_ms = first_ms
        for _ in range(4):
            t = time.perf_counter()
            db.execute(sql, (match,)).fetchall()
            best_ms = min(best_ms, (time.perf_counter() - t) * 1000)
        n_total = db.execute(
            "SELECT COUNT(*) FROM places_fts WHERE places_fts MATCH ?",
            (match,)).fetchone()[0]
        print(f"\n  {q!r}   ->  {n_total:,} matches"
              f"   first {first_ms:.1f} ms, best {best_ms:.1f} ms")
        if not hits:
            print("     (no results)")
        for name, cat, addr, conf, rank in hits:
            addr = (addr or "-")
            if len(addr) > 52:
                addr = addr[:49] + "..."
            conf_s = f"{conf:.2f}" if conf is not None else " n/a"
            print(f"     [{cat:<9}] {(name or '-')[:34]:<34} conf={conf_s}  {addr}")

    if size_mb > args.size_limit_mb:
        print(f"\n!! SIZE WARNING: {size_mb:,.1f} MB exceeds the "
              f"{args.size_limit_mb:.0f} MB limit.")
    else:
        print(f"\nSize OK: {size_mb:,.1f} MB is under the "
              f"{args.size_limit_mb:.0f} MB limit "
              f"({args.size_limit_mb - size_mb:,.1f} MB headroom).")
    db.close()


if __name__ == "__main__":
    main()
