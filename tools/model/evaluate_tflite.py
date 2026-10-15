#!/usr/bin/env python3
"""Oceń wyeksportowany model TFLite na odseparowanych wycinkach plansz."""

from __future__ import annotations

import json
import re
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np
import tensorflow as tf

from train_model import CLASSES, SIZE


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Użycie: evaluate_tflite.py KATALOG_DANYCH MODEL.tflite")
    root, model_path = Path(sys.argv[1]), Path(sys.argv[2])
    test = tf.keras.utils.image_dataset_from_directory(
        root / "test",
        labels="inferred",
        label_mode="categorical",
        class_names=CLASSES,
        image_size=SIZE,
        batch_size=32,
        shuffle=False,
    )
    paths = test.file_paths
    interpreter = tf.lite.Interpreter(
        model_path=str(model_path),
        experimental_op_resolver_type=tf.lite.experimental.OpResolverType.BUILTIN_WITHOUT_DEFAULT_DELEGATES,
    )
    interpreter.allocate_tensors()
    input_tensor = interpreter.get_input_details()[0]
    output_tensor = interpreter.get_output_details()[0]
    if list(input_tensor["shape"]) != [1, *SIZE, 3] or input_tensor["dtype"] not in (np.uint8, np.float32):
        raise ValueError("Model musi przyjmować jeden obraz RGB 224×224")
    if list(output_tensor["shape"]) != [1, len(CLASSES)]:
        raise ValueError("Nieprawidłowy wymiar wyników modelu")
    if output_tensor["dtype"] not in (np.uint8, np.float32):
        raise ValueError("Model musi zwracać UINT8 lub FLOAT32")

    input_scale, input_zero = input_tensor["quantization"]
    output_scale, output_zero = output_tensor["quantization"]
    errors = []
    accepted_count = accepted_correct = correct_count = 0
    per_board = defaultdict(lambda: {"count": 0, "correct": 0})
    per_class = {name: {"count": 0, "correct": 0} for name in CLASSES}
    sample_index = 0
    for images, labels in test:
        for image, one_hot in zip(images.numpy(), labels.numpy()):
            path = Path(paths[sample_index])
            sample_index += 1
            if input_tensor["dtype"] == np.uint8:
                model_input = np.clip(np.rint(image / input_scale + input_zero), 0, 255).astype(np.uint8)
            else:
                model_input = image.astype(np.float32)
            interpreter.set_tensor(input_tensor["index"], model_input[None])
            interpreter.invoke()
            raw = interpreter.get_tensor(output_tensor["index"])[0]
            scores = ((raw.astype(np.float32) - output_zero) * output_scale
                      if output_tensor["dtype"] == np.uint8 else raw.astype(np.float32))
            actual = int(np.argmax(one_hot))
            predicted = int(np.argmax(scores))
            confidence = float(scores[predicted])
            correct = actual == predicted
            accepted = confidence >= 0.85
            correct_count += int(correct)
            accepted_count += int(accepted)
            accepted_correct += int(accepted and correct)
            per_class[CLASSES[actual]]["count"] += 1
            per_class[CLASSES[actual]]["correct"] += int(correct)
            board_match = re.fullmatch(r"board-(\d+)-cell-\d+", path.stem)
            if board_match:
                board = per_board[board_match.group(1)]
                board["count"] += 1
                board["correct"] += int(correct)
            if not correct:
                errors.append({
                    "file": path.name,
                    "actual": CLASSES[actual],
                    "predicted": CLASSES[predicted],
                    "confidence": confidence,
                })

    results = {
        "test_count": sample_index,
        "correct": correct_count,
        "accuracy": correct_count / sample_index,
        "accepted_threshold": 0.85,
        "accepted_count": accepted_count,
        "accepted_correct": accepted_correct,
        "accepted_precision": accepted_correct / accepted_count if accepted_count else None,
        "complete_boards": sum(v["count"] == 20 and v["correct"] == 20 for v in per_board.values()),
        "board_count": len(per_board),
        "per_class": per_class,
        "errors": errors,
        "input_quantization": [float(input_scale), int(input_zero)],
        "output_quantization": [float(output_scale), int(output_zero)],
        "input_dtype": str(input_tensor["dtype"]),
        "output_dtype": str(output_tensor["dtype"]),
        "interpreter": "BUILTIN_WITHOUT_DEFAULT_DELEGATES",
    }
    report_path = model_path.with_suffix(".tflite.metrics.json")
    report_path.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(results, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
