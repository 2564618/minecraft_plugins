# GateDungeon

Paper 1.21.3 plugin providing Solo Leveling-style gates and isolated dungeon instances.

**Version 1.2.0** · **Java 21**

## Features

- Rank-based gates (`E` through `S`) with configurable visuals and capacity
- Reusable dungeon templates: create a void world once, then assign it to one or many ranks
- Isolated `gdinst_*` worlds with preparation countdowns and preloaded chunks
- Region-based combat waves that spawn inside their assigned MOB / ELITE areas
- Wave-region outlines while holding the wave tool or another debug tool
- Persistent gates, templates, regions, and player progress in SQLite
- Player statistics through `/gd stats [player]`

## Commands

| Command | Description |
| --- | --- |
| `/gd stats [player]` | View your progress, or another player's progress with permission |
| `/gd create <name>` | Create a void builder world and register it as a draft template |
| `/gd assign <name> <rank[,rank...]>` | Scan markers and add the dungeon to one or more rank pools |
| `/gd spawn <E\|D\|C\|B\|A\|S>` | Spawn a gate at your location |
| `/gd remove <id>` | Remove an active gate |
| `/gd list` | List active gates |
| `/gd reload` | Reload configuration without disrupting live instances |
| `/gd dungeon list` | List registered templates by rank |
| `/gd dungeon unregister <id>` | Unregister a template |
| `/gd dungeon addregion <id> <wave> <MOB\|ELITE>` | Add the selected region to a wave |
| `/gd dungeon removeregion <id> <region-id>` | Remove a wave region |
| `/gd dungeon forceexit [player]` | Force a player out of an instance |

Examples:

```text
/gd create templateE1
/gd dungeon addregion templateE1 1 MOB
/gd assign templateE1 E
/gd assign templateE1 E,D,C
```

Legacy forms such as `/gd dungeon create <world>` and
`/gd dungeon register <id> <rank> <world>` remain supported.

## Builder workflow

1. Run `/gd create templateE1`. You are teleported into a void template world.
2. Build the dungeon. Keep the gold entrance marker and optionally place diamond boss, emerald exit, and chest loot markers.
3. Select a wave region with the configured `wave-tool` (a stick by default):
   left-click for point one and right-click for point two.
4. Hold the stick, debug stick, or blaze rod to preview region outlines.
5. Add the selection with `/gd dungeon addregion templateE1 1 MOB` or `ELITE`.
6. Assign the finished template to one or more ranks:
   `/gd assign templateE1 E` or `/gd assign templateE1 E,D,C`.
7. Run `/gd spawn E` to test a gate. Any assigned rank can roll this template.

World/template names accept only letters, numbers, and underscores. This avoids
ambiguous names and unsafe filesystem paths.

## Building and testing

The project includes the Maven Wrapper; only a Java 21 JDK is required.

```bash
./mvnw clean verify
```

The shaded plugin is produced at `target/GateDungeon.jar`. Copy it into a Paper
1.21.3 server's `plugins/` directory and restart the server.

GitHub Actions also runs `verify` on pushes and pull requests.

## Project structure

```text
src/main/java/com/maya4pa/gatedungeon/
├── command/    command handling and tab completion
├── config/     YAML configuration access
├── database/   SQLite persistence and player records
├── gate/       gate state, lifecycle, and visuals
├── instance/   dungeon instance state machine and combat
├── listener/   player movement, damage, and region selection
├── template/   markers, regions, template pools, and region viewer
├── util/       shared constants, messages, and world-name safety
└── world/      void generation, copying, loading, and cleanup
```
