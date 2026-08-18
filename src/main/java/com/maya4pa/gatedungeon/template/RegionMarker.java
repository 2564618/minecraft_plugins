package com.maya4pa.gatedungeon.template;

import com.maya4pa.gatedungeon.util.RegionTypes;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RegionMarker {
    private static final int RANDOM_ATTEMPTS = 32;
    private static final int MAX_COLUMN_SCAN = 64;

    private final String id;
    private final int wave;
    private final String worldName;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final String type;

    public RegionMarker(String id, int wave, String worldName, Location pos1, Location pos2, String type) {
        this.id = id;
        this.wave = wave;
        this.worldName = worldName;
        this.type = normalizeType(type);
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
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.type = normalizeType(type);
    }

    private static String normalizeType(String type) {
        String normalized = RegionTypes.normalize(type);
        return normalized != null ? normalized : RegionTypes.MOB;
    }

    public String getId() { return id; }
    public int getWave() { return wave; }
    public String getWorldName() { return worldName; }
    public String getType() { return type; }
    public boolean isElite() { return RegionTypes.ELITE.equals(type); }
    public boolean isMob() { return RegionTypes.MOB.equals(type); }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public int getSizeX() { return maxX - minX + 1; }
    public int getSizeY() { return maxY - minY + 1; }
    public int getSizeZ() { return maxZ - minZ + 1; }

    public int volume() {
        return getSizeX() * getSizeY() * getSizeZ();
    }

    public boolean contains(Location loc) {
        if (loc == null) return false;
        return contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Coordinate check only. Instance worlds reuse template coordinates, so the
     * stored builder world name is intentionally ignored.
     */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean containsColumn(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** Squared distance from a point to the region's AABB. Zero if inside. */
    public double distanceSquared(int x, int y, int z) {
        int dx = x < minX ? minX - x : (x > maxX ? x - maxX : 0);
        int dy = y < minY ? minY - y : (y > maxY ? y - maxY : 0);
        int dz = z < minZ ? minZ - z : (z > maxZ ? z - maxZ : 0);
        return (double) dx * dx + (double) dy * dy + (double) dz * dz;
    }

    /**
     * Returns a spawn location inside this region in {@code world}.
     * Prefers standing positions on solid ground; never leaves the region's XZ
     * and stays as close as possible to the selected Y range.
     */
    public Location getRandomLocation(World world) {
        if (world == null) return null;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < RANDOM_ATTEMPTS; attempt++) {
            int x = randomBlock(random, minX, maxX);
            int z = randomBlock(random, minZ, maxZ);
            Location standing = findStandingInColumn(world, x, z, random);
            if (standing != null) return standing;
        }

        int width = getSizeX();
        int depth = getSizeZ();
        int stepX = Math.max(1, width / 8);
        int stepZ = Math.max(1, depth / 8);
        int scanned = 0;
        List<Location> found = new ArrayList<>();
        for (int x = minX; x <= maxX && scanned < MAX_COLUMN_SCAN; x += stepX) {
            for (int z = minZ; z <= maxZ && scanned < MAX_COLUMN_SCAN; z += stepZ) {
                scanned++;
                Location standing = findStandingInColumn(world, x, z, random);
                if (standing != null) found.add(standing);
            }
        }
        if (!found.isEmpty()) {
            return found.get(random.nextInt(found.size()));
        }

        return getCenterLocation(world);
    }

    public Location getCenterLocation(World world) {
        if (world == null) return null;
        int x = (minX + maxX) / 2;
        int z = (minZ + maxZ) / 2;
        Location standing = findStandingInColumn(world, x, z, ThreadLocalRandom.current());
        if (standing != null) return standing;
        double y = Math.max(minY, Math.min(maxY, minY + 1));
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private Location findStandingInColumn(World world, int x, int z, ThreadLocalRandom random) {
        world.getChunkAt(x >> 4, z >> 4);

        int searchMin = minY - 1;
        int searchMax = maxY;
        if (world.getMinHeight() > searchMin) searchMin = world.getMinHeight();
        if (world.getMaxHeight() - 3 < searchMax) searchMax = world.getMaxHeight() - 3;
        if (searchMax < searchMin) return null;

        List<Integer> feetYs = new ArrayList<>();
        for (int groundY = searchMax; groundY >= searchMin; groundY--) {
            Block ground = world.getBlockAt(x, groundY, z);
            if (!ground.getType().isSolid()) continue;
            Block feet = world.getBlockAt(x, groundY + 1, z);
            Block head = world.getBlockAt(x, groundY + 2, z);
            if (!isSpawnableSpace(feet) || !isSpawnableSpace(head)) continue;
            int feetY = groundY + 1;
            if (feetY >= minY - 1 && feetY <= maxY + 2) {
                feetYs.add(feetY);
            }
        }
        if (feetYs.isEmpty()) return null;
        int feetY = feetYs.get(random.nextInt(feetYs.size()));
        return new Location(world, x + 0.5, feetY, z + 0.5);
    }

    private static boolean isSpawnableSpace(Block block) {
        return block != null && block.isPassable() && !block.isLiquid();
    }

    private static int randomBlock(ThreadLocalRandom random, int min, int max) {
        if (min >= max) return min;
        return random.nextInt(min, max + 1);
    }
}
