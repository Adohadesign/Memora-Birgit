# MEMORA SOLO – Android-Projekt v0.1.0

**Status: Quellcode-Erststand, NICHT auf einem Android-Gerät abgenommen.**

Native Android-App, vollständig lokal, für Samsung Galaxy S26+. Android/Kotlin/Jetpack Compose/Room. Privates Projekt. `docs/MASTERKONZEPT_v0.8.md` enthält die zuletzt vorgelegte vollständige Konzeptgrundlage. Bestätigte Änderungen aus dem Chat (inkl. Icon, Kalender, Muster, 10-Minuten-Regel) sind umgesetzt, soweit im Projektstand als implementiert ausgewiesen. **Kein Code aus BIGGI oder `Adohadesign/zuhause` wurde übernommen.**

## Enthalten

- Dunkle, champagnergoldene Gestaltung und das freigegebene Icon in den Android-Icon-Größen.
- 3×3-Muster mit vierstelligem Notfall-PIN, PBKDF2-Prüfwert, Fehlversuchbegrenzung.
- Heute/„Jetzt“, Monatskalender, Karten pro Tag, mehrere Einträge, Zeit-/Datumsänderung, Mitternachtsregel.
- 10-Minuten-Unterbrechungsregel, Muster beim erneuten App-Aufruf (unabhängig von Kartenwechsel).
- Lange Texte und lokale Zwischenspeicherung, Editieren sowie Einfügen von Sprache an Cursorposition.
- Fotos und Videos aufnehmen oder aus Galerie importieren, vollständige lokale Kopien.
- PDFs intern seitenweise anzeigen; übrige Dateien über eine passende installierte App öffnen.
- Elemente sortieren, auf andere Tage verschieben, Einträge teilen/zusammenführen.
- Endgültiges Löschen nach Bestätigung, **ohne** Papierkorb.
- Manuelles ZIP-Backup mit Dateiprüfsummen; Restore ohne Überschreiben (führt Daten zusätzlich ein).
- Widget und App-Schnellverknüpfungen (Muster bleibt erforderlich).
- Offline-Spracheingabe **nur über die auf dem Gerät verfügbare On-Device-SpeechRecognizer-Schnittstelle**, mit abschnittsweisem Neustart. Keine Online-Erkennung und keine gespeicherten Audiodateien.

## Wichtige technische Grenzen

**Es wurde weder eine APK erzeugt noch ein Android-Build ausgeführt.** In dieser Entwicklungsumgebung sind das Android SDK und die Gradle-Abhängigkeiten nicht verfügbar. Die CI-Datei in `.github/workflows/android.yml` ist vorbereitet, aber nicht auf GitHub ausgeführt. Ein GitHub-Repository wurde nicht angelegt, weil die verbundene GitHub-Schnittstelle keine Repository-Neuanlage anbietet. Es gab auch keinen Test auf dem Samsung S26+.

Die **Qualität und 30-Minuten-Stabilität der Offline-Spracherkennung** sind offen. Das System stellt nicht auf Online-Verarbeitung um; wenn keine On-Device-Engine vorhanden ist, zeigt die App eine Meldung. Grundlegende Zeichensetzung wird ergänzt, eine umfassende sprachliche Korrektur (Grammatik, alle Eigennamen, Absatzlogik) ist **noch nicht nachgewiesen bzw. nicht vollständig implementiert**. Niemals als „garantiert korrekt“ behandeln.

**Dauerhafte APK-Updates:** Jedes neue GitHub-Runner-System besitzt üblicherweise eine andere *Debug*-Signatur. Eine Debug-APK ist deshalb nur für technische Ersttests ohne echte Tagebuchdaten geeignet. Für dauerhaft updatefähige Installationen ist ein **einmaliger, sicher aufzubewahrender Release-Signaturschlüssel** erforderlich. Die CI unterstützt dies, sobald die vier GitHub-Secrets vorhanden sind: `MEMORA_KEYSTORE_B64`, `MEMORA_STORE_PASSWORD`, `MEMORA_KEY_ALIAS`, `MEMORA_KEY_PASSWORD`. **Den Schlüssel niemals ins Repository oder in den Chat hochladen.** Keystore sicher offline aufbewahren. Ohne stabile Signatur können zukünftige APKs nicht ohne Neuinstallation installiert werden; das wäre ein Risiko für deine Tagebuchdaten.

Die Backup-ZIP enthält sämtliche Einträge, Anhänge und Zeitangaben, aber **keinen Wiederherstellungswert für Muster/PIN**. Nach Neuinstallation wird der lokale App-Zugang neu eingerichtet. Das ist bewusst so, weil ein vierstelliger PIN über eine ungeschützte Sicherung sonst offline erraten werden könnte. Die Sicherung ist in dieser Erstversion **nicht verschlüsselt** und sollte nur an einem sicheren Ort liegen. Eine Wiederherstellung fügt Daten hinzu (keine automatische Erkennung von Duplikaten). Die Sicherung wird in der App erst nach erfolgreichem ZIP-Schreiben gemeldet; eine separate Prüfung durch erneutes Einlesen des vollständigen ZIPs ist ein offener Härtungspunkt.

## GitHub und erster APK-Build

1. Auf GitHub im Konto `Adohadesign` ein **neues privates und leeres** Repository mit Namen `memora-solo` anlegen. Das vorhandene Repository `zuhause` nicht verändern.
2. Dieses ZIP auf dem PC entpacken. Anschließend **den Inhalt** des Ordners `MemoraSolo_v0.1.0` in das neue Repository hochladen. Die versteckte `.github`-Ordnerstruktur muss mit hochgeladen werden.
3. Auf GitHub unter **Actions → Android APK bauen** den Workflow prüfen. Wenn der Build erfolgreich ist, unter *Artifacts* die **Debug-APK ausschließlich zum Testen** herunterladen.
4. **Keine persönlichen Tagebucheinträge eingeben**, bis der Build erfolgreich, die Zugriffs- und Backupfunktionen auf dem S26+ getestet und eine dauerhaft signierte Release-APK eingerichtet ist.

Die hier vorbereiteten Dateien benötigen **keine kostenpflichtigen APIs**. GitHub-Dienste können eigene Freikontingente und Limits haben.

## Weiterer Ablauf ohne Funktionsverlust

Eine nachgewiesene lauffähige APK wird zur ersten bestätigten Basisversion. Erst danach Änderungen, jeweils mit eindeutiger Versionsnummer, Migrationsprüfung, Rückrollplan und Regressionstests. Keine Überschreibung vorhandener Gerätedaten. `PROJEKTSTAND.md`, `DATEISTRUKTUR.md`, `TESTPROTOKOLL.md` sind beigefügt.
