#!/usr/bin/env python3
"""Organize the audited October 2026 photos without deleting or recompressing them."""

from __future__ import annotations

import csv
import hashlib
import json
import shutil
import sys
from pathlib import Path

SINGLES = {
    "RABBIT": {23, 24, 25, 76},
    "FOX": {14, 15, 16, 71},
    "DRAGONFLY": {17, 18, 19, 79},
    "TROUT": {9, 10, 12, 13, 77},
    "BEAR": {26, 27, 28, 74},
    "BEE": {6, 7, 8, 73},
    "EAGLE": {72},
    "DEER": {75},
    "WOLF": {20, 21, 22, 78},
    "STREAM": {1, 2, 3, 4, 5, 81},
    "MEADOW": {29, 30, 31, 80},
}
REJECTED_SINGLES = {
    3: "karta poza kadrem",
    4: "silnie przepalone i poruszone",
    11: "ciemny kadr bez widocznej karty",
    12: "karta nieostra i ucięta",
}
REJECTED_BOARDS = {
    51: "karty zachodzą na siebie; układ sfotografowany bokiem",
    54: "karty zachodzą na siebie i tworzą nieregularną siatkę",
    57: "część kart zachodzi na siebie, układ jest obrócony i ucięty",
}
ROTATED_BOARDS = {53, 58, 64, 65, 66, 67}


def board_session(index: int) -> str:
    for first, last, name in (
        (33, 35, "2026-10-07_1843"),
        (36, 41, "2026-10-07_1858"),
        (42, 47, "2026-10-07_1906"),
        (48, 54, "2026-10-10_1333"),
        (55, 58, "2026-10-10_1344"),
        (59, 68, "2026-10-10_1426"),
        (69, 70, "2026-10-10_1945"),
    ):
        if first <= index <= last:
            return name
    raise ValueError(f"Unexpected board index: {index}")


def destination_for(index: int, filename: str) -> tuple[Path, str, str, str, str]:
    if index in REJECTED_SINGLES:
        return Path("odrzucone/pojedyncze") / filename, "single", "excluded", "", REJECTED_SINGLES[index]
    for label, indices in SINGLES.items():
        if index in indices:
            note = "karta obrócona o 180 stopni" if index >= 71 else ""
            return Path("pojedyncze") / label / filename, "single", "candidate", label, note
    if index == 32:
        return Path("pogladowe") / filename, "overview", "reference", "", "trzy plansze na jednym zdjęciu"
    if index in range(33, 71):
        session = board_session(index)
        if index in REJECTED_BOARDS:
            return Path("odrzucone/uklady") / session / filename, "board", "excluded", "", REJECTED_BOARDS[index]
        note = "obrót wymaga korekty przy adnotacji" if index in ROTATED_BOARDS else ""
        return Path("uklady") / session / filename, "board", "candidate", "", note
    raise ValueError(f"Unexpected photo index: {index}")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: organize_photos.py PHOTO_DIRECTORY AUDIT_INVENTORY.csv")
    source = Path(sys.argv[1]).resolve()
    inventory = Path(sys.argv[2]).resolve()
    if not source.is_dir() or inventory.parent == source:
        raise ValueError("Expected a photo directory and separate audit inventory")
    with inventory.open(newline="", encoding="utf-8-sig") as handle:
        rows = list(csv.DictReader(handle))
    if len(rows) != 81 or {int(row["index"]) for row in rows} != set(range(1, 82)):
        raise ValueError("This inventory must contain exactly the 81 audited photos")

    planned = []
    for row in rows:
        index = int(row["index"])
        name = Path(row["path"]).name
        old = source / name
        relative, kind, status, label, note = destination_for(index, name)
        new = source / relative
        if not new.resolve().is_relative_to(source):
            raise ValueError(f"Destination escapes source directory: {new}")
        present = old if old.is_file() else new
        if not present.is_file() or sha256(present) != row["sha256"]:
            raise ValueError(f"Photo missing or changed: {name}")
        if old.is_file() and new.is_file():
            raise ValueError(f"Both original and destination exist: {name}")
        planned.append((index, old, new, kind, status, label, note, row))

    for _, old, new, _, _, _, _, _ in planned:
        if old.is_file():
            new.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(old), str(new))

    records = []
    for index, _, new, kind, status, label, note, old_record in planned:
        if sha256(new) != old_record["sha256"]:
            raise ValueError(f"Hash changed during move: {new}")
        records.append({
            "index": index,
            "path": new.relative_to(source).as_posix(),
            "kind": kind,
            "status": status,
            "label": label,
            "session": board_session(index) if kind == "board" else ("2026-10-07_1754" if index <= 31 else "2026-10-10_1946" if index >= 71 else ""),
            "note": note,
            "display_width": int(old_record["display_width"]),
            "display_height": int(old_record["display_height"]),
            "sha256": old_record["sha256"],
        })

    with (source / "index.csv").open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=records[0].keys())
        writer.writeheader()
        writer.writerows(records)
    draft = [
        {
            "image": str((source / record["path"]).relative_to(Path.cwd()).as_posix()),
            "session": record["session"],
            "split": None,
            "corners": None,
            "labels": None,
            "status": record["status"],
        }
        for record in records if record["kind"] == "board"
    ]
    draft_path = source / "board_annotations_draft.json"
    if not draft_path.exists():
        draft_path.write_text(json.dumps(draft, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Organized {len(records)} unchanged photos under {source}")
    print("Candidates:", sum(r["kind"] == "single" and r["status"] == "candidate" for r in records), "single cards,", sum(r["kind"] == "board" and r["status"] == "candidate" for r in records), "boards")


if __name__ == "__main__":
    main()
