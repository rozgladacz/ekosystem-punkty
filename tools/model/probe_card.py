#!/usr/bin/env python3
"""Render local exposure probes of one questionable board region."""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageEnhance, ImageOps


def main() -> None:
    if len(sys.argv) != 8:
        raise SystemExit("Usage: probe_card.py INPUT ROTATION X1 Y1 X2 Y2 OUTPUT")
    path, rotation, x1, y1, x2, y2, output = sys.argv[1:]
    with Image.open(path) as original:
        image = ImageOps.exif_transpose(original).convert("RGB")
    image = image.rotate(int(rotation), expand=True)
    width, height = image.size
    box = (round(float(x1) * width), round(float(y1) * height), round(float(x2) * width), round(float(y2) * height))
    crop = image.crop(box)
    standard = crop.copy()
    darker = ImageEnhance.Brightness(crop).enhance(0.4)
    lighter = ImageEnhance.Brightness(crop).enhance(2.0)
    contrast = ImageOps.autocontrast(crop, cutoff=0.5)
    tw = 600
    panels = []
    for panel in (standard, darker, lighter, contrast):
        panel.thumbnail((tw, 900))
        panels.append(panel)
    canvas = Image.new("RGB", (tw * 4, max(panel.height for panel in panels)), "white")
    for index, panel in enumerate(panels):
        canvas.paste(panel, (tw * index, 0))
    Path(output).parent.mkdir(parents=True, exist_ok=True)
    canvas.save(output, quality=95)


if __name__ == "__main__":
    main()
