#!/usr/bin/env python3
"""Expand human-readable board reviews into the model manifest schema."""

from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

CODES = {
    "R": "RABBIT", "F": "FOX", "D": "DRAGONFLY", "T": "TROUT",
    "B": "BEAR", "H": "BEE", "E": "EAGLE", "J": "DEER",
    "W": "WOLF", "S": "STREAM", "M": "MEADOW", "?": None,
}


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        return list(csv.DictReader(handle))


def main() -> None:
    if len(sys.argv) != 5:
        raise SystemExit("Usage: build_board_annotations.py INDEX.csv LABELS.csv GEOMETRY.csv OUTPUT.json")
    index_path, labels_path, geometry_path, output_path = map(Path, sys.argv[1:])
    boards = [row for row in read_csv(index_path) if row["kind"] == "board"]
    labels = {row["index"]: row for row in read_csv(labels_path)}
    geometry = {row["index"]: row for row in read_csv(geometry_path)}
    candidates = {row["index"] for row in boards if row["status"] == "candidate"}
    if set(labels) != candidates or not set(geometry) <= candidates:
        raise ValueError("Labels must cover all candidate boards; geometry may be pending")
    result = []
    for board in boards:
        index = board["index"]
        entry = {
            "image": (index_path.parent / board["path"]).as_posix(),
            "session": board["session"],
            "split": None,
            "rotation_degrees": 0,
            "corners": None,
            "labels": None,
            "status": board["status"],
            "geometry_status": None,
            "note": board["note"],
        }
        if index in candidates:
            label_row = labels[index]
            rows = label_row["rows"].split("/")
            if len(rows) != 4 or any(len(row) != 5 or set(row) - CODES.keys() for row in rows):
                raise ValueError(f"Board {index}: expected four valid five-card rows")
            geometry_row = geometry.get(index)
            corners = None
            rotation = 0
            geometry_status = "pending"
            if geometry_row is not None:
                raw_corners = geometry_row["corners"].split("/")
                corners = [[float(value) for value in corner.split(":")] for corner in raw_corners]
                if len(corners) != 4 or any(len(corner) != 2 or any(not 0 <= value <= 1 for value in corner) for corner in corners):
                    raise ValueError(f"Board {index}: four normalized corners required")
                rotation = int(geometry_row["rotation_degrees"])
                if rotation not in {0, 90, 180, 270}:
                    raise ValueError(f"Board {index}: invalid rotation")
                geometry_status = geometry_row["geometry_status"]
            entry.update({
                "rotation_degrees": rotation,
                "corners": corners,
                "labels": [CODES[code] for row in rows for code in row],
                "status": label_row["review_status"],
                "geometry_status": geometry_status,
                "note": "; ".join(filter(None, [label_row["note"], board["note"]])),
            })
        result.append(entry)
    output_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {len(result)} board entries: {sum(item['labels'] is not None for item in result)} labeled")


if __name__ == "__main__":
    main()
