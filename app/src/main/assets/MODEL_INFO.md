# Eksperymentalny model kart

`card_classifier.tflite` to prototyp MobileNetV3-Small FLOAT32 wytrenowany
lokalnie na wycinkach kart z 30 plansz. Wejście: RGB 224×224, zakres 0–255.
Wyjście: 11 wyników softmax w kolejności z `card_labels.txt`.

SHA-256 modelu:
`5b4dc74ab7985680c01da8712c74dd7beefd90457fb96ff64cde42aebbf093b6`.

Na odłożonych 9 planszach z tego samego zbioru prototypowego trafność
pojedynczych pól wyniosła 177/180, lecz na nowych zdjęciach może być inna.
Automatyczne przyjęcie wymaga wyniku co najmniej 0,85; użytkownik musi
sprawdzić cały układ. Uzasadnienie wyboru i ograniczenia: `tools/model/TRAINING_REPORT.md`.
Pakiet LiteRT ma bibliotekę natywną dla ARM64; na urządzeniu bez działającego
interpretera aplikacja pozostaje w trybie ręcznego uzupełniania.

Bazę zainicjalizowano wagami ImageNet dostępnymi w Keras MobileNetV3.
Modelu, wag bazowych i materiałów gry nie obejmuje licencja MIT kodu aplikacji.
Źródło implementacji bazowej: https://github.com/keras-team/keras/blob/master/keras/src/applications/mobilenet_v3.py
