#!/usr/bin/env python3
"""Ponownie skalibruj INT8 na wszystkich obrazach treningowych."""

from __future__ import annotations

import sys
from pathlib import Path

import tensorflow as tf

from train_model import CLASSES, SIZE


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit("Użycie: export_int8_tflite.py MODEL.keras KATALOG_DANYCH MODEL.tflite")
    source, root, output = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    model = tf.keras.models.load_model(source, compile=False)
    train = tf.keras.utils.image_dataset_from_directory(
        root / "train",
        labels="inferred",
        label_mode="categorical",
        class_names=CLASSES,
        image_size=SIZE,
        batch_size=1,
        shuffle=False,
    )

    def representative_dataset():
        for images, _ in train:
            yield [images]

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = representative_dataset
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.uint8
    converter.inference_output_type = tf.uint8
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(converter.convert())
    print(f"Zapisano {output} ({output.stat().st_size} bajtów)")


if __name__ == "__main__":
    main()
