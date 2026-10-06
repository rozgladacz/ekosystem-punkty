#!/usr/bin/env python3
"""Wytnij pola 5x4 z opisanych zdjęć bez wysyłania danych poza komputer."""

from __future__ import annotations

import json
import sys
from pathlib import Path

import cv2
import numpy as np

CLASSES = {
    "RABBIT", "FOX", "DRAGONFLY", "TROUT", "BEAR", "BEE",
    "EAGLE", "DEER", "WOLF", "STREAM", "MEADOW",
}
WIDTH, HEIGHT = 1000, 1118


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Użycie: prepare_dataset.py MANIFEST.json KATALOG_WYJŚCIOWY")
    manifest_path = Path(sys.argv[1])
    output = Path(sys.argv[2])
    entries = json.loads(manifest_path.read_text(encoding="utf-8"))
    for board_index, entry in enumerate(entries):
        split = entry["split"]
        if split not in {"train", "validation", "test"}:
            raise ValueError(f"Nieprawidłowy split: {split}")
        labels = entry["labels"]
        if len(labels) != 20 or not set(labels) <= CLASSES:
            raise ValueError(f"Plansza {board_index}: wymagane jest 20 prawidłowych etykiet")
        image_path = Path(entry["image"])
        image = cv2.imread(str(image_path))
        if image is None:
            raise FileNotFoundError(image_path)
        height, width = image.shape[:2]
        source = np.float32([[x * width, y * height] for x, y in entry["corners"]])
        target = np.float32([[0, 0], [WIDTH, 0], [WIDTH, HEIGHT], [0, HEIGHT]])
        warped = cv2.warpPerspective(image, cv2.getPerspectiveTransform(source, target), (WIDTH, HEIGHT))
        cell_width, cell_height = WIDTH // 5, HEIGHT // 4
        for index, label in enumerate(labels):
            row, column = divmod(index, 5)
            inset_x, inset_y = int(cell_width * 0.06), int(cell_height * 0.06)
            crop = warped[
                row * cell_height + inset_y:(row + 1) * cell_height - inset_y,
                column * cell_width + inset_x:(column + 1) * cell_width - inset_x,
            ]
            directory = output / split / label
            directory.mkdir(parents=True, exist_ok=True)
            cv2.imwrite(str(directory / f"board-{board_index:04d}-cell-{index:02d}.jpg"), crop)


if __name__ == "__main__":
    main()

