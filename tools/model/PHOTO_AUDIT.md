# Przegląd dostarczonych zdjęć — 10 października 2026

Przejrzano wizualnie 91 plików JPG z `Zdjecia/`, w tym 10 nowych ujęć z
`Zdjecia/Nowe`. Oryginały pozostają w tym
katalogu, są ignorowane przez Git i nie zostały zmienione. `Zdjecia/index.csv`
zapisuje ścieżkę, etykietę, sesję, status i sumę SHA-256 każdego pliku.

| Rodzaj | Wszystkie | Kandydaci | Odrzucone / poglądowe |
| --- | ---: | ---: | ---: |
| Pojedyncze karty | 52 | 48 | 4 odrzucone |
| Układy 5×4 | 38 | 35 | 3 odrzucone |
| Zdjęcie trzech plansz | 1 | 0 | 1 poglądowe |

Liczba kandydatów pojedynczych kart według typu: zając 4, lis 4, ważka 4,
pstrąg 4, niedźwiedź 4, pszczoła 4, **bielik 6, jeleń 6**, wilk 4,
potok 4, łąka 4. Część starszych ujęć ma mocne odblaski, rozmycie,
ucięte brzegi lub słabe oświetlenie; status `candidate` wymaga selekcji przed
użyciem. Wcześniejsza seria jedenastu zdjęć pojedynczych kart pokazuje karty do góry
nogami; oryginały pozostawiono bez obrotu.

Nowa seria ma pięć zdjęć bielika i pięć jelenia. Po kontroli pojedynczych
ujęć: **2 czyste** (nr 84 i 85), **6 wstrzymanych** z powodu odblasku lub
ciemności (82, 83, 87–90) i **2 niezdatne** (86 — bardzo ciemne i nieostre;
91 — prześwietlona głowa bielika). Decyzje zapisano w
`Zdjecia/single_new_review.csv`. Status `candidate` w `index.csv` jest
statusem inwentaryzacji, nie decyzją o dopuszczeniu do treningu. Oryginałów
nie usuwano. Nawet dwie czyste klatki wymagają jeszcze kadrowania i
wydzielenia według sesji przed treningiem.

## Sprawdzenie i oznaczenie 38 plansz

Z 38 zdjęć plansz trzy były już odrzucone. Pozostałe 35 sprawdzono pole po
polu. Dla 34 udało się odczytać wszystkie 20 kart; na zdjęciu nr 35 karta w
rzędzie 2, kolumnie 4 jest całkowicie zasłonięta odblaskiem, więc pozostaje
`null`. Dziewięć zdjęć wymagało korekty orientacji: dwa o 90° i siedem o 180°.
Oryginały nie zostały obrócone. Cztery narożniki każdej planszy zapisano jako
**przybliżone**, a ich poprawność sprawdzono na wyprostowanych podglądach.

| Wynik przeglądu | Plansze | Zastosowanie |
| --- | ---: | --- |
| Oznaczone i wstępnie wycinalne | 25 | Manifest prototypowy |
| Wszystkie karty odczytane, geometria nierówna / nakładająca się | 8 | Kontrola ręczna, poza treningiem |
| Górny rząd częściowo ucięty | 1 | Poza treningiem |
| Jedna karta nieodczytywalna z powodu odblasku | 1 | Poza treningiem |
| Wcześniej odrzucone | 3 | Poza treningiem |

Aktualne etykiety, status i narożniki są w
`Zdjecia/board_annotations_review.json`. Dla 25 plansz przygotowano
`Zdjecia/manifest.prototype.json` oraz 500 lokalnych wycinków w
`training-data/dataset-prototype/`: 300 train, 80 validation, 120 test.
Podział jest według sesji; w części testowej występuje wszystkich 11 klas.
To wstępne oznaczenia wykonane na podstawie obrazów, bez niezależnej drugiej
kontroli człowieka.

**Ocena:** liczbowe minimum 30 zdjęć plansz jest spełnione tylko przed
odrzuceniem i kontrolą geometrii. Zdatnych do prototypowego wycinania jest
obecnie 25, zatem **do planowanego minimum brakuje co najmniej 5 czystych
układów**. Zapas 10 nowych układów byłby rozsądniejszy. Fotografuj pełne
prostokąty 5×4 z marginesem wokół, stałymi odstępami, bez nakładania kart i
przy rozproszonym świetle. Obecne adnotacje nie dowodzą jeszcze 98% trafności
kart, 95% detekcji siatki ani 99% precyzji automatycznie akceptowanych
rozpoznań; wymaga to osobnych testów na nowych zdjęciach z telefonu.

## Uzupełnienie — 11 października 2026

