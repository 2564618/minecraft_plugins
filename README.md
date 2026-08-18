# GateDungeon

Paper 1.21.3 plugin providing Solo Leveling-style gates and isolated dungeon instances.

**Version 1.1.0** · **Java 21**

## Features

- Rank-based gates (`E` through `S`) with configurable visuals and capacity
- Random dungeon-template selection for each rank
- Isolated `gdinst_*` worlds with preparation countdowns and preloaded chunks
- Region-based combat waves, configurable mobs, bosses, rewards, and safe exits
- Persistent gates, templates, regions, and player progress in SQLite
- Player statistics through `/gd stats [player]`
- Void builder worlds and marker-based template registration

## Commands

| Command | Description |
| --- | --- |
| `/gd stats [player]` | View your progress, or another player's progress with permission |
| `/gd create <name>` | Create a void builder world with an entrance marker at spawn |
| `/gd assign <name> <rank>` | Scan markers and add the dungeon to a rank's random pool |
| `/gd spawn <E\|D\|C\|B\|A\|S>` | Spawn a gate at your location |
| `/gd remove <id>` | Remove an active gate |
| `/gd list` | List active gates |
| `/gd reload` | Reload configuration without disrupting live instances |
| `/gd dungeon list` | List registered templates by rank |
| `/gd dungeon unregister <id>` | Unregister a template |
| `/gd dungeon addregion <id> <wave> <MOB\|ELITE>` | Add the selected region to a wave |
| `/gd dungeon removeregion <id> <region-id>` | Remove a wave region |
| `/gd dungeon forceexit [player]` | Force a player out of an instance |

Legacy forms such as `/gd dungeon create <world>` and
`/gd dungeon register <id> <rank> <world>` remain supported.

## Builder workflow

1. Run `/gd create forest`.
2. Build the dungeon in the generated void world.
3. Keep the gold entrance marker and optionally place diamond boss, emerald exit,
   and chest loot markers.
4. Select wave regions with the configured `wave-tool` (a stick by default):
   right-click for point one and sneak + right-click for point two.
5. Add each selection with `/gd dungeon addregion forest <wave> <MOB|ELITE>`.
6. Run `/gd assign forest E`.
7. Run `/gd spawn E` to test a gate using a random E-rank template.

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
├── template/   markers, regions, and template pools
├── util/       shared constants, messages, and world-name safety
└── world/      void generation, copying, loading, and cleanup
```
