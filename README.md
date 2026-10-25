# Blueprint Assist (Forge 26.2, nur Client)

Liest Blueprints aus `.minecraft/schematics` (im Ordner deiner CurseForge-Instanz):
`.litematic`, `.schem`, `.nbt`.

## Benutzen im Spiel
1. `/bp list`  - zeigt deine Dateien
2. `/bp load <name>`  - laedt eine Blueprint
3. Auf einen Block schauen, `/bp origin`  - legt die Ecke (0,0,0) fest
   (Feinschliff mit `/bp move <dx> <dy> <dz>`)
4. Fehlende Bloecke werden mit Partikeln markiert. Wenn du eine freie Stelle anschaust,
   waehlt die Mod den passenden Block in der Hotbar aus. Dann normal Rechtsklick.
5. `/bp swap` schaltet den automatischen Wechsel an/aus, `/bp off` entlaedt die Blueprint.

Version 1: nur Hotbar (Bloecke muessen in den Slots 1-9 liegen), Ausrichtung von Treppen,
Tueren usw. wird noch nicht beachtet, keine Drehung der Blueprint.

## Bauen
Variante A (nichts installieren): Projekt bei GitHub hochladen. Unter "Actions" wird die Mod
automatisch gebaut, die .jar liegt dann als "blueprintassist-jar" zum Download bereit.

Variante B (lokal): JDK 17 oder neuer installieren, im Projektordner `gradlew build`
(Windows: `gradlew.bat build`) ausfuehren. Gradle holt Java 25 selbst.
Die Datei liegt danach in `build/libs/`. In CurseForge: Instanz > "Ordner oeffnen" > `mods`.
