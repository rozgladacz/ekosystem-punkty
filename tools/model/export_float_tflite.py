#!/usr/bin/env python3
"""Wyeksportuj nieskwantyzowany model kontrolny do oceny straty po INT8."""

from __future__ import annotations

import sys
from pathlib import Path

import tensorflow as tf


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Użycie: export_float_tflite.py MODEL.keras MODEL.tflite")
    source, output = Path(sys.argv[1]), Path(sys.argv[2])
    model = tf.keras.models.load_model(source, compile=False)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(converter.convert())
    print(f"Zapisano {output} ({output.stat().st_size} bajtów)")


if __name__ == "__main__":
    main()
