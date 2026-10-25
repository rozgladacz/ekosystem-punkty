#!/usr/bin/env python3
"""Audit an opt-in app training ZIP without publishing its photos or labels."""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
from io import BytesIO
import json
from pathlib import Path
from zipfile import ZipFile

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageOps


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--model", type=Path, help="Optionally compare original and 180-degree card crops")
    parser.add_argument("--fixtures", action="store_true", help="Save verified private files for Android diagnostics")
    parser.add_argument("--locator-results", type=Path, help="Optional Android locator output for the same captures")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    try:
        font = ImageFont.truetype("arial.ttf", 16)
    except OSError:
        font = ImageFont.load_default(size=16)
    entries = []
    errors = []
    class_stats = defaultdict(Counter)
    groups = defaultdict(list)
    summary = Counter()
    confusions = Counter()
    orientation_metrics = defaultdict(Counter)
    interpreter = None
    locator_results = {entry["capture_id"]: entry for entry in json.loads(args.locator_results.read_text())} if args.locator_results else {}
    locator_errors = []
    if args.model:
        import tensorflow as tf
        interpreter = tf.lite.Interpreter(model_path=str(args.model), num_threads=4)
        interpreter.allocate_tensors()
        input_spec, output_spec = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
        if input_spec["dtype"] != np.float32 or output_spec["dtype"] != np.float32:
            raise ValueError("Orientation experiment expects the bundled FLOAT32 model")

    def predict(crop):
        model_input = np.asarray(crop.resize((224,224), Image.Resampling.BILINEAR), np.float32)
        interpreter.set_tensor(input_spec["index"], model_input[None])
        interpreter.invoke()
        return interpreter.get_tensor(output_spec["index"])[0]

    with ZipFile(args.archive) as archive:
        labels = archive.read("card_labels.txt").decode("utf-8").split()
        metadata = [json.loads(archive.read(name)) for name in archive.namelist()
                    if name.startswith("metadata/") and name.endswith(".json")]
        metadata.sort(key=lambda entry: (entry["group_id"], entry["shot_index"]))
        photo_sheet = Image.new("RGB", (1440, ((len(metadata)+2)//3)*680), "white")
        warp_sheet = Image.new("RGB", (1800, ((len(metadata)+2)//3)*730), "white")
        for index, record in enumerate(metadata):
            raw = archive.read(record["image_file"])
            if hashlib.sha256(raw).hexdigest() != record["image_sha256"]:
                raise ValueError(f"SHA mismatch: {record['capture_id']}")
            if args.fixtures:
                fixture_root = args.output/"fixtures"
                (fixture_root/"images").mkdir(parents=True, exist_ok=True)
                (fixture_root/"metadata").mkdir(parents=True, exist_ok=True)
                # Never use untrusted ZIP paths as output filenames.
                fixture_name = Path(record["image_file"]).name
                if fixture_name != record["capture_id"]+".jpg":
                    raise ValueError("Capture ID and image filename differ")
                (fixture_root/"images"/fixture_name).write_bytes(raw)
                (fixture_root/"metadata"/(record["capture_id"]+".json")).write_text(
                    json.dumps(record), encoding="utf-8")
            image = ImageOps.exif_transpose(Image.open(BytesIO(raw))).convert("RGB")
            if image.size != (record["image_width"], record["image_height"]):
                raise ValueError("Image dimensions differ from metadata")
            if record["status"] != "labeled":
                raise ValueError("Confirmed labels are required for this audit")
            corners = np.asarray(record["corners"], np.float32)
            if corners.shape != (4, 2) or np.any(corners < 0) or np.any(corners > 1):
                raise ValueError("Invalid normalized corners")
            truth = record["labels"]
            probabilities = np.asarray(record["class_probabilities"], np.float64)
            if probabilities.shape != (20, len(labels)) or not np.all(np.isfinite(probabilities)):
                raise ValueError("Missing/invalid per-cell probabilities")
            predicted = probabilities.argmax(axis=1)
            confidence = probabilities.max(axis=1)
            correct = np.asarray([labels[p] == t for p, t in zip(predicted, truth)])
            high = confidence >= .85
            top3 = np.argsort(-probabilities, axis=1)[:, :3]
            summary.update({"photos": 1, "cells": 20, "correct": int(correct.sum()),
                            "high_confidence": int(high.sum()), "high_correct": int((high & correct).sum()),
                            "low_confidence": int((~high).sum()), "low_correct": int((~high & correct).sum()),
                            "top3_correct": sum(labels.index(t) in ranks for t, ranks in zip(truth, top3)),
                            "manually_touched": len(record["manually_corrected_indices"])})
            group = record["group_id"]
            groups[group].append(record)
            item = {"number": index+1, "capture_id": record["capture_id"], "group_id": group,
                    "shot_index": record["shot_index"], "correct": int(correct.sum()),
                    "high_confidence": int(high.sum()), "low_confidence": int((~high).sum()),
                    "corners": record["corners"], "errors": []}
            title = f"#{index+1} {group[:8]} / shot {record['shot_index']} | {correct.sum()}/20 | low {(~high).sum()}"
            preview = image.copy()
            preview.thumbnail((470, 625))
            draw = ImageDraw.Draw(preview)
            points = [(float(x*preview.width), float(y*preview.height)) for x, y in corners]
            draw.line(points+points[:1], fill="#00ff55", width=3)
            if record["capture_id"] in locator_results:
                automatic = np.asarray(locator_results[record["capture_id"]]["automatic_corners"])
                delta = np.abs(automatic-corners)
                locator_errors.append({"capture_id": record["capture_id"],
                    "max_coordinate_delta": float(delta.max()),
                    "mean_corner_distance_pixels": float(np.linalg.norm(delta*np.asarray(image.size), axis=1).mean())})
                auto_points = [(float(x*preview.width), float(y*preview.height)) for x,y in automatic]
                draw.line(auto_points+auto_points[:1],fill="#ff3030",width=3)
            ox, oy = index % 3*480, index // 3*680
            photo_sheet.paste(preview, (ox, oy+35))
            ImageDraw.Draw(photo_sheet).text((ox+4, oy+4), title, font=font, fill="black")

            source = corners * np.asarray(image.size, np.float32)
            target = np.float32([[0, 0], [1000, 0], [1000, 1118], [0, 1118]])
            warped = Image.fromarray(cv2.warpPerspective(np.asarray(image),
                                     cv2.getPerspectiveTransform(source, target), (1000, 1118)))
            board = Image.new("RGB", (600, 690), "white")
            ImageDraw.Draw(board).text((4, 3), title, font=font, fill="black")
            for cell, expected in enumerate(truth):
                row, col = divmod(cell, 5)
                crop = warped.crop((col*200+12, row*279+16,
                                    (col+1)*200-12, (row+1)*279-16))
                if interpreter is not None:
                    original = predict(crop)
                    rotated = predict(crop.transpose(Image.Transpose.ROTATE_180))
                    variants = {"replayed": original, "rotated_180": rotated,
                                "mean_both": (original+rotated)/2,
                                "more_confident": original if original.max()>=rotated.max() else rotated,
                                "rotate_only_if_uncertain": original if original.max()>=.85 else
                                    original if original.max()>=rotated.max() else rotated}
                    for name,scores in variants.items():
                        p = labels[int(scores.argmax())]
                        orientation_metrics[name].update(cells=1,correct=int(p==expected),
                            high=int(scores.max()>=.85),high_correct=int(scores.max()>=.85 and p==expected))
                    orientation_metrics["replayed"]["same_top1_as_metadata"] += int(original.argmax()==predicted[cell])
                crop.thumbnail((116, 140))
                x, y = col*120, 30+row*165
                board.paste(crop, (x, y))
                predicted_label = labels[predicted[cell]]
                color = "#d22" if not correct[cell] else "#a65c00" if not high[cell] else "#164e18"
                text = f"{predicted_label[:3]} {confidence[cell]:.0%} / {expected[:3]}"
                ImageDraw.Draw(board).text((x+2, y+140), text, font=font, fill=color)
                class_stats[expected].update(total=1, correct=int(correct[cell]), low=int(not high[cell]))
                if not correct[cell]:
                    detail = {"cell": cell, "expected": expected, "predicted": predicted_label,
                              "confidence": float(confidence[cell]), "truth_rank":
                              int(np.where(np.argsort(-probabilities[cell]) == labels.index(expected))[0][0])+1}
                    item["errors"].append(detail)
                    errors.append((crop.copy(), f"#{index+1} ({row+1},{col+1})", detail))
                    confusions[(expected, predicted_label)] += 1
            warp_sheet.paste(board, (index % 3*600, index // 3*730))
            entries.append(item)

    for group, shots in groups.items():
        if any(shot["labels"] != shots[0]["labels"] for shot in shots):
            raise ValueError(f"Labels differ inside group {group}")
    summary["layouts"] = len(groups)
    summary["unique_cells"] = len(groups)*20
    summary["paired_layouts"] = sum(len(shots)>1 for shots in groups.values())
    report = {"summary": dict(summary), "classes": {k: dict(v) for k,v in class_stats.items()},
              "confusions": [{"expected": k[0], "predicted": k[1], "count": v}
                             for k,v in confusions.most_common()], "photos": entries}
    if interpreter is not None:
        report["orientation_experiment"] = {k:dict(v) for k,v in orientation_metrics.items()}
    if locator_errors:
        report["locator_comparison"] = {"photos":len(locator_errors),
            "within_055_frame_delta":sum(entry["max_coordinate_delta"]<=.055 for entry in locator_errors),
            "median_max_coordinate_delta":float(np.median([entry["max_coordinate_delta"] for entry in locator_errors])),
            "photos_detail":locator_errors}
    (args.output/"audit.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    photo_sheet.save(args.output/"photos.jpg", quality=90)
    warp_sheet.save(args.output/"boards.jpg", quality=90)
    if errors:
        sheet = Image.new("RGB", (900, ((len(errors)+4)//5)*230), "white")
        draw = ImageDraw.Draw(sheet)
        for index, (crop, position, detail) in enumerate(errors):
            x,y=index%5*180,index//5*230
            sheet.paste(crop, (x,y))
            draw.text((x,y+143), f"{position}\n{detail['predicted']} {detail['confidence']:.0%}\ntrue: {detail['expected']}",font=font,fill="black")
        sheet.save(args.output/"errors.jpg",quality=92)
    print(json.dumps({k:v for k,v in report.items() if k!="photos"}, indent=2))
    for item in entries:
        print(item["number"], item["capture_id"][:8], item["correct"], item["errors"])


if __name__ == "__main__":
    main()
