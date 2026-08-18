# GateDungeon (mcserver)

Paper 1.21 plugin: Solo Leveling–style gates and instanced dungeons.

**Version 1.0.6**

## Commands
- `/gd create <name>` — create a void builder world (gold entrance block at spawn)
- `/gd assign <name> <rank>` — scan markers and add the dungeon to that rank’s random pool
- `/gd spawn <E|D|C|B|A|S>` — spawn a gate at your feet
- `/gd remove <id>` / `/gd list` / `/gd reload`
- `/gd dungeon create <world>` — same as `/gd create` (legacy)
- `/gd dungeon register <id> <rank> <world>` — same as assign, with a separate world name
- `/gd dungeon addregion <id> <wave> <MOB|ELITE>` — stick selection (right-click / sneak+right-click)
- `/gd dungeon forceexit [player]`

## Workflow
1. `/gd create forest` — build the dungeon in the new void world
2. `/gd assign forest E` — register it as an E-rank template
3. Repeat for more maps of the same rank (`/gd create cave` → `/gd assign cave E`)
4. `/gd spawn E` — walking in copies a **random** E-rank template into a fresh `gdinst_*` world

Walk into a gate (~2 blocks) to enter. The instance is fully loaded first, then you teleport in. Wave 1 waits **30 seconds** (configurable `instance.prep-seconds`) after the first player arrives; later joiners share that same timer. Clear waves, defeat the boss, stand on the emerald exit.
