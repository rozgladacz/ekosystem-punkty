# Ekosystem Punkty

Nieoficjalna, lokalna aplikacja Android do liczenia punktów w podstawowej
polskiej edycji gry „Ekosystem”. Aplikacja prowadzi przez sfotografowanie układu
5×4 każdego gracza, pozwala poprawić rozpoznane karty i wyświetla końcową tabelę.
Jeśli odblask zasłoni pole, można dodać do czterech zdjęć tego samego układu,
bez przesuwania kart. Rozpoznania są łączone pole po polu; sprzeczne lub
prześwietlone pola wymagają ręcznego potwierdzenia.

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
`metadata/<capture_id>.json`. `group_id` łączy ujęcia jednej planszy,
`shot_index` określa kolejność, a `status: labeled` oznacza planszę
zatwierdzoną. Tablica `labels` ma 20 pól w kolejności wierszowej (4 wiersze
po 5 kolumn); `corners` zawiera kolejno lewy górny, prawy górny, prawy dolny
i lewy dolny narożnik jako współrzędne znormalizowane. Metadane obejmują też
rozmiar obrazu, SHA-256, pewności klasyfikatora i indeksy pól poprawionych
ręcznie. Archiwum można przenieść np. kablem USB; aplikacja nigdy nie przesyła
go samodzielnie.

## Budowanie

Wymagane są JDK 21, Android SDK 36 i Android Build Tools 36+.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

APK debug powstaje w `app/build/outputs/apk/debug/`.

## Model kart

Repozytorium nie zawiera zdjęć ani gotowego modelu, dopóki nie zostanie
przygotowany z materiałów właściciela gry. Bez pliku
`app/src/main/assets/card_classifier.tflite` aplikacja celowo oznacza pola jako
nierozpoznane i umożliwia ich ręczne uzupełnienie. Proces przygotowania danych i
modelu opisuje [`tools/model/README.md`](tools/model/README.md).
Wynik pierwszego, niewdrożonego treningu opisuje
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

