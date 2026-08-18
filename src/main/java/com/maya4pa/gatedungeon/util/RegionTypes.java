package com.maya4pa.gatedungeon.util;

import java.util.Locale;

/**
 * Normalizes wave-region types accepted by {@code /gd dungeon addregion}.
 */
public final class RegionTypes {

    public static final String MOB = "MOB";
    public static final String ELITE = "ELITE";

    private RegionTypes() {
        throw new AssertionError("Utility class");
    }

    public static String normalize(String type) {
        if (type == null || type.isBlank()) return null;
        String value = type.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "MOB", "MOBS", "MOB_SPAWN", "NORMAL" -> MOB;
            case "ELITE", "ELITES", "ELITE_MOB", "ELITE_MOBS" -> ELITE;
            default -> null;
        };
    }

    public static boolean isElite(String type) {
        return ELITE.equals(normalize(type));
    }

    public static boolean isMob(String type) {
        return MOB.equals(normalize(type));
    }

    public static boolean isValid(String type) {
        return normalize(type) != null;
    }
}
