package com.maya4pa.gatedungeon.config;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.util.RegionTypes;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ConfigManager {
    private final GateDungeonPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration mobsConfig;
    private Set<Material> debugTools = Set.of(Material.STICK, Material.DEBUG_STICK);

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
        refreshDebugTools();
        validateConfiguration();
    }

    private void refreshDebugTools() {
        Set<Material> tools = new HashSet<>();
        tools.add(getWaveTool());
        tools.add(Material.DEBUG_STICK);
        if (config != null) {
            for (String name : config.getStringList("debug-tools")) {
                if (name == null || name.isBlank()) continue;
                Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
                if (material != null) tools.add(material);
            }
        }
        debugTools = Set.copyOf(tools);
    }

    private void validateConfiguration() {
        if (getRanks().isEmpty()) {
            plugin.getLogger().warning("Configuration has no dungeon ranks; no gates can be created.");
        }
        if (config.getInt("gate.max-players-per-gate", 1) < 1) {
            plugin.getLogger().warning("gate.max-players-per-gate must be at least 1; using 1.");
        }
        int scanRadius = config.getInt("markers.scan-radius", 50);
        if (scanRadius < 1 || scanRadius > 128) {
            plugin.getLogger().warning("markers.scan-radius must be between 1 and 128; the value was clamped.");
        }

        List<Material> materials = List.of(
                getMarkerMaterial("entrance"),
                getMarkerMaterial("boss-spawn"),
                getMarkerMaterial("exit"),
                getMarkerMaterial("loot"));
        if (materials.stream().distinct().count() != materials.size()) {
            plugin.getLogger().warning("Marker materials must be unique; duplicate marker types will be ignored.");
        }
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

    public int getGateExpirationMinutes() { return Math.max(-1, config.getInt("gate.expiration-minutes", -1)); }
    public int getMinDistanceBetweenGates() { return Math.max(0, config.getInt("gate.min-distance-between-gates", 50)); }
    public int getMaxPlayersPerGate() { return Math.max(1, config.getInt("gate.max-players-per-gate", 1)); }
    public int getAutoRemoveTimer() { return Math.max(0, config.getInt("gate.auto-remove-timer", 300)); }
    public int getBossDefeatRemovalDelay() { return Math.max(0, config.getInt("gate.boss-defeat-removal-delay", 60)); }

    public int getMaxActiveInstances() { return Math.max(1, config.getInt("instance.max-active-instances", 50)); }
    public int getCleanupInterval() { return Math.max(10, config.getInt("instance.cleanup-interval", 30)); }
    public int getPrepSeconds() { return Math.max(0, config.getInt("instance.prep-seconds", 30)); }
    public int getChunkLoadRadius() { return Math.max(1, config.getInt("instance.chunk-load-radius", 3)); }
    public int getSettleTicks() { return Math.max(1, config.getInt("instance.settle-ticks", 20)); }

    public boolean isReducedEffects() { return config.getBoolean("visuals.use-reduced-effects", false); }
    public String getRankColor(String rank) { return config.getString("visuals.rank-colors." + rank, "&7"); }

    public Material getMarkerMaterial(String type) {
        String normalizedType = type.toLowerCase(Locale.ROOT).replace('_', '-');
        Material fallback = switch (normalizedType) {
            case "boss-spawn" -> Material.DIAMOND_BLOCK;
            case "exit" -> Material.EMERALD_BLOCK;
            case "loot" -> Material.CHEST;
            default -> Material.GOLD_BLOCK;
        };
        String matName = config.getString("markers." + normalizedType, fallback.name());
        if (matName == null) return fallback;
        Material material = Material.matchMaterial(matName.toUpperCase(Locale.ROOT));
        return material != null ? material : fallback;
    }

    public Material getWaveTool() {
        String materialName = config == null ? "STICK" : config.getString("wave-tool", "STICK");
        Material material = materialName == null ? null : Material.matchMaterial(materialName.toUpperCase(Locale.ROOT));
        return material != null ? material : Material.STICK;
    }

    public Set<Material> getDebugTools() {
        return debugTools;
    }

    public boolean isDebugTool(ItemStack item) {
        return item != null && !item.getType().isAir() && debugTools.contains(item.getType());
    }

    public boolean isDebugTool(Material material) {
        return material != null && debugTools.contains(material);
    }

    public double getTeleportOffsetY() { return config.getDouble("markers.teleport-offset-y", 1.0); }
    public int getMarkerScanRadius() { return Math.clamp(config.getInt("markers.scan-radius", 50), 1, 128); }

    public double getExitRadius() { return Math.max(0.5, config.getDouble("exit.area-radius", 5)); }
    public int getExitTeleportDelay() { return Math.max(0, config.getInt("exit.teleport-delay-seconds", 5)); }
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
            String category = null;
            if (map.get("category") != null) category = String.valueOf(map.get("category"));
            else if (map.get("type-group") != null) category = String.valueOf(map.get("type-group"));
            else if (map.get("elite") != null) category = String.valueOf(map.get("elite"));
            result.add(new MobEntry(type, name, amount, health, damage, category));
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
        public final String category;

        public MobEntry(String type, String name, int amount) {
            this(type, name, amount, -1, -1, null);
        }

        public MobEntry(String type, String name, int amount, double health, double damage) {
            this(type, name, amount, health, damage, null);
        }

        public MobEntry(String type, String name, int amount, double health, double damage, String category) {
            this.type = type;
            this.name = name;
            this.amount = amount;
            this.health = health;
            this.damage = damage;
            this.category = inferCategory(name, category);
        }

        public boolean isElite() {
            return RegionTypes.ELITE.equals(category);
        }

        public static String inferCategory(String name, String explicit) {
            if (explicit != null && !explicit.isBlank()) {
                String normalized = RegionTypes.normalize(explicit);
                if (normalized != null) return normalized;
                if (explicit.equalsIgnoreCase("true") || explicit.equalsIgnoreCase("yes")
                        || explicit.equals("1")) {
                    return RegionTypes.ELITE;
                }
                if (explicit.equalsIgnoreCase("false") || explicit.equalsIgnoreCase("no")
                        || explicit.equals("0")) {
                    return RegionTypes.MOB;
                }
            }
            if (name != null && name.toLowerCase(Locale.ROOT).contains("elite")) {
                return RegionTypes.ELITE;
            }
            return RegionTypes.MOB;
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
