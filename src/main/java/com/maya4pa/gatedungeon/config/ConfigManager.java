package com.maya4pa.gatedungeon.config;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConfigManager {
    private final GateDungeonPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration mobsConfig;

    public ConfigManager(GateDungeonPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        File mobsFile = new File(plugin.getDataFolder(), "mobs.yml");
        if (!mobsFile.exists()) {
            plugin.saveResource("mobs.yml", false);
        }
        mobsConfig = YamlConfiguration.loadConfiguration(mobsFile);
    }

    public boolean isDebug() { return config.getBoolean("debug", false); }
    public List<String> getRanks() { return config.getStringList("ranks"); }

    public boolean isValidRank(String rank) {
        if (rank == null) return false;
        for (String r : getRanks()) {
            if (r.equalsIgnoreCase(rank)) return true;
        }
        return false;
    }

    public int getGateExpirationMinutes() { return config.getInt("gate.expiration-minutes", -1); }
    public int getMinDistanceBetweenGates() { return config.getInt("gate.min-distance-between-gates", 50); }
    public int getMaxPlayersPerGate() { return config.getInt("gate.max-players-per-gate", 1); }
    public int getAutoRemoveTimer() { return config.getInt("gate.auto-remove-timer", 300); }
    public int getBossDefeatRemovalDelay() { return config.getInt("gate.boss-defeat-removal-delay", 60); }

    public int getMaxActiveInstances() { return config.getInt("instance.max-active-instances", 50); }
    public int getCleanupInterval() { return config.getInt("instance.cleanup-interval", 30); }
    public int getPrepSeconds() { return Math.max(0, config.getInt("instance.prep-seconds", 30)); }
    public int getChunkLoadRadius() { return Math.max(1, config.getInt("instance.chunk-load-radius", 3)); }
    public int getSettleTicks() { return Math.max(1, config.getInt("instance.settle-ticks", 20)); }

    public boolean isReducedEffects() { return config.getBoolean("visuals.use-reduced-effects", false); }
    public String getRankColor(String rank) { return config.getString("visuals.rank-colors." + rank, "&7"); }

    public Material getMarkerMaterial(String type) {
        String path = "markers." + type.toLowerCase(Locale.ROOT).replace('_', '-');
        String matName = config.getString(path, "GOLD_BLOCK");
        try {
            return Material.valueOf(matName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Material.GOLD_BLOCK;
        }
    }

    public double getTeleportOffsetY() { return config.getDouble("markers.teleport-offset-y", 1.0); }
    public int getMarkerScanRadius() { return config.getInt("markers.scan-radius", 50); }

    public double getExitRadius() { return config.getDouble("exit.area-radius", 5); }
    public int getExitTeleportDelay() { return config.getInt("exit.teleport-delay-seconds", 5); }
    public boolean isExitParticle() { return config.getBoolean("exit.particle-effect", true); }
    public boolean isExitSound() { return config.getBoolean("exit.sound-effect", true); }

    /**
     * mobs.yml stores waves as YAML lists:
     * ranks.E.waves.1.mobs:
     *   - type: ZOMBIE
     *     name: ...
     */
    public List<MobEntry> getMobsForWave(String rank, int wave) {
        List<MobEntry> result = new ArrayList<>();
        String path = "ranks." + rank + ".waves." + wave + ".mobs";
        List<Map<?, ?>> list = mobsConfig.getMapList(path);
        for (Map<?, ?> map : list) {
            Object typeObj = map.get("type");
            if (typeObj == null) continue;
            String type = String.valueOf(typeObj);
            String name = map.get("name") != null ? String.valueOf(map.get("name")) : type;
            int amount = parseInt(map.get("amount"), 1);
            double health = parseDouble(map.get("health"), -1);
            double damage = parseDouble(map.get("damage"), -1);
            result.add(new MobEntry(type, name, amount, health, damage));
        }
        return result;
    }

    public BossEntry getBossForRank(String rank) {
        String path = "ranks." + rank + ".boss";
        if (!mobsConfig.isConfigurationSection(path)) return null;
        String type = mobsConfig.getString(path + ".type");
        if (type == null) return null;
        String name = mobsConfig.getString(path + ".name", "Boss");
        double health = mobsConfig.getDouble(path + ".health", 100);
        double damage = mobsConfig.getDouble(path + ".damage", 10);
        int exp = mobsConfig.getInt(path + ".rewards.exp", 100);
        return new BossEntry(type, name, health, damage, exp);
    }

    private static int parseInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o != null) {
            try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static double parseDouble(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o != null) {
            try { return Double.parseDouble(String.valueOf(o)); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public static class MobEntry {
        public final String type;
        public final String name;
        public final int amount;
        public final double health;
        public final double damage;

        public MobEntry(String type, String name, int amount) {
            this(type, name, amount, -1, -1);
        }

        public MobEntry(String type, String name, int amount, double health, double damage) {
            this.type = type;
            this.name = name;
            this.amount = amount;
            this.health = health;
            this.damage = damage;
        }
    }

    public static class BossEntry {
        public final String type;
        public final String name;
        public final double health;
        public final double damage;
        public final int expReward;

        public BossEntry(String type, String name, double health, double damage) {
            this(type, name, health, damage, 100);
        }

        public BossEntry(String type, String name, double health, double damage, int expReward) {
            this.type = type;
            this.name = name;
            this.health = health;
            this.damage = damage;
            this.expReward = expReward;
        }
    }
}
