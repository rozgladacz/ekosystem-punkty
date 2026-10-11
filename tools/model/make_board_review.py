#!/usr/bin/env python3
"""Render board photos with normalized coordinate guides for manual review."""

from __future__ import annotations

import csv
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps

ROTATIONS = {47: 180, 52: 180, 53: 90, 58: 90, 64: 180, 65: 180, 66: 180, 67: 180, 70: 180}


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: make_board_review.py INDEX.csv OUTPUT_DIRECTORY")
    index_path = Path(sys.argv[1]).resolve()
    output = Path(sys.argv[2]).resolve()
    output.mkdir(parents=True, exist_ok=True)
    font = ImageFont.load_default(size=21)
    with index_path.open(newline="", encoding="utf-8-sig") as handle:
        records = list(csv.DictReader(handle))
    for record in records:
        if record["kind"] != "board" or record["status"] != "candidate":
            continue
        index = int(record["index"])
        path = index_path.parent / record["path"]
        with Image.open(path) as source:
            image = ImageOps.exif_transpose(source).convert("RGB")
        rotation = ROTATIONS.get(index, 0)
        if rotation:
            image = image.rotate(rotation, expand=True)
        image.thumbnail((1200, 1600))
        width, height = image.size
        canvas = Image.new("RGB", (width, height + 55), "white")
        canvas.paste(image, (0, 55))
        draw = ImageDraw.Draw(canvas)
        draw.text((12, 12), f"#{index}  {path.name}  rotation={rotation}", fill="black", font=font)
        for tenth in range(1, 10):
            x = round(width * tenth / 10)
            y = 55 + round(height * tenth / 10)
            draw.line((x, 55, x, height + 55), fill=(255, 255, 255), width=1)
            draw.line((0, y, width, y), fill=(255, 255, 255), width=1)
            draw.text((x + 2, 57), f".{tenth}", fill="red", stroke_fill="white", stroke_width=2, font=font)
            draw.text((2, y + 2), f".{tenth}", fill="red", stroke_fill="white", stroke_width=2, font=font)
        canvas.save(output / f"board-{index:02d}.jpg", quality=90)
    print(f"Created review images in {output}")


if __name__ == "__main__":
    main()
