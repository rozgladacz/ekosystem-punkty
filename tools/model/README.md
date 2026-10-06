# Przygotowanie klasyfikatora kart

Zdjęcia robocze umieść w `training-data/`; cały katalog jest ignorowany przez
Git. Zalecane minimum to trzy osobne zdjęcia każdego typu karty i 30 pełnych
układów wykonanych przy różnych tłach, kątach i oświetleniu.

## 1. Manifest układów

Skopiuj `manifest.example.json` do `training-data/manifest.json`. Każdy wpis
opisuje zdjęcie, cztery narożniki w kolejności lewy-górny, prawy-górny,
prawy-dolny, lewy-dolny, 20 etykiet w kolejności wierszami oraz część zbioru.
Zdjęcia z tej samej sesji powinny zawsze należeć do tej samej części, aby dane
testowe nie przeciekały do treningu.

## 2. Wycięcie pól

```bash
python tools/model/prepare_dataset.py training-data/manifest.json training-data/dataset
```

## 3. Trening i eksport

W środowisku z Pythonem 3.11 zainstaluj `tensorflow`, `opencv-python` i `numpy`,
a następnie uruchom:

```bash
python tools/model/train_model.py training-data/dataset app/src/main/assets/card_classifier.tflite
```

Skrypt raportuje trafność zbioru testowego i eksportuje model UINT8 224×224.
Model należy dodać dopiero po osiągnięciu co najmniej 98% trafności testowej i
sprawdzeniu prawa do jego dystrybucji.

