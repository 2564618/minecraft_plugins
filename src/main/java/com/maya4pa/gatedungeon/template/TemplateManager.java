package com.maya4pa.gatedungeon.template;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.util.MessageUtils;
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
            rankTemplates.put(rank.toUpperCase(), new ArrayList<>());
        }
        for (DungeonTemplate t : templates.values()) {
            rankTemplates.computeIfAbsent(t.getRank().toUpperCase(), k -> new ArrayList<>()).add(t.getId());
        }
    }

    public void loadFromDatabase() {
        templates.clear();
        List<DungeonTemplate> loaded = plugin.getDatabaseManager().loadAllTemplates();
        for (DungeonTemplate t : loaded) {
            templates.put(t.getId(), t);
        }
        rebuildRankIndex();
        plugin.getLogger().info("Loaded " + loaded.size() + " templates.");
    }

    public void registerTemplate(DungeonTemplate template) {
        templates.put(template.getId(), template);
        rebuildRankIndex();
    }

    public void unregisterTemplate(String id) {
        DungeonTemplate t = templates.remove(id);
        if (t != null) {
            plugin.getDatabaseManager().deleteTemplate(id);
            File schem = new File(plugin.getDataFolder(), "schematics/" + t.getSchematicFile());
            if (schem.exists() && !schem.delete()) {
                plugin.getLogger().fine("Could not delete schematic " + schem.getName());
            }
            rebuildRankIndex();
        }
    }

    public DungeonTemplate getTemplate(String id) {
        return templates.get(id);
    }

    public List<DungeonTemplate> getTemplatesByRank(String rank) {
        List<DungeonTemplate> result = new ArrayList<>();
        if (rank == null) return result;
        for (String id : rankTemplates.getOrDefault(rank.toUpperCase(), Collections.emptyList())) {
            DungeonTemplate t = templates.get(id);
            if (t != null) result.add(t);
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

    public void registerFromWorld(String templateId, String rank, String worldName, Player player) {
        assign(player, templateId, rank, worldName);
    }

    /**
     * Scan a builder world and put it in the rank's random template pool.
     * Re-assigning the same name updates rank/markers and keeps regions.
     */
    public boolean assign(CommandSender sender, String name, String rank) {
        return assign(sender, name, rank, name);
    }

    public boolean assign(CommandSender sender, String templateId, String rank, String worldName) {
        if (templateId == null || templateId.isEmpty()) {
            MessageUtils.send(sender, "dungeon-invalid-name");
            return false;
        }
        if (!plugin.getConfigManager().isValidRank(rank)) {
            MessageUtils.send(sender, "invalid-rank");
            return false;
        }
        String rankKey = rank.toUpperCase();
        World world = plugin.getWorldManager().getOrLoadWorld(worldName);
        if (world == null) {
            MessageUtils.send(sender, "template-world-missing", "world", worldName);
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

        DungeonTemplate existing = getTemplate(templateId);
        DungeonTemplate template;
        if (existing != null) {
            existing.setRank(rankKey);
            existing.setEntrance(entrance);
            existing.clearMarkers();
            for (Marker m : scanner.getMarkers()) {
                existing.addMarker(m);
            }
            template = existing;
            rebuildRankIndex();
        } else {
            template = new DungeonTemplate(templateId, rankKey, templateId, world.getName(), world.getName(),
                    entrance, System.currentTimeMillis());
            for (Marker m : scanner.getMarkers()) {
                template.addMarker(m);
            }
            registerTemplate(template);
        }
        plugin.getDatabaseManager().saveTemplate(template);
        MessageUtils.send(sender, "dungeon-assigned", "name", templateId, "rank", rankKey);
        plugin.getLogger().info("Assigned template '" + templateId + "' to rank " + rankKey
                + " (" + getTemplatesByRank(rankKey).size() + " in pool).");
        return true;
    }

    public boolean addRegion(String templateId, int wave, Location pos1, Location pos2, String type) {
        DungeonTemplate template = getTemplate(templateId);
        if (template == null) return false;
        String regionId = UUID.randomUUID().toString().substring(0, 8);
        RegionMarker region = new RegionMarker(regionId, wave, template.getWorldName(), pos1, pos2, type);
        template.addRegion(region);
        plugin.getDatabaseManager().saveRegions(template);
        return true;
    }

    public boolean removeRegion(String templateId, String regionId) {
        DungeonTemplate template = getTemplate(templateId);
        if (template == null) return false;
        template.removeRegion(regionId);
        plugin.getDatabaseManager().saveRegions(template);
        return true;
    }

    public List<RegionMarker> getRegions(String templateId) {
        DungeonTemplate template = getTemplate(templateId);
        if (template == null) return Collections.emptyList();
        return template.getRegions();
    }
}
