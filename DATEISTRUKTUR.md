# DATEISTRUKTUR – Memora Solo v0.1.0

```text
MemoraSolo_v0.1.0/
├── .github/workflows/android.yml       GitHub Actions: Android Build und signierter Release mit Secrets
├── app/build.gradle.kts                 Android-Abhängigkeiten und Versionierung
├── app/src/main/AndroidManifest.xml     App und Systemberechtigungen
├── app/src/main/java/de/adohadesign/memorasolo/
│   ├── MainActivity.kt                  Oberfläche, Musteransicht, Kalender, Medienbedienung
│   ├── data/DiaryDatabase.kt            Room-Entitäten und DAO
│   ├── data/DiaryRepository.kt          Sitzung, Medien, Einträge und Datenoperationen
│   ├── data/EntryPolicy.kt              Geprüfte Zeit- und Mitternachtsregeln
│   ├── security/PatternSecurity.kt       Muster/PIN-Schutz
│   ├── voice/OfflineDictation.kt        On-Device-Spracherkennung
│   ├── backup/ArchiveManager.kt         Manuelle ZIP-Sicherung und Import
│   └── widget/MemoraWidget.kt          Launcher-Widget
├── app/src/main/res/                    Design, Icons, Widget, Datei-Freigaben
├── app/src/test/                        JVM-Regeltests
├── docs/MASTERKONZEPT_v0.8.md          Ursprüngliche aktuelle Konzeptbasis
├── README.md                            Nutzung und Grenzen
├── PROJEKTSTAND.md                      Implementierungsstand und offene Nachweise
├── TESTPROTOKOLL.md                     Tatsächliche Tests, noch offene Tests
└── DATEISTRUKTUR.md                     Diese Beschreibung
```

**Speicherung auf dem Handy:** `memora.db` im privaten Datenbankbereich der App; Dateikopien in `files/media/`. Kein SQL-Server. Im ZIP sind weder private Tagebuchdaten noch Schlüssel enthalten.
