#!/usr/bin/env python3
"""Offline visual check of the edge-projection crop proposal on held-out photos.

The crop is a proposal for the user to review, not a card-classification metric.
"""

from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageOps


def weighted_position(profile: np.ndarray, fraction: float) -> int:
    cumulative = np.cumsum(profile, dtype=np.float64)
    if cumulative[-1] <= 0:
        raise ValueError("No edges in image")
    return int(np.searchsorted(cumulative, fraction * cumulative[-1]))


def locate(image: Image.Image) -> list[list[float]]:
    rgb = np.asarray(image.convert("RGB"))
    gray = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
    edges = cv2.Canny(cv2.GaussianBlur(gray, (5, 5), 0), 80, 200)
    x_profile = (edges > 0).sum(axis=0)
    y_profile = (edges > 0).sum(axis=1)
    x0 = weighted_position(x_profile, .01) / image.width
    x1 = weighted_position(x_profile, .99) / image.width
    y0 = weighted_position(y_profile, .01) / image.height
    y1 = weighted_position(y_profile, .99) / image.height
    bounds = [max(0, x0 - .02), max(0, y0 - .02), min(1, x1 + .035), min(1, y1 + .035)]

    # A fabric seam can add many edges outside the board. Use clear card outlines
    # to trim that false early bound, but never extrapolate from only a few cards.
    card_edges = cv2.Canny(cv2.GaussianBlur(gray, (5, 5), 0), 40, 120)
    card_edges = cv2.morphologyEx(card_edges, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    contours, _ = cv2.findContours(card_edges, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    candidates = []
    image_area = image.width * image.height
    for contour in contours:
        area = cv2.contourArea(contour)
        x, y, width, height = cv2.boundingRect(contour)
        if (.003 < area / image_area < .06 and .42 < width / height < 1.2
                and area / (width * height) > .55
                and width / image.width > .08 and height / image.height > .08):
            candidates.append((area, x, y, width, height))
    kept = []
    for candidate in sorted(candidates, reverse=True):
        _, x, y, width, height = candidate
        if any(
            max(0, min(x + width, kx + kw) - max(x, kx))
            * max(0, min(y + height, ky + kh) - max(y, ky))
            / min(width * height, kw * kh) > .65
            for _, kx, ky, kw, kh in kept
        ):
            continue
        kept.append(candidate)
    if len(kept) >= 10:
        card_x0 = min(box[1] for box in kept) / image.width
        card_x1 = max(box[1] + box[3] for box in kept) / image.width
        card_y0 = min(box[2] for box in kept) / image.height
        card_y1 = max(box[2] + box[4] for box in kept) / image.height
        if (card_x1 - card_x0) / (bounds[2] - bounds[0]) >= .85 and card_x0 - bounds[0] > .045:
            bounds[0] = card_x0 - .01
        if (card_y1 - card_y0) / (bounds[3] - bounds[1]) >= .7 and card_y0 - bounds[1] > .045:
            bounds[1] = card_y0 - .01
    def horizontal_span(start: float, end: float) -> tuple[float, float]:
        band = edges[int(start * image.height):int(end * image.height)]
        profile = (band > 0).sum(axis=0)
        low = max(0, weighted_position(profile, .01) / image.width - .02)
        high = min(1, weighted_position(profile, .99) / image.width + .02)
        if high - low < .55 or high - low > .98:
            return bounds[0], bounds[2]
        return low, high

    band_height = (bounds[3] - bounds[1]) * .22
    top_left, top_right = horizontal_span(bounds[1], bounds[1] + band_height)
    bottom_left, bottom_right = horizontal_span(bounds[3] - band_height, bounds[3])
    return [
        [top_left, bounds[1]], [top_right, bounds[1]],
        [bottom_right, bounds[3]], [bottom_left, bounds[3]],
    ]


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: probe_board_locator.py INDEX.csv OUTPUT_DIRECTORY")
    index_path, output = Path(sys.argv[1]), Path(sys.argv[2])
    output.mkdir(parents=True, exist_ok=True)
    with index_path.open(encoding="utf-8-sig", newline="") as handle:
        rows = [row for row in csv.DictReader(handle) if row["kind"] == "board" and int(row["index"]) >= 119]
    labels = {
        entry["image"]: entry["labels"]
        for entry in json.loads((index_path.parent / "board_annotations_review.json").read_text(encoding="utf-8"))
    }
    manifest = []
    sheet = Image.new("RGB", (1600, ((len(rows) + 3) // 4) * 570), "white")
    draw_sheet = ImageDraw.Draw(sheet)
    for offset, row in enumerate(rows):
        with Image.open(index_path.parent / row["path"]) as source:
            image = ImageOps.exif_transpose(source).convert("RGB")
        # The Android locator also normalizes analysis to at most 1200×1600.
        image.thumbnail((1200, 1600), Image.Resampling.BILINEAR)
        corners = locate(image)
        left = min(corners[0][0], corners[3][0])
        right = max(corners[1][0], corners[2][0])
        top, bottom = corners[0][1], corners[2][1]
        source_path = (index_path.parent / row["path"]).as_posix()
        manifest.append({
            "image": source_path,
            "session": row["session"],
            "split": "test",
            "rotation_degrees": 0,
            "corners": corners,
            "labels": labels[source_path],
        })
        if int(row["index"]) in {119, 122, 123, 126, 131, 133, 137, 139}:
            source = np.float32([[x * image.width, y * image.height] for x, y in corners])
            target = np.float32([[0, 0], [1000, 0], [1000, 1118], [0, 1118]])
            warped = cv2.warpPerspective(np.asarray(image), cv2.getPerspectiveTransform(source, target), (1000, 1118))
            preview = Image.fromarray(warped)
            grid = ImageDraw.Draw(preview)
            for column in range(1, 5):
                grid.line((column * 200, 0, column * 200, 1118), fill="red", width=3)
            for board_row in range(1, 4):
                grid.line((0, board_row * 1118 // 4, 1000, board_row * 1118 // 4), fill="red", width=3)
            preview.save(output / f"warp-{row['index']}.jpg", quality=85)
        image.thumbnail((400, 535))
        draw = ImageDraw.Draw(image)
        points = [(x * image.width, y * image.height) for x, y in corners]
        draw.line(points + points[:1], fill="red", width=5)
        sx, sy = (offset % 4) * 400, (offset // 4) * 570
        sheet.paste(image, (sx, sy + 30))
        draw_sheet.text((sx + 4, sy + 5), f"#{row['index']} ({left:.2f}, {top:.2f})-({right:.2f}, {bottom:.2f})", fill="black")
        print(f"{row['index']},{left:.3f},{top:.3f},{right:.3f},{bottom:.3f}")
    sheet.save(output / "board-locator-contact-sheet.jpg", quality=90)
    (output / "holdout-manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
