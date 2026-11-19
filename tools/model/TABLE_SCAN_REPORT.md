# Skanowanie całego stołu — 25 października 2026

## Implementacja

Nowy przepływ aparatu wykrywa osobne układy 5×4, pozwala poprawić obrysy,
ich orientację i nazwy, a następnie prowadzi przez karty kolejnych graczy.
Zatwierdzenie podglądu wymaga 2–6 poprawnych obrysów. Można dodać brakujący
układ, usunąć błędny, przybliżyć obraz i przeciągać narożniki po powiększeniu.
Linie siatki są rzutowane perspektywicznie tak samo jak pola rozpoznawania.

`MultiBoardLocator` generuje kandydatów z kilku progów i skal lokalnej tekstury.
Dopasowuje obrócone siatki 5×4, korzysta pomocniczo z konturów kart do ustalenia
skali i fazy siatki, poprawia cztery narożniki oraz usuwa nakładające się
propozycje. Względny kontrast ogranicza wybieranie samej jasnej części planszy
przy silnym cieniu. Analiza geometrii ma najwyżej 1600 px na dłuższym boku.
Pusta detekcja pozostaje pusta; nie tworzy propozycji obejmującej całe zdjęcie.

Kierunek układu jest wybierany spośród czterech orientacji na podstawie
geometrii i wyników obecnego klasyfikatora. Użytkownik może go zmienić przed
rozpoznaniem. Fragmenty poszczególnych plansz są dekodowane z oryginału,
z uwzględnieniem wszystkich ośmiu wariantów EXIF. Klasyfikator jest wspólny
wewnątrz każdej operacji obejmującej wiele plansz; przetwarzanie jest kolejne
i można je anulować. Model TFLite nie został zmieniony.

Wspólne zdjęcie robocze jest utrzymywane do zakończenia lub przerwania kolejki.
Zatwierdzenie pierwszego gracza go nie usuwa. Dodatkowe ujęcia pojedynczego
układu zachowują ręczne poprawki i limit czterech zdjęć. Każda zatwierdzona
plansza trafia do istniejącego zapisu partii; przerwanie i odtworzenie stanu
zachowują zatwierdzone plansze oraz pozostałych graczy oczekujących na zdjęcie.

## Archiwum rozwojowe

Wersja 2 zachowuje jeden obraz fizycznego zdjęcia i tablicę `boards[]`.
Każdy układ ma osobne `board_id`, `group_id`, narożniki, status oraz etykiety
i wyniki modelu. Zmiana jednego gracza nie nadpisuje innych układów.
Dodatkowe pojedyncze zdjęcie może nadal mieć format wersji 1 i ten sam
`group_id`, co odpowiedni układ na zdjęciu stołu. Zapis, aktualizacja etykiet,
eksport i usuwanie są synchronizowane. Nazwy graczy nie są archiwizowane.

`archive_records.py` normalizuje oba formaty do obserwacji plansz.
`audit_app_captures.py` i pomocniczy `BoardLocatorArchiveProbe` obsługują
identyfikację parą `capture_id`/`board_id`. Audyt pomija niezatwierdzone układy
i odróżnia liczbę fizycznych zdjęć od liczby obserwacji plansz.

## Weryfikacja lokalna

Końcowe polecenie `gradlew.bat testDebugUnitTest lintDebug assembleDebug
connectedDebugAndroidTest` zakończyło się powodzeniem na emulatorach API 33
i API 36. Wyniki XML:

| Zestaw | Przeszły | Pominięte | Błędy |
| --- | ---: | ---: | ---: |
| Testy jednostkowe Android/JVM | 29 | 0 | 0 |
| Testy instrumentacyjne API 33 | 31 | 1 | 0 |
| Testy instrumentacyjne API 36 | 30 | 2 | 0 |
| Zgodność archiwów — Python | 3 | 0 | 0 |

Pominięte przypadki to istniejące opcjonalne próby wymagające osobnego
prywatnego zestawu pięciu plansz, a na API 36 także archiwum lokalizatora.
Nowe testy zdjęcia trzech graczy zostały wykonane na obu urządzeniach.

Sprawdzono:

- syntetyczne zdjęcia 2–6 układów, obroty 0°, 20°, −25°, 90°, 180° i 270°;
- perspektywę, odblask, cień, pusty stół oraz rozpraszające krawędzie i obiekty;
- trzy osobne obrysy na istniejącym prywatnym zdjęciu; dodatkowy warunek
  odrzuca obrys obejmujący jedynie jasny fragment dolnej planszy;
- odczyt trzech zestawów po 20 wyników modelu ze wspólnego oryginału;
- edycję nazw, obrotu i liczby układów, ciągłe przeciąganie oraz powiększenie
  do 200% i późniejsze przeciąganie w poprawnych współrzędnych zdjęcia;
- dodatkowe zdjęcia, ręczne poprawki, limit czterech ujęć, częściowy zapis,
  odtworzenie stanu i anulowanie bez ponownego otwierania starego zdjęcia;
- dekodowanie regionów dla ośmiu orientacji EXIF i czterech kierunków siatki;
- niezależność etykiet plansz i pojedynczy obraz w eksporcie wersji 2.

Pełny audyt starszego archiwum odtworzył 12 zdjęć, 9 układów i 240 obserwacji
kart, z niezmienionym porównaniem 239/240 wyników modelu do zapisanych etykiet.
Próba formatu wersji 2 na lokalnym montażu dwóch zdjęć poprawnie dała jedno
zdjęcie i dwie osobne obserwacje plansz. Montaż jest wyłącznie sprawdzeniem
formatu, nie niezależnym testem skuteczności detekcji.

Prywatne zdjęcie można dołączyć przed testami poleceniem:

```powershell
adb -s emulator-5554 push Zdjecia/pogladowe/20261007_184333.jpg /data/local/tmp/ekosystem-table.jpg
```

Należy powtórzyć operację dla każdego testowanego emulatora. Zdjęcia,
montaże, podglądy i wyniki szczegółowe pozostają w ignorowanych katalogach.
Test zgodności archiwów jest też częścią CI.

## Ograniczenia oceny

Jedyne dostępne zdjęcie wielu rzeczywistych plansz posłużyło do strojenia
detektora. Nie jest niezależnym pomiarem jakości. Test rozpoznawania na nim
sprawdza poprawne wykonanie całego odczytu, nie trafność wszystkich 60 kart.
Obrysy i karty nadal wymagają sprawdzenia przez użytkownika.

Przed oceną skuteczności i szybkości na telefonie potrzebne są niezależne
zdjęcia pełnych stołów dla 2, 4 i 6 graczy, z różnymi tłami i oświetleniem.
Nie potwierdzono nowych procentowych progów trafności ani działania na
fizycznym urządzeniu.
