package com.maya4pa.gatedungeon.template;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.concurrent.ThreadLocalRandom;

public class RegionMarker {
    private final String id;
    private final int wave;
    private final String worldName;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final String type; // "MOB" or "ELITE"

    public RegionMarker(String id, int wave, String worldName, Location pos1, Location pos2, String type) {
        this.id = id;
        this.wave = wave;
        this.worldName = worldName;
        this.type = type;
        this.minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        this.minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        this.minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        this.maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        this.maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        this.maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
    }

    public RegionMarker(String id, int wave, String worldName, int minX, int minY, int minZ,
                        int maxX, int maxY, int maxZ, String type) {
        this.id = id;
        this.wave = wave;
        this.worldName = worldName;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.type = type;
    }

    public String getId() { return id; }
    public int getWave() { return wave; }
    public String getWorldName() { return worldName; }
    public String getType() { return type; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /**
     * Returns a random location inside this region in the given world.
     * The world name is ignored; coordinates are absolute.
     * Attempts to find a suitable spawn location (not inside blocks).
     */
    public Location getRandomLocation(World world) {
        int attempts = 0;
        Location loc;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Try up to 10 times to find a suitable spawn location
        while (attempts < 10) {
            int x = random.nextInt(minX, maxX + 1);
            int y = random.nextInt(minY, maxY + 1);
            int z = random.nextInt(minZ, maxZ + 1);

            // Add 0.5 to get center of block
            loc = new Location(world, x + 0.5, y, z + 0.5);

            // Check if the block below is solid and the block above is air
            Block blockBelow = loc.getBlock().getRelative(0, -1, 0);
            Block blockAbove = loc.getBlock().getRelative(0, 1, 0);
            Block blockAt = loc.getBlock();

            // Spawn on top of solid blocks with air above
            if (blockBelow.getType().isSolid() &&
                blockAbove.getType().isAir() &&
                !blockAt.getType().isSolid()) {
                return loc;
            }

            attempts++;
        }

        // Fallback: spawn at the calculated location (may be in blocks)
        int x = random.nextInt(minX, maxX + 1);
        int y = random.nextInt(minY, maxY + 1);
        int z = random.nextInt(minZ, maxZ + 1);
        return new Location(world, x + 0.5, y, z + 0.5);
    }
}