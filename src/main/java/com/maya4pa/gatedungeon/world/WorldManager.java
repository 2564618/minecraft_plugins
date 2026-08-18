package com.maya4pa.gatedungeon.world;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.template.DungeonTemplate;
import com.maya4pa.gatedungeon.util.Constants;
import com.maya4pa.gatedungeon.util.Worlds;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class WorldManager {
    private final GateDungeonPlugin plugin;
    private final ConcurrentHashMap<String, World> loadedWorlds = new ConcurrentHashMap<>();

    public WorldManager(GateDungeonPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadExistingWorlds() {
        for (World w : Bukkit.getWorlds()) {
            String name = w.getName();
            if (Worlds.isInstanceWorld(name) || name.startsWith("template_") || name.startsWith("dun_")) {
                loadedWorlds.put(name, w);
            }
        }
    }

    public String nextInstanceWorldName() {
        return Constants.INSTANCE_WORLD_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public World getOrLoadWorld(String name) {
        if (!Worlds.isSafeWorldReference(name)) return null;
        World existing = getWorld(name);
        if (existing != null) return existing;
        File folder = new File(Bukkit.getWorldContainer(), name);
        if (!folder.isDirectory()) return null;
        return createVoidWorld(name, !Worlds.isInstanceWorld(name));
    }

    public World createVoidWorld(String name) {
        return createVoidWorld(name, true);
    }

    /**
     * Creates or loads a void world. New builder worlds auto-save and get a gold
     * entrance block at spawn; instance shells stay unsaved and empty.
     * Existing folders are loaded without overwriting blocks.
     */
    public World createVoidWorld(String name, boolean builder) {
        if (!Worlds.isSafeWorldReference(name)) {
            plugin.getLogger().warning("Rejected unsafe world name: " + name);
            return null;
        }
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            loadedWorlds.put(name, existing);
            return existing;
        }
        File folder = new File(Bukkit.getWorldContainer(), name);
        boolean alreadyOnDisk = folder.isDirectory();
        WorldCreator creator = new WorldCreator(name);
        creator.generator(new VoidWorldGenerator());
        creator.generateStructures(false);
        World world = Bukkit.createWorld(creator);
        if (world != null) {
            if (builder) {
                applyBuilderRules(world);
                if (!alreadyOnDisk) {
                    Location spawn = new Location(world, 0.5, 128, 0.5);
                    world.setSpawnLocation(spawn);
                    world.getBlockAt(0, 127, 0).setType(Material.BEDROCK);
                    world.getBlockAt(0, 128, 0).setType(Material.GOLD_BLOCK);
                    world.save();
                }
            } else {
                applyInstanceRules(world);
            }
            loadedWorlds.put(name, world);
        }
        return world;
    }

    /**
     * Creates an isolated {@code gdinst_*} copy of the template's built area
     * block-by-block. Avoids copying uid.dat / session.lock (world-folder clone).
     */
    public World createInstanceWorld(String worldName, DungeonTemplate template) {
        World templateWorld = getOrLoadWorld(template.getWorldName());
        if (templateWorld == null) {
            plugin.getLogger().warning("Template world '" + template.getWorldName() + "' is not loaded.");
            return null;
        }

        World world = createVoidWorld(worldName, false);
        if (world == null) return null;

        try {
            copyBuiltArea(templateWorld, world, template);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to copy template area: " + e.getMessage());
            unloadInstanceWorld(world);
            return null;
        }

        applyInstanceRules(world);
        Location entrance = template.getEntrance();
        if (entrance != null) {
            world.setSpawnLocation(entrance.getBlockX(), entrance.getBlockY(), entrance.getBlockZ());
        }
        loadedWorlds.put(worldName, world);
        return world;
    }

    private void copyBuiltArea(World from, World to, DungeonTemplate template) {
        int copied = 0;
        int radius = Math.max(32, plugin.getConfigManager().getMarkerScanRadius());
        Location center = template.getEntrance();
        if (center == null) {
            center = from.getSpawnLocation();
        }
        copied += copyBox(from, to,
                center.getBlockX() - radius, from.getMinHeight(), center.getBlockZ() - radius,
                center.getBlockX() + radius, from.getMaxHeight() - 1, center.getBlockZ() + radius);

        for (var region : template.getRegions()) {
            copied += copyBox(from, to,
                    region.getMinX() - 2, region.getMinY() - 2, region.getMinZ() - 2,
                    region.getMaxX() + 2, region.getMaxY() + 2, region.getMaxZ() + 2);
        }
        for (var marker : template.getMarkers()) {
            Location loc = marker.getLocation();
            copied += copyBox(from, to,
                    loc.getBlockX() - 2, loc.getBlockY() - 2, loc.getBlockZ() - 2,
                    loc.getBlockX() + 2, loc.getBlockY() + 2, loc.getBlockZ() + 2);
        }
        plugin.getLogger().info("Copied " + copied + " blocks from " + from.getName() + " to " + to.getName());
    }

    private int copyBox(World from, World to, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int yMin = Math.max(minY, from.getMinHeight());
        int yMax = Math.min(maxY, from.getMaxHeight() - 1);
        if (yMax < yMin) return 0;
        int copied = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    Block src = from.getBlockAt(x, y, z);
                    if (src.getType().isAir()) continue;
                    to.getBlockAt(x, y, z).setBlockData(src.getBlockData(), false);
                    copied++;
                }
            }
        }
        return copied;
    }

    private void applyBuilderRules(World world) {
        world.setAutoSave(true);
        world.setKeepSpawnInMemory(true);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setTime(6000);
    }

    private void applyInstanceRules(World world) {
        world.setAutoSave(false);
        world.setKeepSpawnInMemory(true);
        world.setDifficulty(Difficulty.HARD);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setTime(6000);
    }

    public boolean deleteBuilderWorld(String worldName) {
        if (!Worlds.isSafeWorldReference(worldName) || Worlds.isReservedWorldName(worldName)) return false;
        World world = getWorld(worldName);
        File folder = world != null ? world.getWorldFolder() : new File(Bukkit.getWorldContainer(), worldName);
        if (world != null) {
            Location fallback = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
            for (org.bukkit.entity.Player p : world.getPlayers()) {
                if (fallback != null) p.teleport(fallback);
            }
            Bukkit.unloadWorld(world, true);
            loadedWorlds.remove(worldName);
        }
        if (folder.exists()) {
            deleteDirectory(folder);
            return true;
        }
        return world != null;
    }

    public void unloadInstanceWorld(World world) {
        if (world == null) return;
        String name = world.getName();
        if (!Worlds.isInstanceWorld(name)) {
            Bukkit.unloadWorld(world, true);
            loadedWorlds.remove(name);
            return;
        }

        try {
            world.removePluginChunkTickets(plugin);
        } catch (Throwable ignored) {
        }
        Location fallback = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
        for (org.bukkit.entity.Player p : world.getPlayers()) {
            p.setGravity(true);
            if (fallback != null) p.teleport(fallback);
        }
        Bukkit.unloadWorld(world, false);
        loadedWorlds.remove(name);
        File folder = world.getWorldFolder();
        if (folder.exists()) {
            deleteDirectory(folder);
        }
    }

    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectory(child);
                }
            }
        }
        if (!dir.delete()) {
            plugin.getLogger().fine("Could not delete " + dir.getAbsolutePath());
        }
    }

    public World getWorld(String name) {
        World w = loadedWorlds.get(name);
        if (w != null) return w;
        return Bukkit.getWorld(name);
    }

    /**
     * Force-load a square of chunks around {@code center}, ticket them to this
     * plugin, then run {@code onDone} on the main thread.
     */
    public void preloadAround(World world, Location center, int chunkRadius, Runnable onDone) {
        if (center == null) {
            preloadLocations(world, List.of(), chunkRadius, onDone);
            return;
        }
        preloadLocations(world, List.of(center), chunkRadius, onDone);
    }

    public void preloadLocations(World world, List<Location> centers, int chunkRadius, Runnable onDone) {
        if (world == null) {
            if (onDone != null) Bukkit.getScheduler().runTask(plugin, onDone);
            return;
        }
        int radius = Math.max(1, chunkRadius);
        List<CompletableFuture<?>> futures = new ArrayList<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        if (centers != null) {
            for (Location center : centers) {
                if (center == null) continue;
                int cx = center.getBlockX() >> 4;
                int cz = center.getBlockZ() >> 4;
                for (int x = cx - radius; x <= cx + radius; x++) {
                    for (int z = cz - radius; z <= cz + radius; z++) {
                        long key = (((long) x) << 32) ^ (z & 0xffffffffL);
                        if (!seen.add(key)) continue;
                        world.addPluginChunkTicket(x, z, plugin);
                        try {
                            futures.add(world.getChunkAtAsync(x, z));
                        } catch (Throwable ignored) {
                            world.getChunkAt(x, z);
                        }
                    }
                }
            }
        }
        if (futures.isEmpty()) {
            if (onDone != null) Bukkit.getScheduler().runTask(plugin, onDone);
            return;
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((v, ex) -> {
            if (onDone != null) Bukkit.getScheduler().runTask(plugin, onDone);
        });
    }

    /** Places a small solid pad if the landing block is air so players cannot drop into void. */
    public void ensureSafePlatform(World world, Location landing) {
        if (world == null || landing == null) return;
        int y = landing.getBlockY() - 1;
        if (y < world.getMinHeight()) y = world.getMinHeight();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = world.getBlockAt(landing.getBlockX() + dx, y, landing.getBlockZ() + dz);
                if (!block.getType().isSolid()) {
                    block.setType(Material.BARRIER, false);
                }
            }
        }
    }

    public void cleanup() {
        for (World w : loadedWorlds.values()) {
            if (Worlds.isInstanceWorld(w.getName())) {
                unloadInstanceWorld(w);
            }
        }
    }
}
