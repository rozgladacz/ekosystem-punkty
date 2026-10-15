# Pierwszy trening klasyfikatora — 11 października 2026

Trening przeprowadzono lokalnie na CPU (TensorFlow 2.20.0, Python 3.13), bez
wysyłania zdjęć. Dane pochodzą z `Zdjecia/manifest.prototype.json`: 30 plansz
podzielonych według sesji na 16 treningowych, 5 walidacyjnych i 9 testowych.
Daje to odpowiednio 320, 100 i 180 wycinków kart. Model to MobileNetV3-Small
z wagami ImageNet i zamrożoną bazą; warstwę klasyfikacyjną uczono przez 20 epok.

| Wariant | Karty testowe | Automatycznie zaakceptowane, próg 0,85 | Całe plansze 20/20 |
| --- | ---: | ---: | ---: |
| Keras FLOAT32 | 177/180 (98,3%) | 164/164 poprawnych | — |
| TFLite FLOAT32 | 177/180 (98,3%) | 164/164 poprawnych | 7/9 |
| TFLite INT8, kalibracja 200 próbkami | 155/180 (86,1%) | 50/50 poprawnych | 1/9 |
| TFLite INT8, kalibracja 320 próbkami | 116/180 (64,4%) | 30/32 poprawnych | 0/9 |

Wersje INT8 ponadto nie inicjalizują się na standardowym interpreterze z
delegatem XNNPACK na tym komputerze; wyniki podano z interpretera bez delegata.
Kwantyzacja nie jest więc obecnie gotowa do użycia w aplikacji. Na prośbę
użytkownika wariant FLOAT32 dołączono do APK jako **eksperymentalny**. Wagi
źródłowe i szczegółowe metryki pozostają w ignorowanym przez Git
`training-data/model-prototype/`.

Te liczby są **wynikiem prototypu**, nie potwierdzeniem wymaganych progów na
nowych zdjęciach. Próbki z jednej planszy są skorelowane, a test obejmuje tylko
9 plansz i 180 pól. Nie badano jeszcze pełnego wykrywania siatki z surowych
zdjęć, oceny odblasków po scaleniu ujęć ani działania na fizycznym telefonie.
Ponowne dobieranie architektury według tego samego testu pozbawiłoby go
niezależności — następna walidacja wymaga nowych, odłożonych zdjęć.

Najbliższy krok techniczny: ocenić cały przepływ na nowych planszach i
fizycznym telefonie oraz znaleźć przyczynę utraty jakości przy eksporcie INT8.
