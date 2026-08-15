#!/usr/bin/env python3
"""One entry point for the Overture places pipeline.

    python3 tools/places_pipeline.py status      # what exists, how stale
    python3 tools/places_pipeline.py all         # run every stage that needs it
    python3 tools/places_pipeline.py seed        # just rebuild the catalog asset
    python3 tools/places_pipeline.py verify      # check every artefact

The stages, and what each one produces:

    fetch     Overture S3          -> data/mumbai_places.geojson   (309 MB)
    map       the extract          -> data/category_map.json       (12 buckets)
                                      data/seed_category_map.json  (6 PlaceCategory)
    db        the extract + map    -> data/mumbai_places.db        (71.7 MB, FTS5)
    seed      the db + seed map    -> build/mumbai-places-merged.tsv (CMPL)
    analyze   the extract          -> coverage report on stdout

`seed` is the one that touches the app: it produces the same CMPL catalog format
`tools/build_seed.py` writes and `PlaceCatalogCodec.kt` reads. It writes to
build/, never to app/src/main/assets/ — adopting a catalog is a deliberate copy,
not a side effect of running the pipeline.

Stages are skipped when their output is newer than their inputs; --force reruns
them anyway.
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PY = sys.executable

GEOJSON = ROOT / "data/mumbai_places.geojson"
CAT_MAP = ROOT / "data/category_map.json"
SEED_MAP = ROOT / "data/seed_category_map.json"
DB = ROOT / "data/mumbai_places.db"
SEED_TSV = ROOT / "build/mumbai-places-merged.tsv"
APP_TSV = ROOT / "app/src/main/assets/mumbai-places.tsv"

STAGES = {
    "fetch":   dict(script="fetch_overture_places.py", out=[GEOJSON], deps=[]),
    "map":     dict(script="gen_category_map.py",      out=[CAT_MAP, SEED_MAP], deps=[GEOJSON]),
    "db":      dict(script="build_places_db.py",       out=[DB], deps=[GEOJSON, CAT_MAP]),
    # APP_TSV is `seed`'s merge base but deliberately NOT a dependency: adopting
    # a catalog copies seed's own output over it, which would leave this stage
    # reporting stale for ever. build_seed_overture.py strips the rows it wrote
    # before merging, so an adopted catalog reconstructs the same source catalog
    # and is not a real input change.
    "seed":    dict(script="build_seed_overture.py",   out=[SEED_TSV], deps=[DB, SEED_MAP]),
    "analyze": dict(script="analyze_places.py",        out=[], deps=[GEOJSON]),
}
ORDER = ["fetch", "map", "db", "seed"]


def mtime(path):
    return path.stat().st_mtime if path.exists() else None


def stale(name):
    """True if the stage needs to run: an output is missing or older than a dep."""
    spec = STAGES[name]
    if not spec["out"]:
        return True
    outs = [mtime(p) for p in spec["out"]]
    if any(t is None for t in outs):
        return True
    deps = [mtime(p) for p in spec["deps"] if p.exists()]
    return bool(deps) and min(outs) < max(deps)


def run(name, extra=()):
    spec = STAGES[name]
    cmd = [PY, str(ROOT / "tools" / spec["script"]), *extra]
    print(f"\n{'='*66}\n== {name}: {' '.join(cmd[1:])}\n{'='*66}")
    result = subprocess.run(cmd, cwd=ROOT)
    if result.returncode != 0:
        sys.exit(f"\nstage '{name}' failed with exit code {result.returncode}")


def human(path):
    if not path.exists():
        return "missing"
    size = path.stat().st_size
    unit = f"{size/1e6:.1f} MB" if size >= 1e6 else f"{size/1e3:.0f} kB"
    import datetime
    when = datetime.datetime.fromtimestamp(path.stat().st_mtime).strftime("%Y-%m-%d %H:%M")
    return f"{unit:>9}  {when}"


def cmd_status():
    print(f"{'artefact':<38} {'size':>9}  {'modified':<16} state")
    print("-" * 78)
    for name in ORDER:
        for path in STAGES[name]["out"]:
            rel = path.relative_to(ROOT)
            state = "stale" if stale(name) else "ok"
            print(f"{str(rel):<38} {human(path)}  {state}")
    print()
    if APP_TSV.exists() and SEED_TSV.exists():
        sys.path.insert(0, str(ROOT / "tools"))
        from overture_common import read_cmpl_catalog
        a_stamp, a = read_cmpl_catalog(APP_TSV)
        b_stamp, b = read_cmpl_catalog(SEED_TSV)
        print(f"shipped catalog  {len(a):>7,} places  stamp {a_stamp}")
        print(f"built catalog    {len(b):>7,} places  stamp {b_stamp}")
        print("  -> identical" if a_stamp == b_stamp else
              f"  -> differ: adopting would add {len(b)-len(a):,} places")


def cmd_verify():
    sys.path.insert(0, str(ROOT / "tools"))
    from overture_common import read_cmpl_catalog
    import sqlite3

    ok = True

    def check(cond, msg):
        nonlocal ok
        print(("  PASS  " if cond else "  FAIL  ") + msg)
        ok = ok and bool(cond)

    print("artefacts")
    for path in (GEOJSON, CAT_MAP, SEED_MAP, DB, SEED_TSV):
        check(path.exists(), f"{path.relative_to(ROOT)} exists")

    if DB.exists():
        print("\ndatabase")
        # Read-write on purpose: fts5's 'integrity-check' is issued as an INSERT
        # into the virtual table, so a read-only handle rejects it before it can
        # verify anything. It inspects the index and writes nothing.
        db = sqlite3.connect(DB)
        n = db.execute("SELECT COUNT(*) FROM places").fetchone()[0]
        f = db.execute("SELECT COUNT(*) FROM places_fts").fetchone()[0]
        check(n > 1000, f"places has {n:,} rows")
        check(n == f, f"places_fts row count matches places ({f:,})")
        check(db.execute("PRAGMA integrity_check").fetchone()[0] == "ok",
              "PRAGMA integrity_check")
        try:
            db.execute("INSERT INTO places_fts(places_fts) VALUES('integrity-check')")
            check(True, "fts5 integrity-check (index agrees with content)")
        except sqlite3.DatabaseError as e:
            check(False, f"fts5 integrity-check: {e}")
        check(db.execute("PRAGMA user_version").fetchone()[0] == 1, "user_version is 1")
        db.close()

    if SEED_TSV.exists():
        print("\ncatalog")
        try:
            stamp, places = read_cmpl_catalog(SEED_TSV)
            check(True, f"{SEED_TSV.relative_to(ROOT)} decodes ({len(places):,} places)")
            cats = {p["category"] for p in places}
            valid = {"TOURIST", "RESTAURANT", "CAFE", "CULTURE", "PARK", "HIDDEN_GEM"}
            check(cats <= valid, f"categories are all real PlaceCategory constants: {sorted(cats)}")
            ids = [p["id"] for p in places]
            check(len(ids) == len(set(ids)), "ids are unique")
            if APP_TSV.exists():
                _, base = read_cmpl_catalog(APP_TSV)
                missing = {p["id"] for p in base} - set(ids)
                check(not missing,
                      f"all {len(base):,} shipped ids preserved "
                      f"({len(missing)} missing — upsert would duplicate them)")

            # The same assertions PlaceCatalogAssetTest makes about the shipped
            # asset, so a catalog that could not be adopted fails here first
            # rather than in the Kotlin suite after it is copied into place.
            print("\ncatalog vs PlaceCatalogAssetTest")
            check(len(places) > 2_500, f"more than 2,500 places ({len(places):,})")
            blank = [p for p in places if not p["id"].strip() or not p["name"].strip()
                     or not p["description"].strip()]
            check(not blank, f"every place has an id, a name and a description ({len(blank)} blank)")
            addressless = [p for p in places if not p["address"].strip()]
            check(not addressless, f"every place carries an address ({len(addressless):,} without)")
            outside = [p for p in places
                       if not (18.86 <= p["lat"] <= 19.30 and 72.75 <= p["lon"] <= 73.01)]
            check(not outside, f"every place is inside Mumbai ({len(outside):,} outside)")
            groups = {}
            for p in places:
                groups.setdefault(p["name"], []).append(p)
                repeated = {k: v for k, v in groups.items() if len(v) > 1}
            rc = sum(len(v) for v in repeated.values())
            amb = sum(len(v) - len({x["address"] for x in v}) for v in repeated.values())
            check(bool(repeated), "a full catalog has repeated names")
            check(amb <= rc * 0.15,
                  f"same-named places told apart by address: {amb/rc:.1%} ambiguous <= 15%")
            missing_cat = valid - cats
            check(not missing_cat, f"every category the app renders is present ({missing_cat or 'all 6'})")
        except Exception as e:
            check(False, f"catalog: {e}")

    print("\nRESULT:", "ALL CHECKS PASSED" if ok else "FAILURES ABOVE")
    return 0 if ok else 1


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("stage", choices=[*STAGES, "all", "status", "verify"])
    ap.add_argument("--force", action="store_true", help="run even if outputs look fresh")
    ap.add_argument("rest", nargs=argparse.REMAINDER,
                    help="arguments passed through to the stage script")
    args = ap.parse_args()

    if args.stage == "status":
        return cmd_status()
    if args.stage == "verify":
        sys.exit(cmd_verify())

    if args.stage == "all":
        for name in ORDER:
            if args.force or stale(name):
                run(name)
            else:
                print(f"== {name}: up to date, skipping (use --force to rerun)")
        print("\nall stages complete. `places_pipeline.py verify` to check them.")
        return

    if not args.force and not stale(args.stage) and STAGES[args.stage]["out"]:
        print(f"{args.stage}: up to date. Use --force to rerun.")
        return
    run(args.stage, args.rest)


if __name__ == "__main__":
    main()
