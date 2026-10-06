# Ekosystem Punkty

Nieoficjalna, lokalna aplikacja Android do liczenia punktów w podstawowej
polskiej edycji gry „Ekosystem”. Aplikacja prowadzi przez sfotografowanie układu
5×4 każdego gracza, pozwala poprawić rozpoznane karty i wyświetla końcową tabelę.

## Prywatność

- brak kont, backendu, telemetrii i reklam;
- zdjęcia są analizowane na urządzeniu i usuwane po zatwierdzeniu;
- stan obejmuje wyłącznie bieżącą partię;
- aplikacja nie sprawdza aktualizacji w tle ani przy uruchomieniu;
- połączenie z GitHub następuje tylko po naciśnięciu **Sprawdź aktualizacje**.

Uprawnienie aparatu służy wyłącznie do wykonania zdjęcia. Zezwolenie na
instalowanie pakietów jest potrzebne dopiero po ręcznym pobraniu aktualizacji.

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

