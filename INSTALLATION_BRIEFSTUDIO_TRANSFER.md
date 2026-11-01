# Memora Solo v0.1.3 – Briefstudio-Import

**Basis:** bestätigte v0.1.2. Datenbankmigration Room 1→2: neue Zuordnungstabelle; bestehende Tabellen und Einträge bleiben erhalten.

1. Android-Projekt-Quellcode dieses ZIPs in ein neues GitHub-Commit übertragen und GitHub Actions zum Bauen der APK verwenden. Nicht die App deinstallieren; dabei könnten lokale Tagebuchdaten verloren gehen.
2. Vor dem App-Update über die bestehende Funktion Einstellungen → Vollständige Sicherung erstellen eine ZIP-Sicherung des Tagebuchs anlegen.
3. Signatur muss zur bisher installierten App passen; eine anders signierte APK lässt sich nicht datenbewahrend als Update installieren.
4. Briefstudio-Webupdate v0.1.6: `app.js` und `index.html` im Server-Root ersetzen, `config.js` unangetastet lassen.
5. Briefstudio: Dokument öffnen, Datum und Uhrzeit setzen, „Zur Übertragung bereitstellen“ klicken.
6. Android: Memora Solo öffnen, Muster entsperren, Einstellungen → Aus Briefstudio übernehmen, Briefstudio-E-Mail und -Passwort eingeben, „Jetzt übernehmen“.
7. Das Dokument erscheint am ausgewählten Datum/Uhrzeit als **neuer** lokaler Tagebucheintrag. Wiederholung importiert dasselbe Dokument nicht erneut.

**Grenzen:** Der Import überträgt Titel und reinen Text. Fotos, Briefpapier, freie Elemente und Layout werden nicht übertragen. Aktualisierte Dokumente werden nach einmaligem Import nicht automatisch überschrieben. Die Übertragung findet nur manuell statt; es gibt keine Hintergrund-Synchronisation.

**Rollback:** Nur die Webdateien lassen sich ohne Weiteres zurücktauschen. Eine Rückkehr der Android-Datenbank von Version 2 auf Version 1 ohne Wiederherstellung/Spezialmigration ist NICHT möglich; deshalb vor Installation sichern und erst nach verifiziertem Build testen.
