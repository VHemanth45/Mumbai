#!/usr/bin/env python3
"""Download Overture `place` features for a bbox, bypassing the STAC catalog.

    python3 tools/fetch_overture_places.py
    python3 tools/fetch_overture_places.py --bbox 72.75,18.85,73.20,19.50
    python3 tools/fetch_overture_places.py --release 2026-07-22.0 --list-releases

Why this exists instead of `overturemaps download`: that CLI validates its
--release against https://stac.overturemaps.org/catalog.json, which currently
returns 404 for every path. The validation runs even when --release is given
explicitly and even with --no-stac, so the CLI cannot be used at all while the
endpoint is down. This calls the same underlying library functions directly.

Two things this handles that the CLI does not:
  * SSL — python.org builds on macOS ship no CA bundle, so certifi is wired in.
  * Atomicity — the download goes to a .part file and is renamed only on
    success, so a failed run cannot truncate a good 309 MB extract to zero.
"""
from __future__ import annotations

import argparse
import os
import ssl
import sys
import time
import urllib.request
from pathlib import Path
from xml.etree import ElementTree

BUCKET = "overturemaps-us-west-2"
LISTING = f"https://{BUCKET}.s3.amazonaws.com/?list-type=2&prefix=release/&delimiter=/"
DEFAULT_BBOX = "72.75,18.85,73.20,19.50"     # Mumbai metro
DEFAULT_OUT = "data/mumbai_places.geojson"


def install_certifi():
    """python.org's macOS build has no CA bundle; without this every https
    call dies with CERTIFICATE_VERIFY_FAILED."""
    try:
        import certifi
    except ImportError:
        print("note: certifi not installed; TLS may fail on macOS python.org builds",
              file=sys.stderr)
        return
    os.environ.setdefault("SSL_CERT_FILE", certifi.where())
    os.environ.setdefault("REQUESTS_CA_BUNDLE", certifi.where())
    ssl._create_default_https_context = lambda *a, **k: ssl.create_default_context(
        cafile=certifi.where())


def available_releases():
    """Release ids present in the public S3 bucket, oldest first.

    Overture keeps only the most recent few, so this is also the answer to
    'why did the release I pinned last month stop working'.
    """
    with urllib.request.urlopen(LISTING, timeout=60) as resp:
        root = ElementTree.fromstring(resp.read())
    ns = {"s3": "http://s3.amazonaws.com/doc/2006-03-01/"}
    out = []
    for prefix in root.findall(".//s3:CommonPrefixes/s3:Prefix", ns):
        name = (prefix.text or "").removeprefix("release/").strip("/")
        if name:
            out.append(name)
    return sorted(out)


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--bbox", default=DEFAULT_BBOX, help="lon_min,lat_min,lon_max,lat_max")
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--release", default=None, help="default: newest available on S3")
    ap.add_argument("--type", default="place", help="Overture type to download")
    ap.add_argument("--list-releases", action="store_true")
    ap.add_argument("--connect-timeout", type=int, default=120)
    ap.add_argument("--request-timeout", type=int, default=600)
    args = ap.parse_args()

    install_certifi()

    releases = available_releases()
    if args.list_releases:
        print("releases available on S3:")
        for r in releases:
            print(f"  {r}")
        return
    if not releases:
        sys.exit("no releases found in the S3 bucket listing")

    release = args.release or releases[-1]
    if release not in releases:
        sys.exit(f"release {release} is not on S3. Available: {', '.join(releases)}")

    try:
        bbox = tuple(float(v) for v in args.bbox.split(","))
    except ValueError:
        sys.exit(f"could not parse --bbox {args.bbox!r}")
    if len(bbox) != 4:
        sys.exit("--bbox needs exactly 4 comma-separated numbers")
    if not (bbox[0] < bbox[2] and bbox[1] < bbox[3]):
        sys.exit(f"--bbox is inside out: {bbox}")

    from overturemaps.cli import copy, get_writer
    from overturemaps.core import record_batch_reader

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    # Write beside the target and rename at the end: a crash or a Ctrl-C must
    # not destroy the extract that is already there.
    part = out.with_suffix(out.suffix + ".part")

    print(f"release  {release}" + ("" if args.release else "  (newest on S3)"))
    print(f"bbox     {bbox}")
    print(f"type     {args.type}")
    print(f"out      {out}")

    t0 = time.perf_counter()
    reader = record_batch_reader(args.type, bbox, release,
                                 args.connect_timeout, args.request_timeout, stac=False)
    if reader is None:
        sys.exit("overturemaps returned no reader for that query")

    try:
        with open(part, "w", encoding="utf-8") as fh:
            with get_writer("geojson", fh, schema=reader.schema) as writer:
                copy(reader, writer)
    except BaseException:
        part.unlink(missing_ok=True)
        raise

    size = part.stat().st_size
    if size == 0:
        part.unlink(missing_ok=True)
        sys.exit("download produced an empty file; leaving the previous extract alone")

    part.replace(out)
    print(f"\nwrote {out}  {size/1e6:.1f} MB in {time.perf_counter()-t0:.0f}s")


if __name__ == "__main__":
    main()
