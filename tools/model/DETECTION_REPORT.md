# Wykrywanie planszy — próba rozwojowa, 15 października 2026

Poprzedni detektor w `BoardRecognizer` wybierał największy wypukły
czworokąt. Ponieważ 20 kart zwykle nie styka się ze sobą, w próbie offline
tylko 1 z 22 nowych zdjęć dało czworokąt o powierzchni ponad 20% kadru.

Nowy `BoardLocator` nie wymaga wspólnej ramki. Sumuje krawędzie wzdłuż osi
obrazu, usuwa skrajny szum kwantylami i wyznacza szerokość planszy osobno w
górnym i dolnym pasie. Tam, gdzie widać co najmniej 10 obrysów pojedynczych
kart, używa ich do odrzucenia fałszywej krawędzi tła. Analizę wykonuje na
obrazie maksymalnie 1200×1600, niezależnie od rozdzielczości aparatu.
Wynik pozostaje propozycją: ekran kadrowania pokazuje 5×4 linii, umożliwia
przesunięcie narożników i ostrzega o przybliżonym wykryciu.

`probe_board_locator.py` jest komputerowym odpowiednikiem tego algorytmu.
Wygenerował podglądy i manifest próbny w ignorowanym przez Git
`training-data/locator-probe/`. Po wycięciu pól zgodnie z propozycjami
oceniono niezmieniony prototypowy model TFLite FLOAT32 na wszystkich 22
zdjęciach:

| Miara rozwojowa | Wynik |
| --- | ---: |
| Zdjęcia z propozycją obejmującą układ w podglądzie | 22/22 |
| Trafne etykiety wyciętych pól | 414/440 (94,1%) |
| Trafne całe zdjęcia 20/20 | 11/22 |
| Trafne rozpoznania z pewnością ≥0,85 | 358/360 (99,4%) |

Para zdjęć tego samego układu nie tworzy niezależnych próbek, a 11 układów
użyto do strojenia detektora. Wyniki nie są niezależnym potwierdzeniem progów
98% trafności kart, 95% detekcji siatki ani 99% precyzji automatycznie
akceptowanych rozpoznań. Szczególnie źle wypadło zdjęcie 131 (12/20), gdzie
silne przesunięcie kolumn między rzędami nie daje się wystarczająco dobrze
opisać samą propozycją czterech narożników. Para 129–130 narusza założenie
o nienakładających się kartach. Nadal potrzebne są dokładniejsze wykrywanie
pojedynczych pól, test na nowych układach i sprawdzenie na fizycznym telefonie.

Kompilacja debug, testy jednostkowe i dwa testy detektora na emulatorze
Android 13 przeszły. Test emulatora użył syntetycznej siatki i pustego obrazu;
nie zastępuje testu pełnych zdjęć na Androidzie. Model FLOAT32 użyty w próbie
pozostaje poza aplikacją, a model INT8 nadal nie nadaje się do wdrożenia.
