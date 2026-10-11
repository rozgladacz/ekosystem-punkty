#!/usr/bin/env python3
"""Select only visually reviewed boards and split them by session."""

from __future__ import annotations

import json
import sys
from pathlib import Path

SPLITS = {
    "2026-10-07_1843": "train",
    "2026-10-07_1858": "train",
    "2026-10-07_1906": "test",
    "2026-10-10_1333": "validation",
    "2026-10-10_1344": "validation",
    "2026-10-10_1426": "train",
    "2026-10-10_1945": "test",
    "2026-10-11_1028": "train",
    "2026-10-11_1031": "validation",
    "2026-10-11_1834": "test",
}


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: make_prototype_manifest.py REVIEW.json OUTPUT.json")
    entries = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    selected = []
    for entry in entries:
        if entry["status"] != "labeled" or entry["geometry_status"] != "estimated":
            continue
        if entry["session"] not in SPLITS or len(entry["labels"]) != 20 or not all(entry["labels"]):
            raise ValueError(f"Incomplete reviewed board: {entry['image']}")
        selected.append({
            "image": entry["image"],
            "split": SPLITS[entry["session"]],
            "session": entry["session"],
            "rotation_degrees": entry["rotation_degrees"],
            "corners": entry["corners"],
            "labels": entry["labels"],
        })
    Path(sys.argv[2]).write_text(json.dumps(selected, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Selected {len(selected)} reviewed boards: " + ", ".join(f"{split}={sum(item['split'] == split for item in selected)}" for split in ("train", "validation", "test")))


if __name__ == "__main__":
    main()
