package com.maya4pa.gatedungeon.template;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarkerScanner {
    private final GateDungeonPlugin plugin;
    private final World world;
    private final List<Marker> markers = new ArrayList<>();
    private Location entrance = null;
    private final Map<Material, String> markerMap;

    public MarkerScanner(GateDungeonPlugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        this.markerMap = new HashMap<>();
        // Only keep ENTRANCE, BOSS_SPAWN, EXIT, LOOT
        markerMap.put(plugin.getConfigManager().getMarkerMaterial("entrance"), "ENTRANCE");
        markerMap.put(plugin.getConfigManager().getMarkerMaterial("boss-spawn"), "BOSS_SPAWN");
        markerMap.put(plugin.getConfigManager().getMarkerMaterial("exit"), "EXIT");
        markerMap.put(plugin.getConfigManager().getMarkerMaterial("loot"), "LOOT");
    }

    public void scan() {
        int radius = plugin.getConfigManager().getMarkerScanRadius();
        Location spawn = world.getSpawnLocation();
        if (spawn == null) {
            spawn = new Location(world, 0, 128, 0);
        }
        int centerX = spawn.getBlockX();
        int centerZ = spawn.getBlockZ();

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("MarkerScanner: scanning around " + centerX + ", " + centerZ + " with radius " + radius);
        }

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = (centerX + dx) >> 4;
                int chunkZ = (centerZ + dz) >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    world.getChunkAt(chunkX, chunkZ);
                }

                int startY = 0;
                int endY = world.getMaxHeight();
                for (int y = startY; y < endY; y++) {
                    Block block = world.getBlockAt(centerX + dx, y, centerZ + dz);
                    Material type = block.getType();
                    String markerType = markerMap.get(type);
                    if (markerType != null) {
                        Location loc = block.getLocation().add(0.5, 0, 0.5);
                        Marker marker = new Marker(markerType, loc, null);
                        markers.add(marker);
                        if (markerType.equals("ENTRANCE") && entrance == null) {
                            entrance = loc;
                            if (plugin.getConfigManager().isDebug()) {
                                plugin.getLogger().info("Found entrance at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                            }
                        }
                    }
                }
            }
        }
    }

    public List<Marker> getMarkers() { return markers; }
    public Location getEntrance() { return entrance; }
}