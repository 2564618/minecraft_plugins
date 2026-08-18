package com.maya4pa.gatedungeon.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses and formats dungeon rank lists such as {@code E}, {@code E,D,C}, or {@code E D C}.
 */
public final class Ranks {

    private Ranks() {
        throw new AssertionError("Utility class");
    }

    public static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        LinkedHashSet<String> ranks = new LinkedHashSet<>();
        for (String part : raw.split("[,\\s/|]+")) {
            if (part.isBlank()) continue;
            ranks.add(part.toUpperCase(Locale.ROOT));
        }
        return new ArrayList<>(ranks);
    }

    public static List<String> parse(Collection<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        LinkedHashSet<String> ranks = new LinkedHashSet<>();
        for (String value : raw) {
            ranks.addAll(parse(value));
        }
        return new ArrayList<>(ranks);
    }

    public static String format(Collection<String> ranks) {
        if (ranks == null || ranks.isEmpty()) return "";
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String rank : ranks) {
            unique.addAll(parse(rank));
        }
        return String.join(",", unique);
    }

    public static Set<String> unique(Collection<String> ranks) {
        return new LinkedHashSet<>(parse(ranks));
    }
}
