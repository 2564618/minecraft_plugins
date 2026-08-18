package com.maya4pa.gatedungeon.util;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;
import java.util.Set;

public final class Worlds {
    private static final Set<String> VANILLA_WORLDS = Set.of("world", "world_nether", "world_the_end");

    private Worlds() {}

    public static String name(World world) {
        return world == null ? null : world.getName();
    }

    public static String sanitizeWorldName(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }

    public static boolean isInstanceWorld(String name) {
        if (name == null || name.isEmpty()) return false;
        return name.startsWith(Constants.INSTANCE_WORLD_PREFIX)
                || name.startsWith(Constants.LEGACY_INSTANCE_WORLD_PREFIX);
    }

    public static boolean isVanillaWorld(String name) {
        return name != null && VANILLA_WORLDS.contains(name.toLowerCase(Locale.ROOT));
    }

    public static boolean isReservedWorldName(String name) {
        if (name == null || name.isEmpty()) return true;
        return isVanillaWorld(name) || isInstanceWorld(name);
    }

    public static boolean same(World a, World b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        String na = a.getName();
        String nb = b.getName();
        return na != null && na.equals(nb);
    }

    public static boolean same(Location a, Location b) {
        if (a == null || b == null) return false;
        return same(a.getWorld(), b.getWorld());
    }

    /** Safe squared distance; returns MAX_VALUE if worlds differ or are null. */
    public static double distanceSquared(Location a, Location b) {
        if (!same(a, b)) return Double.MAX_VALUE;
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
