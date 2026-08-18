package com.maya4pa.gatedungeon.template;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.util.MessageUtils;
import com.maya4pa.gatedungeon.util.Ranks;
import com.maya4pa.gatedungeon.util.RegionTypes;
import com.maya4pa.gatedungeon.util.Worlds;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class TemplateManager {
    private final GateDungeonPlugin plugin;
    private final Map<String, DungeonTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, List<String>> rankTemplates = new ConcurrentHashMap<>();

    public TemplateManager(GateDungeonPlugin plugin) {
        this.plugin = plugin;
        rebuildRankIndex();
    }

    private void rebuildRankIndex() {
        rankTemplates.clear();
        for (String rank : plugin.getConfigManager().getRanks()) {
            rankTemplates.put(rank.toUpperCase(Locale.ROOT), new ArrayList<>());
        }
        for (DungeonTemplate template : templates.values()) {
            for (String rank : template.getRanks()) {
                if (rank.isBlank()) continue;
                rankTemplates.computeIfAbsent(rank.toUpperCase(Locale.ROOT), key -> new ArrayList<>())
                        .add(template.getId());
            }
        }
    }

    public void loadFromDatabase() {
        templates.clear();
        List<DungeonTemplate> loaded = plugin.getDatabaseManager().loadAllTemplates();
        for (DungeonTemplate template : loaded) {
            templates.put(template.getId(), template);
        }
        rebuildRankIndex();
        plugin.getLogger().info("Loaded " + loaded.size() + " templates.");
    }

    public void registerTemplate(DungeonTemplate template) {
        templates.put(template.getId(), template);
        rebuildRankIndex();
    }

    public boolean unregisterTemplate(String id) {
        DungeonTemplate template = getTemplate(id);
        if (template == null) return false;

        if (!plugin.getDatabaseManager().deleteTemplate(template.getId())) return false;
        templates.remove(template.getId());
        if (template.getSchematicFile().matches("[a-zA-Z0-9_.-]+")) {
            File schem = new File(plugin.getDataFolder(), "schematics/" + template.getSchematicFile());
            if (schem.isFile() && !schem.delete()) {
                plugin.getLogger().fine("Could not delete schematic " + schem.getName());
            }
        }
        rebuildRankIndex();
        return true;
    }

    public DungeonTemplate getTemplate(String id) {
        if (id == null) return null;
        DungeonTemplate exact = templates.get(id);
        if (exact != null) return exact;
        String normalized = Worlds.normalizeWorldName(id);
        DungeonTemplate byNormalized = templates.get(normalized);
        if (byNormalized != null) return byNormalized;
        return templates.values().stream()
                .filter(template -> template.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    public DungeonTemplate getTemplateByWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) return null;
        for (DungeonTemplate template : templates.values()) {
            if (template.getWorldName().equalsIgnoreCase(worldName)
                    || template.getId().equalsIgnoreCase(worldName)) {
                return template;
            }
        }
        return null;
    }

    public List<DungeonTemplate> getTemplatesByRank(String rank) {
        List<DungeonTemplate> result = new ArrayList<>();
        if (rank == null) return result;
        for (String id : rankTemplates.getOrDefault(rank.toUpperCase(Locale.ROOT), Collections.emptyList())) {
            DungeonTemplate template = templates.get(id);
            if (template != null) result.add(template);
        }
        return result;
    }

    public DungeonTemplate selectRandomTemplate(String rank) {
        List<DungeonTemplate> list = getTemplatesByRank(rank);
        if (list.isEmpty()) return null;
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    public List<DungeonTemplate> getAllTemplates() {
        return new ArrayList<>(templates.values());
    }

    public List<DungeonTemplate> getUnassignedTemplates() {
        List<DungeonTemplate> result = new ArrayList<>();
        for (DungeonTemplate template : templates.values()) {
            if (!template.isAssigned()) result.add(template);
        }
        return result;
    }

    public void registerFromWorld(String templateId, String rank, String worldName, Player player) {
        assign(player, templateId, rank, worldName);
    }

    /**
     * Creates (or returns) a draft template for a builder world so regions can be
     * added before the dungeon is assigned to any rank.
     */
    public DungeonTemplate ensureDraftTemplate(String rawName) {
        if (!Worlds.isValidWorldName(rawName)) return null;
        String id = Worlds.normalizeWorldName(rawName);
        DungeonTemplate existing = getTemplate(id);
        if (existing != null) return existing;
        World world = plugin.getWorldManager().getOrLoadWorld(id);
        if (world == null) return null;
        return createDraftTemplate(id, world);
    }

    public DungeonTemplate createDraftTemplate(String id, World world) {
        if (world == null || !Worlds.isValidWorldName(id)) return null;
        String normalizedId = Worlds.normalizeWorldName(id);
        DungeonTemplate existing = getTemplate(normalizedId);
        if (existing != null) return existing;

        Location entrance = world.getSpawnLocation();
        if (entrance == null) {
            entrance = new Location(world, 0.5, 128, 0.5);
        }

        DungeonTemplate template = new DungeonTemplate(
                normalizedId, "", normalizedId, world.getName(), world.getName(),
                entrance, System.currentTimeMillis());
        template.addMarker(new Marker("ENTRANCE", entrance, null));
        registerTemplate(template);
        plugin.getDatabaseManager().saveTemplate(template);
        plugin.getLogger().info("Created draft dungeon template '" + normalizedId + "'.");
        return template;
    }

    /**
     * Scan a builder world and put it in one or more rank random template pools.
     * Re-assigning the same name updates rank/markers and keeps regions.
     */
    public boolean assign(CommandSender sender, String name, String rank) {
        return assign(sender, name, rank, name);
    }

    public boolean assign(CommandSender sender, String templateId, String rank, String worldName) {
        if (!Worlds.isValidWorldName(templateId) || !Worlds.isValidWorldName(worldName)) {
            MessageUtils.send(sender, "dungeon-invalid-name");
            return false;
        }
        List<String> ranks = Ranks.parse(rank);
        if (ranks.isEmpty()) {
            MessageUtils.send(sender, "invalid-rank");
            return false;
        }
        for (String parsed : ranks) {
            if (!plugin.getConfigManager().isValidRank(parsed)) {
                MessageUtils.send(sender, "invalid-rank");
                return false;
            }
        }

        String normalizedId = Worlds.normalizeWorldName(templateId);
        String normalizedWorld = Worlds.normalizeWorldName(worldName);
        World world = plugin.getWorldManager().getOrLoadWorld(normalizedWorld);
        if (world == null) {
            MessageUtils.send(sender, "template-world-missing", "world", normalizedWorld);
            return false;
        }
        if (Worlds.isInstanceWorld(world.getName())) {
            sender.sendMessage(ChatColor.RED + "Cannot assign a live instance world.");
            return false;
        }

        MarkerScanner scanner = new MarkerScanner(plugin, world);
        scanner.scan();
        Location entrance = scanner.getEntrance();
        if (entrance == null) {
            MessageUtils.send(sender, "dungeon-no-entrance",
                    "block", plugin.getConfigManager().getMarkerMaterial("entrance").name());
            return false;
        }
        world.save();

        DungeonTemplate existing = getTemplate(normalizedId);
        DungeonTemplate template;
        if (existing != null) {
            if (!existing.getWorldName().equalsIgnoreCase(world.getName())) {
                sender.sendMessage(ChatColor.RED + "Template '" + normalizedId + "' already uses world '"
                        + existing.getWorldName() + "'. Unregister it before assigning a different world.");
                return false;
            }
            existing.setRanks(ranks);
            existing.setEntrance(entrance);
            existing.clearMarkers();
            for (Marker marker : scanner.getMarkers()) {
                existing.addMarker(marker);
            }
            template = existing;
            rebuildRankIndex();
        } else {
            template = new DungeonTemplate(normalizedId, Ranks.format(ranks), normalizedId, world.getName(),
                    world.getName(), entrance, System.currentTimeMillis());
            for (Marker marker : scanner.getMarkers()) {
                template.addMarker(marker);
            }
            registerTemplate(template);
        }
        plugin.getDatabaseManager().saveTemplate(template);
        MessageUtils.send(sender, "dungeon-assigned", "name", normalizedId, "rank", template.getRanksSerialized());
        plugin.getLogger().info("Assigned template '" + normalizedId + "' to rank(s) "
                + template.getRanksSerialized() + ".");
        return true;
    }

    public boolean addRegion(String templateId, int wave, Location pos1, Location pos2, String type) {
        DungeonTemplate template = getTemplate(templateId);
        if (template == null) {
            template = ensureDraftTemplate(templateId);
        }
        if (template == null || wave < 1 || pos1 == null || pos2 == null
                || pos1.getWorld() == null || pos2.getWorld() == null
                || !Worlds.same(pos1, pos2)
                || !template.getWorldName().equalsIgnoreCase(pos1.getWorld().getName())) {
            return false;
        }
        String normalizedType = RegionTypes.normalize(type);
        if (normalizedType == null) return false;

        String regionId;
        do {
            regionId = UUID.randomUUID().toString().substring(0, 8);
        } while (template.getRegion(regionId) != null);

        RegionMarker region = new RegionMarker(
                regionId, wave, template.getWorldName(), pos1, pos2, normalizedType);
        template.addRegion(region);
        plugin.getDatabaseManager().saveRegions(template);
        return true;
    }

    public boolean removeRegion(String templateId, String regionId) {
        DungeonTemplate template = getTemplate(templateId);
        if (template == null || !template.removeRegion(regionId)) return false;
        plugin.getDatabaseManager().saveRegions(template);
        return true;
    }

    public List<RegionMarker> getRegions(String templateId) {
        DungeonTemplate template = getTemplate(templateId);
        if (template == null) return Collections.emptyList();
        return template.getRegions();
    }
}
