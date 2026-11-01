# Memora Solo v0.1.2 – vollständiges GitHub-Projekt

1. ZIP entpacken. Im GitHub-Repository den vollständigen Projektinhalt aus `memora-solo-main` übernehmen (nicht den übergeordneten Ordner).
2. GitHub Actions → Android APK bauen → neuesten erfolgreichen Lauf → Artifacts herunterladen.
3. Nur wenn keine Daten benötigt werden: alte App deinstallieren, neue APK installieren.
4. Hochkantfoto direkt mit Kamera aufnehmen, speichern und erneut öffnen. Auch Galerie-Import, Querformatfoto, Text, Video und Anmeldung prüfen.

## Änderung
Nur direkte Kameraaufnahmen ohne gültige EXIF-Drehung, deren gespeicherte Pixel breiter als hoch sind, erhalten beim Import die EXIF-Drehung 270° (90° gegen Uhrzeigersinn). Bilder mit vorhandener Orientierung sowie Galerieimporte bleiben unangetastet. Die Originalaufnahme in der externen Kamera wird nicht verändert.

## Prüfstatus
Quellcode und ZIP-Struktur kontrolliert; kein Android-Build und kein Gerätetest in dieser Umgebung durchgeführt. Die Heuristik kann bei absichtlich quer aufgenommenen Direkt-Kamerafotos eine unerwünschte Drehung bewirken.
