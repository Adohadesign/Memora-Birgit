# Memora Solo v0.2.0 – automatische Briefstudio-Übernahme

## Für die Nutzung
1. Das vollständige Android-Projekt wird über den bereits bestehenden GitHub-Workflow zu einer APK gebaut. Dieses ZIP ist **Quellcode, keine APK**.
2. Die gebaute APK auf dem Handy installieren. Wenn die Signatur der alten App abweicht, kann Android ein Update verweigern. Da laut Nutzer keine Einträge vorhanden sind, ist in diesem Fall eine Neuinstallation nach eigener Prüfung möglich. **Nicht automatisch deinstallieren**.
3. In Memora Solo unter **Einstellungen → Briefstudio verbinden / synchronisieren** einmal das Briefstudio-Konto anmelden.
4. Danach in Briefstudio einen Text oder Brief speichern, Datum/Uhrzeit einstellen und **Für Memora Solo bereitstellen** wählen.
5. Memora Solo öffnen und das Muster entsperren. Neue Dokumente werden automatisch importiert, unter dem eingestellten Datum und der Uhrzeit angezeigt. Bei älteren Daten über den Kalender zum Tag wechseln.

## Grenzen
- Kein Hintergrund-Push bei geschlossener App; Abruf beim Entsperren benötigt Internet.
- Übertragen werden Titel und Text, nicht Fotos und das gestaltete Layout.
- Einmal übernommene Dokumente werden nicht erneut angelegt oder automatisch überschrieben, auch wenn sie später im Briefstudio bearbeitet werden.
- Einmalige Anmeldung speichert **nicht** das Passwort, sondern ein mit Android Keystore verschlüsseltes, erneuerbares Sitzungstoken.
- Briefstudio-Webserver-Dateien aus v0.1.6 bleiben unverändert. Die vorhandene Supabase-Datenbank bleibt unverändert.
