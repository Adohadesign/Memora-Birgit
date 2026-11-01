# Memora Solo v0.2.2 – Überschriftenpflicht

## Änderungen
- Eigenes Feld `entries.title` für jeden Tagebucheintrag, mit nichtleerem Standardtitel für Altbestände.
- Raum-Migration Room 2 → 3 ohne Löschen der lokalen Daten.
- Titel erscheinen in der Tagesübersicht; Antippen öffnet den vorhandenen Eintrag mit Text und Medien.
- Neue Text- und Medieneinträge fragen nach einer Überschrift, mit vorgeschlagenem Standardtitel.
- Titel lässt sich im Eintrag über das Titelsymbol ändern.
- Briefstudio-Import übernimmt `title` als eigenständiges Feld und `body` als Textinhalt ohne Duplizierung.

## Einschränkungen
- Aktueller Build sowie Praxis-/Regressionstests auf dem Handy sind nicht bestätigt.
- Die vollständige Übertragungs-Versionierung (Konzept v1.4 Variante A) wird durch dieses Titelupdate **nicht** umgesetzt.
- Bei paralleler Medienmehrfachauswahl kann ein neutraler Standardtitel entstehen; jeder Eintrag bleibt separat umbenennbar.

## Tests vor produktiver Installation
1. Vorher lokale ZIP-Sicherung im bestehenden Memora Solo erstellen.
2. Upgrade v0.2.1 → v0.2.2 mit bestehenden Einträgen; keine Deinstallation.
3. Texte, Foto, Video, Sprache erstellen und Titel prüfen.
4. Titel in Kalender/Tagesliste anklicken, Eintrag öffnen und bearbeiten.
5. Eintrag aus Briefstudio importieren; Datum, Uhrzeit, Titel und Body prüfen.
6. Medien, Muster/PIN, Datenbankmigration, Löschen, Backup testen.

## Rollback
GitHub-Vorgängerstand bleibt im Commitverlauf. Die Room-Migration ist vorwärtsgerichtet; ein APK-Downgrade ohne passende Datenbank-Wiederherstellung ist **nicht** sicher.