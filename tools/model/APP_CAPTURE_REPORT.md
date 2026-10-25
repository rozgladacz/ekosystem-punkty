# Analiza archiwum aplikacji — 25 października 2026

Nowy materiał to `Zdjecia/Nowe/ekosystem-trening.zip`: 12 fotografii 9
układów, w tym trzy pary ujęć. Wszystkie rekordy mają potwierdzone etykiety,
narożniki oraz pełne wyniki klasyfikatora. Zweryfikowano sumy SHA-256,
wymiary obrazów i zgodność etykiet wewnątrz par. Archiwum i podglądy
pozostają poza Git. Dziesięć wcześniejszych JPG-ów z 17–18 października
było już użyte do strojenia poprzedniej wersji detektora.

## Rozpoznawanie kart

Miary poniżej porównują zapisane wyniki modelu z etykietami zatwierdzonymi
przez użytkownika, dla końcowych narożników z archiwum.

| Miara | Wynik |
| --- | ---: |
| Najlepsza klasa zgodna z etykietą | 239/240 (99,6%) |
| Wyniki ≥0,85 zgodne z etykietą | 199/199 |
| Wyniki <0,85 zgodne z etykietą | 40/41 |
| Etykieta w trzech najlepszych klasach | 240/240 |
| Całe zdjęcie z 20 trafnymi najlepszymi klasami | 11/12 |

To 240 obserwacji **180 różnych pól** w 9 układach; ujęcia par nie są
niezależne. Zatwierdzone etykiety nie zostały sprawdzone przez drugiego
anotatora. Wyniki nie stanowią pomiaru całego automatycznego przepływu ani
potwierdzenia skuteczności na innych tłach i oświetleniu.

Potoki mają 24 słabe wyniki na 42 wystąpienia, wilki 6/17, pstrągi 5/23,
lisy 3/18, ważki 2/16 i łąki 1/37. Jedyny błąd najlepszej klasy to odwrócony
potok (wiersz 2, kolumna 2 w pierwszym ujęciu jednej pary), sklasyfikowany
jako pstrąg z wynikiem 0,502. Potok jest drugą propozycją. Drugie zdjęcie
tego układu daje poprawną najlepszą klasę.

`manually_corrected_indices` obejmuje także wybranie poprawnej propozycji,
którą poprzednia wersja ukryła pod znakiem zapytania. Indeksy są wspólne
dla par. Liczby 28 wpisanych indeksów nie należy interpretować jako 28
błędów modelu.

## Obrót pojedynczych kart

Ponowne wycięcie kart i uruchomienie dołączonego modelu na komputerze
odtworzyło wszystkie 240 najlepszych klas zapisanych przez aplikację.
Wyniki liczbowe nie są identyczne ze względu na ponowne kodowanie zdjęć
i skalowanie. Eksperyment z dodatkowymi odczytami po obrocie o 180° dał:

| Wariant eksperymentu | Trafne klasy | Wyniki ≥0,85 |
| --- | ---: | ---: |
| Ponowny odczyt zwykły | 239/240 | 193 |
| Tylko obrócone wycinki | 231/240 | 93 |
| Średnia wyników obu orientacji | 240/240 | 123 |
| Pewniejszy odczyt, dodatkowy obrót tylko przy <0,85 | 240/240 | 195 |

Samo odwracanie wszystkich zdjęć pogarsza wynik. Warto przetestować
dodatkowy odczyt słabych kart w obu orientacjach na większym zbiorze.
Obecny skrypt treningu losuje obrót do około ±10,8°, więc kolejne uczenie
powinno obejmować również niezależne obroty kart o 180°.
W tym wydaniu model i sposób wykonywania inferencji pozostają bez zmian;
eksperyment nie był niezależną walidacją nowej metody.

## Automatyczne narożniki

Uruchomiono rzeczywisty `BoardLocator` na emulatorze API 33 dla 12 zdjęć
z archiwum. Na 10/12 maksymalna różnica dowolnej współrzędnej od
zatwierdzonego kadru wynosi najwyżej 5,5% odpowiedniego wymiaru zdjęcia.
Na 8 zdjęciach narożniki są identyczne. Dwa wyraźniejsze odchylenia,
6,1% i 6,5% kadru, występują w obu ujęciach tego samego układu.

Na tych dwóch zdjęciach dolna granica obejmuje także tło przy dłoniach
i ubraniu. Propozycja jest prostokątem, podczas gdy zatwierdzony układ
wymaga skośnych boków. Priorytetem następnej poprawki detektora jest
dopasowanie czterech niezależnych boków/perspektywy i sprawdzenie, czy
dodatkowy pas poniżej rzeczywiście należy do czwartego rzędu kart.

To porównanie z końcowym kadrem użytkownika, a nie niezależna adnotacja
geometrii. Archiwum nie zachowuje początkowych narożników, flag ich ręcznej
zmiany, wersji modelu/detektora ani oceny odblasku. Dodanie tych pól do
kolejnego formatu metadanych uprości ocenę zmian i odróżni regresję
algorytmu od różnicy ponownego kodowania zdjęcia.

## Wprowadzona zmiana

Klasyfikator oraz scalanie kilku ujęć przypisują najlepszą dostępną kartę
również poniżej progu pewności. Niska pewność, odblask i sprzeczne ujęcia
nadal oznaczają pole do sprawdzenia. Osobna flaga zachowuje ostrzeżenie
nawet wtedy, gdy po scaleniu sprzecznych zdjęć zwycięska klasa ma wynik
≥0,85. Ręczny wybór usuwa oznaczenie. Jeśli model nie dostarczył wyniku,
pole pozostaje puste.

## Odtworzenie analizy

`audit_app_captures.py ARCHIWUM.zip KATALOG_WYJSCIOWY --model MODEL.tflite`
generuje statystyki, plansze kontrolne i wycinki błędów. `--fixtures`
zapisuje zweryfikowane pliki do prywatnej diagnostyki Androida.
Opcjonalny `BoardLocatorArchiveProbe` czyta je z katalogu emulatora
`/data/local/tmp/ekosystem-locator-archive/{images,metadata}` i zapisuje
`locator-archive-results.json` w pamięci zewnętrznej aplikacji testowej.
Bez prywatnych plików próba jest pomijana. Wynik można podać skryptowi
przez `--locator-results` w celu utworzenia podglądu: zielony kadr jest
zatwierdzony, czerwony pochodzi z ponownego wykrycia.
