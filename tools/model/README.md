# Przygotowanie klasyfikatora kart

Zdjęcia robocze umieść w `training-data/` lub `Zdjecia/`; oba katalogi są
ignorowane przez Git. Minimum z planu to trzy osobne zdjęcia każdego typu
karty i 30 pełnych układów wykonanych przy różnych tłach, kątach i oświetleniu.
Wynik przeglądu obecnie dostarczonych zdjęć opisuje `PHOTO_AUDIT.md`.

`Zdjecia/index.csv` zawiera typ, status, sesję, uwagę i SHA-256 każdego ze 140
oryginałów. Zdjęcia są w `pojedyncze/`, `uklady/`, `pogladowe/` i `odrzucone/`.
`candidate` oznacza ujęcie do dalszego sprawdzenia, nie gwarancję jakości
treningowej. Dla dziesięciu nowych pojedynczych kart osobną decyzję jakości
zapisano w `Zdjecia/single_new_review.csv`: 2 kwalifikują się do kadrowania,
6 wstrzymano, 2 wyłączono.
Serię z 11 października opisuje `Zdjecia/single_20261011_review.csv`:
7 ujęć kwalifikuje się do kadrowania, 4 wstrzymano z powodu odblasków.
Oryginałów nie obracano ani nie kompresowano;
uwagę o obrocie zapisano w adnotacjach. Odrzuconych ujęć nie należy używać do
treningu ani testu.

## 1. Manifest układów

Pełny przegląd znajduje się w `Zdjecia/board_annotations_review.json`.
`board_labels_review.csv` zawiera 20 typów w kolejności wierszami, a
`board_geometry_review.csv` — orientację i przybliżone narożniki. Dwie
fotografie mają po jednej nieodczytywalnej karcie (`null` w JSON), przy czym
drugie ujęcie planszy nr 115 (zdjęcie nr 116) pokazuje zasłoniętego lisa.
Identyfikator tej pary jest w `Zdjecia/multishot_groups.csv`. Piętnaście
fotografii wymaga dodatkowej kontroli geometrii. Nie używaj całego pliku
przeglądu jako manifestu treningu.

`Zdjecia/manifest.prototype.json` wybiera 30 układów po wizualnej kontroli
wyprostowania. To **prototyp**, nie zbiór potwierdzający progi jakości.
Każdy wpis
opisuje zdjęcie, cztery narożniki w kolejności lewy-górny, prawy-górny,
prawy-dolny, lewy-dolny, 20 etykiet w kolejności wierszami oraz część zbioru.
`rotation_degrees` oznacza obrót obrazu po zastosowaniu EXIF, w stopniach
przeciwnie do ruchu wskazówek zegara. Podział jest według sesji: 16 plansz do
treningu, 5 do walidacji, 9 do testu. Nowych zdjęć pojedynczych kart nie
włączono automatycznie do klasyfikatora — także czystych, ponieważ nie
przygotowano jeszcze zatwierdzonych kadrów.

Pojedyncze karty mają etykiety katalogów i indeksu, lecz wymagają jeszcze
kadrowania i selekcji jakości. Nie należy oceniać modelu na niemal identycznych
zdjęciach tej samej karty i z tej samej sesji, na których był trenowany.

Nowe 11 układów sfotografowanych dwukrotnie 12 i 15 października ma etykiety
wszystkich 20 pól, ale geometrię `pending`. Oba ujęcia muszą pozostać w jednej
grupie (`Zdjecia/multishot_groups.csv`). Nie są częścią powyższego manifestu
ani wycinków treningowych; posłużyły do rozwojowego sprawdzenia detektora,
więc końcowy test całego przepływu wymaga kolejnych, nowych układów.
Szczegóły opisują `PHOTO_AUDIT.md` i `DETECTION_REPORT.md`.

## 2. Wycięcie pól

```bash
python tools/model/prepare_dataset.py Zdjecia/manifest.prototype.json training-data/dataset-prototype
```

## 3. Trening i eksport

W środowisku z Pythonem 3.13 i TensorFlow 2.20.0 uruchom:

```bash
python tools/model/train_model.py training-data/dataset-prototype training-data/model-prototype/card_classifier.tflite
python tools/model/evaluate_tflite.py training-data/dataset-prototype training-data/model-prototype/card_classifier.tflite
```

Pierwszy skrypt zapisuje model Keras, metryki i model UINT8 224×224 wyłącznie
poza aplikacją. Drugi mierzy rzeczywisty wynik wyeksportowanego TFLite, w tym
precyzję automatycznych decyzji i liczbę całych plansz bez błędu. Do porównania
bez kwantyzacji służy `export_float_tflite.py`; do kalibracji INT8 wszystkimi
próbkami — `export_int8_tflite.py`. Wyniki pierwszego eksperymentu opisuje
`TRAINING_REPORT.md`.

Nie kopiuj modelu do `app/src/main/assets/` na podstawie samych metryk Keras.
Eksport INT8 może mieć istotnie gorszą trafność i wymaga osobnej weryfikacji.
Przed wydaniem potrzebna jest także kontrola całego przepływu na telefonie,
walidacja wykrywania siatki oraz sprawdzenie prawa do dystrybucji wag.

