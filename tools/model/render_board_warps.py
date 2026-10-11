#!/usr/bin/env python3
"""Render perspective-corrected boards for visual QA of manual annotations."""

from __future__ import annotations

import json
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageOps

WIDTH, HEIGHT = 1000, 1120


def render(entry: dict, output: Path) -> Path:
    path = Path(entry["image"])
    with Image.open(path) as original:
        image = ImageOps.exif_transpose(original).convert("RGB")
    image = image.rotate(entry["rotation_degrees"], expand=True)
    rgb = np.asarray(image)
    height, width = rgb.shape[:2]
    source = np.float32([[x * width, y * height] for x, y in entry["corners"]])
    target = np.float32([[0, 0], [WIDTH, 0], [WIDTH, HEIGHT], [0, HEIGHT]])
    warped = cv2.warpPerspective(rgb, cv2.getPerspectiveTransform(source, target), (WIDTH, HEIGHT))
    canvas = Image.fromarray(warped)
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.load_default(size=30)
    for column in range(1, 5):
        x = column * WIDTH // 5
        draw.line((x, 0, x, HEIGHT), fill="red", width=3)
    for row in range(1, 4):
        y = row * HEIGHT // 4
        draw.line((0, y, WIDTH, y), fill="red", width=3)
    for index, label in enumerate(entry["labels"]):
        x = (index % 5) * WIDTH // 5 + 8
        y = (index // 5) * HEIGHT // 4 + 8
        draw.text((x, y), label or "?", fill="red", stroke_fill="white", stroke_width=2, font=font)
    result = output / f"{path.stem}.jpg"
    canvas.save(result, quality=90)
    return result


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: render_board_warps.py ANNOTATIONS.json OUTPUT_DIRECTORY")
    entries = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    output = Path(sys.argv[2])
    output.mkdir(parents=True, exist_ok=True)
    rendered = []
    for entry in entries:
        if entry["corners"] is not None and entry["labels"] is not None:
            rendered.append((entry, render(entry, output)))
    for offset in range(0, len(rendered), 4):
        sheet = Image.new("RGB", (WIDTH * 2, (HEIGHT + 45) * 2), "white")
        draw = ImageDraw.Draw(sheet)
        for item, (entry, path) in enumerate(rendered[offset:offset + 4]):
            x, y = (item % 2) * WIDTH, (item // 2) * (HEIGHT + 45)
            with Image.open(path) as image:
                sheet.paste(image, (x, y + 45))
            draw.text((x + 8, y + 8), f"{Path(entry['image']).stem}  {entry['status']}", fill="black", font=ImageFont.load_default(size=25))
        sheet.save(output / f"sheet-{offset // 4 + 1:02d}.jpg", quality=88)
    print(f"Rendered {len(rendered)} boards in {output}")


if __name__ == "__main__":
    main()