Dodano 27 nienaruszonych plików: 11 pojedynczych kart (po jednej każdego
typu) oraz 16 zdjęć plansz. Indeks obejmuje teraz **118 zdjęć**:
63 pojedyncze karty, 54 zdjęcia plansz i 1 zdjęcie poglądowe. Nowe
pojedyncze karty oznaczono w `Zdjecia/single_20261011_review.csv`:
7 kwalifikuje się do kadrowania, 4 wstrzymano przez odblask. To nadal
decyzja wizualna, nie pomiar trafności modelu.

Na wszystkich 16 nowych planszach zapisano wierszowe etykiety 5×4 i
przybliżone narożniki. Po kontroli wyprostowanych podglądów tylko **5**
dołączono do manifestu prototypowego. Pięć ma nierówną geometrię
rozbijającą stałą siatkę, kolejne trzy mają istotne odblaski, jedna ma
całkowicie zasłoniętą kartę, a dwie mają duże odstępy lub perspektywę.
Razem daje to **30 wstępnie wycinalnych, różnych układów** i 600 lokalnych
wycinków (320 train, 100 validation, 180 test). To spełnia minimum
liczebności z planu, lecz nie dowodzi wymaganych progów jakości.

Zdjęcia nr **115 i 116** pokazują ten sam układ bez przekładania kart.
Na pierwszym pole (4,5) jest całkowicie białe od odbicia lampy; na drugim
widać lisa. Para jest opisana w `Zdjecia/multishot_groups.csv`. Nr 115 nie
wchodzi do zbioru treningowego ani testowego jako samodzielne ujęcie;
może posłużyć do osobnej walidacji łączenia zdjęć.

## Uzupełnienie — 15 października 2026

Przeniesiono bez modyfikowania bajtów 22 nowe oryginały z `Zdjecia/Nowe/`
do katalogów sesji w `Zdjecia/uklady/`. Kontrolne sumy SHA-256 są w
`Zdjecia/index.csv`, a manifest operacji w `Zdjecia/audyt/manifest_20261015.csv`.
Indeks obejmuje teraz **140 zdjęć**: 63 pojedyncze, 76 zdjęć plansz i jedno
poglądowe. Nowa partia to **11 różnych układów, po dwa ujęcia**; obie klatki
każdej pary mają niezmieniony układ kart. Pary i obserwacje odblasków zapisano
w `Zdjecia/multishot_groups.csv`.

Wszystkim 22 klatkom przypisano w `Zdjecia/board_labels_review.csv` po 20
etykiet, czyli 220 **różnych pól układu**, a nie 440 niezależnych kart.
Etykiety par są identyczne i obejmują wszystkie 11 rodzajów kart. Odczyt jest
wizualny, bez niezależnego drugiego anotatora. Dziesięć układów można poddać
dalszej kontroli geometrii; para 129–130 ma karty nakładające się i nierówne
kolumny, dlatego jest przypadkiem trudnym, a nie zwykłą próbką siatki 5×4.
W parze 137–138 górne łąki są prześwietlone w obu ujęciach, więc drugi kadr
nie musi usuwać każdego odblasku.

Narożników i podziału na 20 pól tej partii **jeszcze nie zatwierdzono**:
`geometry_status=pending`, `corners=null` w
`Zdjecia/board_annotations_review.json`. Układy pozostają poza istniejącym
manifestem treningowym, walidacyjnym i testowym. Posłużyły następnie do
**rozwojowej oceny i strojenia detektora**, więc nie są już niezależnym zbiorem
do końcowego potwierdzenia jakości całego przepływu. Oryginały nadal nie były
użyte do uczenia klasyfikatora.

Liczebnościowo jest teraz 30 wcześniejszych układów w prototypowym
manifeście oraz 11 nowych par rozwojowych. Wymagane progi 98%/95%/99% nadal
wymagają pomiaru na poprawnie wyciętych polach i pełnym przepływie na
telefonie. Szczególnie ważne jest rozdzielenie według układu: obu ujęć tej
samej planszy nigdy nie wolno dzielić między trening i test.

Wstępna diagnostyka istniejącego algorytmu wykrywania narożników (ta sama
sekwencja: Canny, zamknięcie 15×15, największy wypukły czworokąt, na obrazach
zmniejszonych jak w aplikacji) wykazała czworokąt o powierzchni ponad 20%
kadru tylko na **1 z 22** nowych zdjęć. Dla pozostałych wzór pewności w
aplikacji daje 0. To próba offline, nie test na Androidzie, lecz pokazuje, że
bieżący detektor zewnętrznego prostokąta nie radzi sobie z rozdzielonymi
kartami. Przed pomiarem jakości klasyfikatora trzeba rozwiązać wykrywanie
poszczególnych kart lub ustalanie całej siatki z ich położeń.

Nowe podejście i jego ograniczenia opisuje `DETECTION_REPORT.md`.
