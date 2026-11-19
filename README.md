# Ekosystem Punkty

Nieoficjalna, lokalna aplikacja Android do liczenia punktów w podstawowej
polskiej edycji gry „Ekosystem”. Przycisk **Skanuj cały stół** pozwala zrobić
jedno zdjęcie wszystkich układów 5×4. Aplikacja proponuje osobne obrysy,
automatycznie tworzy 2–6 graczy i rozpoznaje karty kolejno dla każdego z nich.
Układy mogą być obrócone względem siebie. Przed rozpoznaniem można przybliżyć
zdjęcie, zmienić nazwy, przesunąć narożniki, obrócić siatkę oraz dodać lub
usunąć obrys. Detekcja jest przybliżona: każdy obrys musi obejmować dokładnie
20 kart, a linie powinny przebiegać między kartami.

Można również wcześniej wpisać graczy i fotografować ich układy osobno.
Aplikacja pozwala poprawić rozpoznane karty i wyświetla końcową tabelę.
Jeśli odblask zasłoni pole, można dodać do czterech zdjęć tego samego układu,
bez przesuwania kart. Rozpoznania są łączone pole po polu; sprzeczne lub
prześwietlone pola wymagają ręcznego potwierdzenia.

Po zatwierdzeniu jednego gracza ze zdjęcia stołu aplikacja otwiera następnego,
bez ponownego uruchamiania aparatu. Wspólne zdjęcie robocze pozostaje do końca
tej kolejki. Wyjście ze skanowania zachowuje zatwierdzone plansze i usuwa
zdjęcia robocze; pozostali gracze oczekują na osobne zdjęcia. Po restarcie
procesu aplikacji przywracany jest ten sam stan zapisanej partii.

## Prywatność

- brak kont, backendu, telemetrii i reklam;
- zdjęcia są analizowane na urządzeniu; domyślnie ujęcia robocze są usuwane po zatwierdzeniu;
- stan obejmuje wyłącznie bieżącą partię;
- aplikacja nie sprawdza aktualizacji w tle ani przy uruchomieniu;
- połączenie z GitHub następuje tylko po naciśnięciu **Sprawdź aktualizacje**.

Uprawnienie aparatu służy wyłącznie do wykonania zdjęcia. Zezwolenie na
instalowanie pakietów jest potrzebne dopiero po ręcznym pobraniu aktualizacji.

W ustawieniach można świadomie włączyć **Tryb rozwoju**. Od tego momentu
aplikacja zachowuje w swojej prywatnej pamięci kopię każdego nowego zdjęcia,
również powtórzonego. Przy zatwierdzeniu planszy dopisuje etykiety 20 kart,
narożniki kadru, pewności rozpoznania i indeksy ręcznych korekt. Zdjęcia
odrzucone pozostają bez etykiet. Ujęcia są pomniejszane do maksymalnie 2400 px
na dłuższym boku, normalizowane do właściwej orientacji i kodowane ponownie
jako JPEG bez EXIF. Metadane nie zawierają nazw graczy, dat, lokalizacji ani
modelu telefonu; losowy `group_id` pozwala połączyć zdjęcia tego samego układu.
Sam obraz może jednak zawierać prywatne elementy tła — sprawdź go przed
udostępnieniem.

Użytkownik może wyeksportować zapisane zdjęcia i pliki JSON do ZIP-u przez
systemowy wybór miejsca zapisu, a potem przenieść ZIP ręcznie na komputer.
Nie są potrzebne uprawnienia do galerii ani plików i nic nie jest wysyłane
automatycznie. Wyłączenie trybu nie kasuje wcześniejszych próbek; w ustawieniach
jest osobny przycisk do ich usunięcia. Usunięcie aplikacji usuwa też prywatny
materiał, jeśli nie został wcześniej wyeksportowany.

W archiwum każdemu `images/<capture_id>.jpg` odpowiada
`metadata/<capture_id>.json`. W wersji 1 `group_id` łączy ujęcia jednej planszy,
`shot_index` określa kolejność, a `status: labeled` oznacza planszę
zatwierdzoną. Tablica `labels` ma 20 pól w kolejności wierszowej (4 wiersze
po 5 kolumn); `corners` zawiera kolejno lewy górny, prawy górny, prawy dolny
i lewy dolny narożnik jako współrzędne znormalizowane. Metadane obejmują też
rozmiar obrazu, SHA-256, wyniki modelu dla wszystkich 11 typów kart,
pewności klasyfikatora i indeksy pól poprawionych ręcznie. Archiwum można
przenieść np. kablem USB; aplikacja nigdy nie przesyła
go samodzielnie.

Zdjęcie całego stołu jest zapisywane raz, z `schema_version: 2`. Jego tablica
`boards` zawiera osobne rekordy układów: `board_id`, `group_id`, `shot_index`,
`corners`, `status` oraz — po zatwierdzeniu — `labels`, `recognition_confidence`,
`class_probabilities` i `manually_corrected_indices`. Narożniki są podane we
współrzędnych całego zdjęcia po korekcie EXIF, w logicznej kolejności siatki,
również dla układów obróconych. `group_id` każdego układu łączy go z dodatkowymi
zdjęciami pojedynczego gracza. Stan główny `partial` oznacza zatwierdzenie
części układów, a `labeled` — wszystkich. Nazwy graczy nie trafiają do plików.
Dotychczasowe rekordy wersji 1 pozostają obsługiwane przez aplikację i audyt.

## Budowanie

Wymagane są JDK 21, Android SDK 36 i Android Build Tools 36+.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

APK debug powstaje w `app/build/outputs/apk/debug/`.

## Model kart

Repozytorium zawiera eksperymentalny model FLOAT32 MobileNetV3-Small, ale nie
zawiera zdjęć źródłowych. Każde pole z wynikiem modelu otrzymuje najlepszą
propozycję. Wyniki poniżej 0,85 oraz pola z odblaskiem lub sprzecznymi
odczytami są zaznaczane na czerwono do sprawdzenia. Użytkownik powinien
sprawdzić wszystkie 20 pól przed zatwierdzeniem. Wybór ręczny pokazuje typy
w kolejności wyników modelu. Ranking jest zachowywany przy korekcie
bieżącej partii. Proces przygotowania danych opisuje
[`tools/model/README.md`](tools/model/README.md), a pochodzenie modelu
[`MODEL_INFO.md`](app/src/main/assets/MODEL_INFO.md). Wynik prototypowego treningu opisuje
[`tools/model/TRAINING_REPORT.md`](tools/model/TRAINING_REPORT.md).

## Wydania

Push do `main` uruchamia testy, podpisuje APK i tworzy wydanie
`v0.1.<run_number>` wraz z `update.json`. Repozytorium musi mieć sekrety:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Klucz podpisujący należy zachować poza repozytorium. Utrata klucza uniemożliwi
aktualizowanie już zainstalowanej aplikacji.

## Prawa do gry

Projekt nie jest powiązany z autorami ani wydawcą gry. Nazwa gry, ilustracje i
pozostałe materiały należą do ich właścicieli i nie są objęte licencją MIT tego
repozytorium.

