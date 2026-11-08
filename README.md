# Charge Monitor

Zeigt live, mit wie viel Watt das Handy gerade lädt, per Kabel, USB oder Qi.
Android-App im dunkelgrünen Design, entstanden für das Samsung Galaxy S25 Ultra.

**Aktuelle Version:** 1.5

<!-- Screenshot: Datei als docs/screenshot.png hochladen, dann die nächste Zeile einkommentieren -->
<!-- ![Charge Monitor](docs/screenshot.png) -->

## Funktionen

- Ladeleistung in Watt als animierter Ring, dazu Spannung, Strom, Ladestand und Akkutemperatur
- Erkennt die Ladequelle: Netzteil, USB oder Qi (kabellos)
- Verlauf der letzten 60 Sekunden mit Durchschnitt und Spitzenwert
- Ring-Skala fest (getrennt für Kabel und Qi) oder automatisch nach Spitzenwert, mit Skalenbeschriftung
- Standby-Anzeige: dunkel, gedimmt, mit digitaler oder analoger Uhr, einstellbarer Ringgröße und Einbrennschutz für OLED-Displays
- Nachtmodus von 22 bis 6 Uhr mit eigener, sehr niedriger Helligkeit
- Bildschirmschoner, der beim Laden automatisch startet
- Optional: App öffnet sich beim Einstecken des Ladekabels direkt im Standby
- Automatik jederzeit pausierbar: per Knopf in der Benachrichtigung oder durch langes Tippen auf das App-Symbol
- „Display aus“: Standby für den laufenden Ladevorgang abschalten, auf Wunsch sofort wie mit der Seitentaste
- Temperatur-Hinweis, einstellbare Skala für Kabel und Qi, Hilfe und Disclaimer in der App

Gemessen wird die Leistung, die im Akku ankommt (Akkuspannung × Ladestrom). Sie ist kleiner als die Leistung am Netzteil. Die Werte stammen aus der Akku-Schnittstelle von Android und sind nicht kalibriert.

## Installation

Die fertige APK gibt es unter [Releases](../../releases). Die App steht nicht im Play Store. Beim ersten Installieren fragt Android, ob Installationen aus dieser Quelle erlaubt werden sollen.

Voraussetzung: Android 10 oder neuer.

Wer „Display sofort ausschalten“ nutzt, muss diese Option vor dem Deinstallieren in den Einstellungen der App wieder abschalten. Sonst lässt Android die Deinstallation nicht zu.

## Berechtigungen

Die App hat keine Internet-Berechtigung und überträgt keine Daten. Alle weiteren Berechtigungen gehören zu optionalen Funktionen:

| Berechtigung | Wofür |
|---|---|
| Über anderen Apps einblenden | Start beim Einstecken des Ladekabels |
| Vordergrunddienst, Benachrichtigungen | Hintergrunddienst „Charge Monitor bereit“ für den Start beim Einstecken |
| Nach dem Neustart starten | Hintergrunddienst nach dem Neustart wieder aktivieren |
| Akku-Optimierung ignorieren | Damit der Hintergrunddienst nicht beendet wird |
| Geräteadministrator (nur „Bildschirm sperren“) | „Display sofort ausschalten“ |

## Selbst bauen

Kotlin mit Jetpack Compose, Build mit Gradle. Voraussetzung sind JDK 17 oder neuer und das Android SDK (Plattform 35).

```bash
echo "sdk.dir=/pfad/zum/android-sdk" > local.properties
./gradlew assembleRelease      # ergibt app/build/outputs/apk/release/
```

Der Signierschlüssel ist nicht im Repo. Für eine signierte APK eine Datei `keystore.properties` anlegen:

```properties
storeFile=/pfad/zu/eigenem.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Ohne diese Datei entsteht eine unsignierte APK. Mit einem eigenen Schlüssel gebaute APKs lassen sich nicht über die offizielle Version installieren.

## Hinweis

Privates Projekt, kostenlos und ohne Gewähr. Kein Support.

Gebaut von Justus Jonas & MCHLNWK · [cappy0815.github.io](https://cappy0815.github.io)
