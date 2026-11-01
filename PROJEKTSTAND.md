# Memora Solo – Projektstand v0.2.0 (01.11.2026)

Basis: bestehendes vollständiges Android-Projekt v0.1.3 mit manuellem Briefstudio-Import. Nutzer erlaubt Neuinstallation, weil derzeit keine Tagebucheinträge in der App vorhanden sind. Bestehende Funktionsbereiche wurden unverändert belassen.

Änderung: Einmalige Briefstudio-Anmeldung; danach Abruf automatisch beim Entsperren; Refresh-Token mit Android Keystore AES/GCM geschützt, Passwort nicht gespeichert. Lokaler Import dedupliziert über UUID und übernimmt diary_date/diary_time. Kein Hintergrunddienst und kein Push; kein Foto- oder Layoutimport.

Geänderte Dateien: `BriefstudioTransfer.kt`, `MainActivity.kt`, `app/build.gradle.kts`, GitHub Workflow-Artifactname, Projekt-/Installations-/Testdokumente. Datenbankschema unverändert (Room Version 2).

Status: Quellcodepaket, **kein erfolgreicher APK-Build oder Gerätetest**. Vor Verwendung über GitHub Actions bauen und auf realem Gerät prüfen.
