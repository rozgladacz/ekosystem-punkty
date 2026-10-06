#!/usr/bin/env python3
"""Wytrenuj MobileNetV3-Small i wyeksportuj kwantyzowany model TFLite."""

from __future__ import annotations

import sys
from pathlib import Path

import tensorflow as tf

CLASSES = [
    "RABBIT", "FOX", "DRAGONFLY", "TROUT", "BEAR", "BEE",
    "EAGLE", "DEER", "WOLF", "STREAM", "MEADOW",
]
SIZE = (224, 224)
BATCH = 32


def dataset(root: Path, split: str, shuffle: bool) -> tf.data.Dataset:
    return tf.keras.utils.image_dataset_from_directory(
        root / split,
        labels="inferred",
        label_mode="categorical",
        class_names=CLASSES,
        image_size=SIZE,
        batch_size=BATCH,
        shuffle=shuffle,
        seed=2026,
    ).prefetch(tf.data.AUTOTUNE)


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Użycie: train_model.py KATALOG_DANYCH MODEL.tflite")
    root, output = Path(sys.argv[1]), Path(sys.argv[2])
    train = dataset(root, "train", True)
    validation = dataset(root, "validation", False)
    test = dataset(root, "test", False)

    augmentation = tf.keras.Sequential([
        tf.keras.layers.RandomBrightness(0.2),
        tf.keras.layers.RandomContrast(0.2),
        tf.keras.layers.RandomRotation(0.03),
        tf.keras.layers.RandomZoom(0.12),
        tf.keras.layers.RandomTranslation(0.06, 0.06),
    ])
    base = tf.keras.applications.MobileNetV3Small(
        input_shape=(*SIZE, 3),
        include_top=False,
        weights="imagenet",
        pooling="avg",
        include_preprocessing=False,
    )
    base.trainable = False
    inputs = tf.keras.Input(shape=(*SIZE, 3))
    x = augmentation(inputs)
    # Zachowujemy kontrakt wejścia RGB 0..255. Warstwa poniżej wykonuje
    # normalizację wymaganą przez MobileNetV3, a kwantyzacja UINT8 otrzyma
    # dokładnie ten sam zakres co kod aplikacji.
    x = tf.keras.layers.Rescaling(1.0 / 127.5, offset=-1.0)(x)
    x = base(x, training=False)
    x = tf.keras.layers.Dropout(0.25)(x)
    outputs = tf.keras.layers.Dense(len(CLASSES), activation="softmax")(x)
    model = tf.keras.Model(inputs, outputs)
    model.compile(optimizer="adam", loss="categorical_crossentropy", metrics=["accuracy"])
    model.fit(train, validation_data=validation, epochs=20, callbacks=[
        tf.keras.callbacks.EarlyStopping(patience=4, restore_best_weights=True),
    ])
    _, accuracy = model.evaluate(test)
    print(f"Test accuracy: {accuracy:.4f}")
    if accuracy < 0.98:
        raise SystemExit("Model nie osiągnął wymaganego progu 98%")

    representative = train.unbatch().batch(1).take(200)

    def representative_dataset():
        for images, _ in representative:
            yield [tf.cast(images, tf.float32)]

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = representative_dataset
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.uint8
    converter.inference_output_type = tf.uint8
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(converter.convert())


if __name__ == "__main__":
    main()

