# Testprotokoll v0.2.0

Durchgeführt:
- Vorhandene v0.1.3-Quellstruktur und Netzwerkberechtigung geprüft.
- Statische Prüfung: alte `fetch(email,password)`-Aufrufe entfernt, Versionsnummer aktualisiert.
- Archiv auf Lesbarkeit und Pfade geprüft.

NICHT durchgeführt:
- Android-Gradle-Build: Android SDK und Gradle-Abhängigkeiten in dieser Umgebung nicht verfügbar.
- Kotlin-Compile, instrumentierte UI-Tests, echter Supabase-Login, Netzwerk- und Sicherheits-Penetrationstests.
- Echtes Speichern/Importieren, Entsperren/erneutes Abrufen, Rotieren der Refresh-Tokens und APK-Installation auf Handy.

Abnahmetest nach Build: (1) einmal verbinden, (2) Text in Briefstudio an altem Datum und Uhrzeit bereitstellen, (3) App entsperren, (4) Tagebuchdatum prüfen, (5) erneut öffnen und keine Duplikate sehen, (6) offline öffnen und bestehenden Eintrag weiterhin sehen.
