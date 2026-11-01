# TESTPROTOKOLL – Memora Solo v0.1.0

Stand: 31.10.2026. Nur tatsächlich durchgeführte Prüfungen gelten als bestanden.

| Test | Ergebnis | Umfang |
|---|---|---|
| 10-Minuten-Regel < 600000 ms | BESTANDEN | Reiner Kotlin-JVM-Test |
| 10-Minuten-Regel >= 600000 ms | BESTANDEN | Reiner Kotlin-JVM-Test |
| Uhrzeit-Sprung zurück | BESTANDEN | Reiner Kotlin-JVM-Test |
| Mitternacht bleibt bei Startdatum | BESTANDEN | Reiner Kotlin-JVM-Test |
| Android XML-Dateien wohlgeformt | BESTANDEN | XML-Parser |
| Kotlin syntaktische Tokenprüfung | TEILWEISE | Kotlin-Compiler ohne Android-Bibliotheken; kein Android-Typ-/Buildtest |
| Vollständiger Gradle/AGP-Android-Build | NICHT GETESTET | Android SDK und Abhängigkeiten fehlen |
| APK auf S26+ installieren/starten | NICHT GETESTET | Kein Gerätezugang |
| Muster-PIN und Sperr-Rückkehr | NICHT GETESTET | S26+ erforderlich |
| FA-01 bis FA-28 Kalender/Medien/Bearbeitung | NICHT GETESTET | Runtime/Regression ausstehend |
| FA-30 bis FA-39 Diktat einschließlich 30 min | NICHT GETESTET | Lokale Engine und Gerät erforderlich |
| FA-50 bis FA-52 endgültiges Löschen | NICHT GETESTET | Datenbestands- und Fehlertests ausstehend |
| FA-60 bis FA-62 Widget/Schnellstart | NICHT GETESTET | Android-Gerät erforderlich |
| FA-70 bis FA-75 Backup/Restore/Updates | NICHT GETESTET | Realistische Daten inkl. Video erforderlich |
| FA-80 bis FA-83 Einstellungen/Muster/PIN | NICHT GETESTET | Android-Gerät erforderlich |
| Videowiedergabe mit Ton und Vollbild | NICHT GETESTET | Android-Gerät erforderlich |
| PDF-Renderer mit langen Dokumenten | NICHT GETESTET | Android-Gerät erforderlich |
| Wiederherstellung bei beschädigter ZIP | NICHT GETESTET | Instrumentierter Test erforderlich |
| Signierte Release-APK/Update-Identität | NICHT GETESTET | Dauerhafter Keystore und CI erforderlich |

**Nicht behauptet:** „alles funktioniert“, „APK fertig“, „Spracheingabe korrekt“ oder „Backup sicher“, bevor die Gerätetests durchgeführt sind.

## Pflichtabnahme vor produktiver Nutzung

1. GitHub-Workflow erfolgreich und reproduzierbar; Release mit stabiler Signatur.
2. Basiskomponenten auf dem S26+ prüfen, insbesondere Muster nach Kamera-Rückkehr.
3. 3-, 10- und 30-Minuten-Diktate mit Telefonanruf und Flugmodus; Fehler und Genauigkeit dokumentieren.
4. Erstellen, Editieren, Nachtragen, Verschieben, Teilen, endgültiges Löschen mit Testdaten.
5. Komplette ZIP-Sicherung einschließlich großem Video erstellen; frisches Testgerät / Testinstallation wiederherstellen; Dateien prüfen.
6. APK-Update über bestehende Installation mit Testeinträgen durchführen, ohne Deinstallation.
7. Fehlerfälle: Speicher voll, Berechtigung verweigert, App-Absturz, ZIP beschädigt, Unterbrechung bei Aufnahme.
