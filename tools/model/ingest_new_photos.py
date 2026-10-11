#!/usr/bin/env python3
"""Ingest labeled photos into Zdjecia, preserving original bytes and audit history."""

from __future__ import annotations

import csv
import hashlib
import os
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageOps

CLASSES = {
    "RABBIT", "FOX", "DRAGONFLY", "TROUT", "BEAR", "BEE",
    "EAGLE", "DEER", "WOLF", "STREAM", "MEADOW",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit("Usage: ingest_new_photos.py NEW_DIRECTORY INDEX.csv SESSION")
    source = Path(sys.argv[1]).resolve()
    index_path = Path(sys.argv[2]).resolve()
    root = index_path.parent
    session = sys.argv[3]
    if not source.is_dir() or not index_path.is_file() or not source.is_relative_to(root):
        raise ValueError("Source must be a directory inside the indexed photo collection")
    manifest_path = source / "manifest.csv"
    with manifest_path.open(newline="", encoding="utf-8-sig") as handle:
        manifest = list(csv.DictReader(handle))
    with index_path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        fields = reader.fieldnames
        records = list(reader)
    if fields is None or not manifest:
        raise ValueError("Missing index header or empty manifest")
    expected_fields = {"index", "path", "kind", "status", "label", "session", "note", "display_width", "display_height", "sha256"}
    if set(fields) != expected_fields:
        raise ValueError("Unexpected index columns")
    known_hashes = {record["sha256"] for record in records}
    names = [row["filename"] for row in manifest]
    if len(names) != len(set(names)):
        raise ValueError("Manifest contains duplicate filenames")
    actual = {path.name for path in source.iterdir() if path.suffix.lower() in {".jpg", ".jpeg", ".png"}}
    if actual != set(names):
        raise ValueError(f"Manifest and photos differ: {sorted(actual ^ set(names))}")

    planned = []
    next_index = max(int(record["index"]) for record in records) + 1
    for offset, row in enumerate(manifest):
        filename, label = row["filename"], row["label"]
        kind = row.get("kind") or "single"
        photo_session = row.get("session") or session
        if Path(filename).name != filename or kind not in {"single", "board"}:
            raise ValueError(f"Invalid filename or kind: {filename}, {kind}")
        if (kind == "single" and label not in CLASSES) or (kind == "board" and label):
            raise ValueError(f"Invalid label for {kind}: {filename}, {label}")
        if not photo_session or any(char not in "0123456789_-" for char in photo_session):
            raise ValueError(f"Invalid session: {photo_session}")
        original = source / filename
        target = root / ("pojedyncze" if kind == "single" else "uklady") / (label if kind == "single" else photo_session) / filename
        if target.exists() or not target.resolve().is_relative_to(root):
            raise ValueError(f"Unsafe or occupied target: {target}")
        digest = sha256(original)
        if digest in known_hashes:
            raise ValueError(f"Duplicate photo bytes: {filename}")
        known_hashes.add(digest)
        with Image.open(original) as image:
            width, height = ImageOps.exif_transpose(image).size
        planned.append((original, target, {
            "index": next_index + offset,
            "path": target.relative_to(root).as_posix(),
            "kind": kind, "status": "candidate", "label": label,
            "session": photo_session, "note": row.get("note", ""),
            "display_width": width, "display_height": height, "sha256": digest,
        }))

    for original, target, record in planned:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(original), str(target))
        if sha256(target) != record["sha256"]:
            raise ValueError(f"Hash changed during move: {target}")
    temporary = index_path.with_suffix(".csv.tmp")
    with temporary.open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(records + [item[2] for item in planned])
    os.replace(temporary, index_path)
    print(f"Ingested {len(planned)} unchanged photos; index now has {len(records) + len(planned)} entries")


if __name__ == "__main__":
    main()
