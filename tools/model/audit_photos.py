#!/usr/bin/env python3
"""Create local contact sheets and a lossless inventory of source photos."""

from __future__ import annotations

import csv
import hashlib
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps

TILE_WIDTH = 360
TILE_HEIGHT = 390
COLUMNS = 4
ROWS = 4


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: audit_photos.py PHOTO_DIRECTORY OUTPUT_DIRECTORY")
    source = Path(sys.argv[1])
    output = Path(sys.argv[2])
    output.mkdir(parents=True, exist_ok=True)
    paths = sorted(path for path in source.rglob("*") if path.suffix.lower() in {".jpg", ".jpeg", ".png"})
    records = []
    for index, path in enumerate(paths, start=1):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        with Image.open(path) as image:
            width, height = ImageOps.exif_transpose(image).size
            orientation = image.getexif().get(274, 1)
        records.append((index, str(path), width, height, orientation, path.stat().st_size, digest))

    with (output / "inventory.csv").open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.writer(handle)
        writer.writerow(("index", "path", "display_width", "display_height", "exif_orientation", "bytes", "sha256"))
        writer.writerows(records)

    font = ImageFont.load_default(size=19)
    for first in range(0, len(paths), COLUMNS * ROWS):
        sheet = Image.new("RGB", (COLUMNS * TILE_WIDTH, ROWS * TILE_HEIGHT), "#f5f5f5")
        draw = ImageDraw.Draw(sheet)
        for offset, path in enumerate(paths[first : first + COLUMNS * ROWS]):
            column, row = offset % COLUMNS, offset // COLUMNS
            x, y = column * TILE_WIDTH, row * TILE_HEIGHT
            with Image.open(path) as original:
                thumbnail = ImageOps.exif_transpose(original).convert("RGB")
                thumbnail.thumbnail((TILE_WIDTH - 20, TILE_HEIGHT - 75))
            sheet.paste(thumbnail, (x + (TILE_WIDTH - thumbnail.width) // 2, y + 8))
            draw.text((x + 12, y + TILE_HEIGHT - 60), f"{first + offset + 1:02d}  {path.name}", fill="black", font=font)
            draw.rectangle((x, y, x + TILE_WIDTH - 1, y + TILE_HEIGHT - 1), outline="#999999", width=1)
        sheet.save(output / f"sheet-{first // (COLUMNS * ROWS) + 1:02d}.jpg", quality=90)
    print(f"Audited {len(paths)} photos into {output}")


if __name__ == "__main__":
    main()
