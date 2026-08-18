package com.maya4pa.gatedungeon.command;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.database.PlayerData;
import com.maya4pa.gatedungeon.template.DungeonTemplate;
import com.maya4pa.gatedungeon.template.RegionMarker;
import com.maya4pa.gatedungeon.instance.DungeonInstance;
import com.maya4pa.gatedungeon.util.Constants;
import com.maya4pa.gatedungeon.util.MessageUtils;
import com.maya4pa.gatedungeon.util.RegionTypes;
import com.maya4pa.gatedungeon.util.Worlds;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GateDungeonCommand implements CommandExecutor {
    private final GateDungeonPlugin plugin;

    public GateDungeonCommand(GateDungeonPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("gatedungeon.reload")) {
                    MessageUtils.send(sender, "no-permission");
                    return true;
                }
                plugin.getConfigManager().loadConfigs();
                MessageUtils.init(plugin);
                plugin.getGateManager().applyReloadedConfiguration();
                MessageUtils.send(sender, "reloaded");
                return true;
            }
            case "spawn" -> {
                if (!sender.hasPermission("gatedungeon.gate.spawn")) {
                    MessageUtils.send(sender, "no-permission");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can spawn gates.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /gd spawn <rank>");
                    return true;
                }
                String rank = args[1].toUpperCase(Locale.ROOT);
                var gate = plugin.getGateManager().createGate(player.getLocation(), rank, player);
                if (gate == null) {
                    sender.sendMessage(ChatColor.RED + "Failed to spawn gate. Check rank or distance.");
                } else {
                    MessageUtils.send(sender, "gate-created");
                }
                return true;
            }
            case "remove" -> {
                if (!sender.hasPermission("gatedungeon.gate.remove")) {
                    MessageUtils.send(sender, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /gd remove <gate-id>");
                    return true;
                }
                if (plugin.getGateManager().removeGate(args[1])) {
                    MessageUtils.send(sender, "gate-removed");
                } else {
                    MessageUtils.send(sender, "gate-not-found");
                }
                return true;
            }
            case "list" -> {
                if (!sender.hasPermission("gatedungeon.gate.list")) {
                    MessageUtils.send(sender, "no-permission");
                    return true;
                }
                var gates = plugin.getGateManager().getAllGates();
                if (gates.isEmpty()) {
                    sender.sendMessage("§eNo active gates.");
                } else {
                    sender.sendMessage("§6=== Active Gates ===");
                    for (var g : gates) {
var loc = g.getLocation();
                        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "?";
                        sender.sendMessage(String.format("§7ID: %s §7| Rank: §e%s §7| World: §e%s §7| X: %d Y: %d Z: %d",
                                g.getId(), g.getRank(), worldName,
                                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
                    }
                }
                return true;
            }
            case "create" -> {
                if (!hasDungeonCreate(sender)) {
                    MessageUtils.send(sender, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /gd create <name>");
                    return true;
                }
                handleCreate(sender, args[1]);
                return true;
            }
            case "assign" -> {
                if (!hasDungeonAssign(sender)) {
                    MessageUtils.send(sender, "no-permission");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /gd assign <name> <rank[,rank...]>");
                    return true;
                }
                handleAssign(sender, args[1], joinRanks(args, 2));
                return true;
            }
            case "stats" -> {
                handleStats(sender, args);
                return true;
            }
            case "dungeon" -> {
                handleDungeon(sender, args);
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private void handleDungeon(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /gd dungeon <register|unregister|list|create|assign|deleteworld|addregion|removeregion|listregions|forceexit>");
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "register" -> {
                if (!hasDungeonAssign(sender)) {
                    MessageUtils.send(sender, "no-permission");
                    return;
                }
                if (args.length < 5) {
                    sender.sendMessage(ChatColor.RED + "Usage: /gd dungeon register <id> <rank> <world>");
                    return;
                }
                plugin.getTemplateManager().assign(sender, args[2], args[3].toUpperCase(Locale.ROOT), args[4]);
            }
            case "assign" -> {
                if (!hasDungeonAssign(sender)) {
                    MessageUtils.send(sender, "no-permission");
                    return;
                }
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /gd dungeon assign <name> <rank[,rank...]>");
                    return;
                }
                handleAssign(sender, args[2], joinRanks(args, 3));
            }
            case "unregister" -> {
                if (!sender.hasPermission("gatedungeon.dungeon.unregister")) {
                    MessageUtils.send(sender, "no-permission");
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /gd dungeon unregister <id>");
                    return;
                }
                if (plugin.getTemplateManager().unregisterTemplate(args[2])) {
                    MessageUtils.send(sender, "template-unregistered", "id", args[2]);
                } else {
                    MessageUtils.send(sender, "template-not-found");
                }
            }
            case "list" -> listTemplates(sender);
            case "create" -> {
                if (!hasDungeonCreate(sender)) {
                    MessageUtils.send(sender, "no-permission");
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /gd dungeon create <world-name>");
                    return;
                }
                handleCreate(sender, args[2]);
            }
            case "deleteworld" -> {
                if (!sender.hasPermission("gatedungeon.dungeon.admin")) {
                    MessageUtils.send(sender, "no-permission");
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /gd dungeon deleteworld <world-name>");
                    return;
                }
                if (!Worlds.isValidWorldName(args[2])) {
                    MessageUtils.send(sender, "dungeon-invalid-name");
                    return;
                }
                String worldName = Worlds.normalizeWorldName(args[2]);
                if (Worlds.isVanillaWorld(worldName) || Worlds.isInstanceWorld(worldName)) {
                    sender.sendMessage(ChatColor.RED + "Cannot delete vanilla or live instance worlds.");
                    return;
                }
                for (DungeonTemplate t : plugin.getTemplateManager().getAllTemplates()) {
                    if (t.getWorldName().equalsIgnoreCase(worldName) || t.getId().equalsIgnoreCase(worldName)) {
                        sender.sendMessage(ChatColor.RED + "World '" + worldName + "' is used by template '" + t.getId() + "'. Unregister the template first.");
                        return;
                    }
                }
                if (!plugin.getWorldManager().deleteBuilderWorld(worldName)) {
                    sender.sendMessage(ChatColor.RED + "World '" + worldName + "' was not found.");
                    return;
                }
                sender.sendMessage(ChatColor.GREEN + "✔ World '" + worldName + "' has been deleted.");
            }
            case "addregion" -> {
                if (!hasDungeonRegion(sender)) {
                    MessageUtils.send(sender, "no-permission");
                    return;
                }
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /gd dungeon addregion <template-id> <wave> <MOB|ELITE>");
                    return;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can add regions (requires in-world selection).");
                    return;
                }
                String templateId = args[2];
                int wave;
                try {
                    wave = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Wave must be a number.");
                    return;
                }
                if (wave < 1) {
                    sender.sendMessage(ChatColor.RED + "Wave must be at least 1.");
                    return;
                }
                String type = args.length >= 5 ? args[4] : "MOB";
                String normalizedType = RegionTypes.normalize(type);
                if (normalizedType == null) {
                    sender.sendMessage(ChatColor.RED + "Type must be MOB or ELITE.");
                    return;
                }
                var listener = plugin.getRegionSelectionListener();
                Location pos1 = listener.getPos1(player);
                Location pos2 = listener.getPos2(player);
                if (pos1 == null || pos2 == null) {
                    sender.sendMessage(ChatColor.RED + "Select two points first: left-click and right-click with the wave tool (stick). Hold it to preview regions.");
                    return;
                }
                if (pos1.getWorld() == null || pos2.getWorld() == null
                        || !pos1.getWorld().getName().equals(pos2.getWorld().getName())) {
                    sender.sendMessage(ChatColor.RED + "Both points must be in the same world.");
                    return;
                }
                DungeonTemplate template = plugin.getTemplateManager().getTemplate(templateId);
                if (template == null) {
                    template = plugin.getTemplateManager().ensureDraftTemplate(templateId);
                }
                if (template == null) {
                    sender.sendMessage(ChatColor.RED + "Template not found: " + templateId
                            + ". Create it with /gd create " + templateId);
                    return;
                }
                if (!template.getWorldName().equalsIgnoreCase(pos1.getWorld().getName())) {
                    sender.sendMessage(ChatColor.RED + "The selected region must be inside the template's world (" + template.getWorldName() + ").");
                    return;
                }
                if (!plugin.getTemplateManager().addRegion(template.getId(), wave, pos1, pos2, normalizedType)) {
                    sender.sendMessage(ChatColor.RED + "Could not add the region. Check its world, wave, and type.");
                    return;
                }
                listener.clearSelection(player);
                sender.sendMessage(ChatColor.GREEN + "Region added to template '" + template.getId()
                        + "' for wave " + wave + " (" + normalizedType + "). Hold the wave tool to see it.");
            }
            case "removeregion" -> {
                if (!hasDungeonRegion(sender)) {
                    MessageUtils.send(sender, "no-permission");
                    return;
                }
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /gd dungeon removeregion <template-id> <region-id>");
                    return;
                }
                String templateId = args[2];
                String regionId = args[3];
                if (plugin.getTemplateManager().removeRegion(templateId, regionId)) {
                    sender.sendMessage(ChatColor.GREEN + "Region removed.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Template or region not found.");
                }
            }
            case "listregions" -> {
                if (!hasDungeonRegion(sender)) {
                    MessageUtils.send(sender, "no-permission");
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /gd dungeon listregions <template-id>");
                    return;
                }
                String templateId = args[2];
                DungeonTemplate template = plugin.getTemplateManager().getTemplate(templateId);
                if (template == null) {
                    sender.sendMessage(ChatColor.RED + "Template not found.");
                    return;
                }
                List<RegionMarker> regions = template.getRegions();
                if (regions.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "No regions defined for this template.");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "=== Regions for " + templateId + " ===");
                    for (RegionMarker r : regions) {
                        sender.sendMessage(ChatColor.WHITE + "ID: " + r.getId() + " | Wave: " + r.getWave() + " | Type: " + r.getType() +
                                " | Area: " + r.getMinX() + "," + r.getMinY() + "," + r.getMinZ() +
                                " -> " + r.getMaxX() + "," + r.getMaxY() + "," + r.getMaxZ());
                    }
                }
            }
            case "forceexit" -> {
                if (!sender.hasPermission("gatedungeon.dungeon.admin")) {
                    MessageUtils.send(sender, "no-permission");
                    return;
                }
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayer(args[2]);
                    if (target == null) {
                        sender.sendMessage(ChatColor.RED + "Player not found.");
                        return;
                    }
                } else if (sender instanceof Player) {
                    target = (Player) sender;
                } else {
                    sender.sendMessage(ChatColor.RED + "You must specify a player when using console.");
                    return;
                }

                DungeonInstance inst = plugin.getInstanceManager().getPlayerInstance(target);
                if (inst == null) {
                    sender.sendMessage(ChatColor.RED + target.getName() + " is not in a dungeon.");
                    return;
                }
                inst.teleportOut(target);
                sender.sendMessage(ChatColor.GREEN + "Forced " + target.getName() + " out of the dungeon.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown dungeon subcommand.");
        }
    }

    private void handleCreate(CommandSender sender, String rawName) {
        if (!Worlds.isValidWorldName(rawName)) {
            MessageUtils.send(sender, "dungeon-invalid-name");
            return;
        }
        String name = Worlds.normalizeWorldName(rawName);
        if (name.isEmpty()) {
            MessageUtils.send(sender, "dungeon-invalid-name");
            return;
        }
        if (Worlds.isReservedWorldName(name)) {
            MessageUtils.send(sender, "dungeon-reserved-name");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can create dungeon worlds.");
            return;
        }
        World existing = plugin.getWorldManager().getWorld(name);
        if (existing != null) {
            plugin.getTemplateManager().ensureDraftTemplate(name);
            player.teleport(new Location(existing, 0.5, 128, 0.5));
            MessageUtils.send(player, "dungeon-exists", "name", name);
            return;
        }
        World world = plugin.getWorldManager().createVoidWorld(name, true);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Failed to create world '" + name + "'.");
            return;
        }
        DungeonTemplate template = plugin.getTemplateManager().ensureDraftTemplate(name);
        player.teleport(new Location(world, 0.5, 128, 0.5));
        MessageUtils.send(player, "dungeon-created", "name", name);
        player.sendMessage(ChatColor.YELLOW + "This void world is a reusable dungeon template.");
        player.sendMessage(ChatColor.GRAY + "Select wave areas with the stick (left-click / right-click). Hold it to preview markers.");
        player.sendMessage(ChatColor.GRAY + "Add a region: /gd dungeon addregion " + name + " 1 MOB");
        player.sendMessage(ChatColor.GRAY + "Assign ranks: /gd assign " + name + " E   or   /gd assign " + name + " E,D,C");
        if (template == null) {
            player.sendMessage(ChatColor.RED + "Warning: the draft template could not be saved. Try /gd assign after building.");
        }
    }

    private void handleAssign(CommandSender sender, String rawName, String rank) {
        if (!Worlds.isValidWorldName(rawName)) {
            MessageUtils.send(sender, "dungeon-invalid-name");
            return;
        }
        String name = Worlds.normalizeWorldName(rawName);
        if (name.isEmpty()) {
            MessageUtils.send(sender, "dungeon-invalid-name");
            return;
        }
        plugin.getTemplateManager().assign(sender, name, rank);
    }

    private String joinRanks(String[] args, int from) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) builder.append(',');
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private void handleStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Constants.PERMISSION_STATS)) {
            MessageUtils.send(sender, "no-permission");
            return;
        }

        PlayerData data;
        if (args.length >= 2) {
            if (!sender.hasPermission(Constants.PERMISSION_STATS_OTHERS)) {
                MessageUtils.send(sender, "no-permission");
                return;
            }
            data = plugin.getDatabaseManager().findPlayerByName(args[1]).orElse(null);
            if (data == null) {
                MessageUtils.send(sender, "stats-not-found", "player", args[1]);
                return;
            }
        } else if (sender instanceof Player player) {
            data = plugin.getDatabaseManager().loadPlayer(player.getUniqueId());
            if (data.getName() == null) data.setName(player.getName());
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /gd stats <player>");
            return;
        }

        int attempts = data.getTotalCompleted() + data.getTotalFailed();
        double successRate = attempts == 0 ? 0.0 : data.getTotalCompleted() * 100.0 / attempts;
        sender.sendMessage(ChatColor.GOLD + "=== Dungeon Stats: " + data.getDisplayName() + " ===");
        sender.sendMessage(ChatColor.GRAY + "Cleared: " + ChatColor.GREEN + data.getTotalCompleted()
                + ChatColor.GRAY + " | Failed: " + ChatColor.RED + data.getTotalFailed());
        sender.sendMessage(ChatColor.GRAY + "Highest rank: " + ChatColor.YELLOW + data.getHighestRank()
                + ChatColor.GRAY + " | Success: " + ChatColor.AQUA + String.format(Locale.ROOT, "%.1f%%", successRate));
    }

    private void listTemplates(CommandSender sender) {
        var templates = plugin.getTemplateManager().getAllTemplates();
        if (templates.isEmpty()) {
            sender.sendMessage("§eNo templates yet. Use /gd create <name>, add regions, then /gd assign <name> <rank>.");
            return;
        }
        sender.sendMessage("§6=== Rank template pools ===");
        Map<String, List<String>> byRank = new LinkedHashMap<>();
        for (String rank : plugin.getConfigManager().getRanks()) {
            byRank.put(rank.toUpperCase(Locale.ROOT), new ArrayList<>());
        }
        List<String> unassigned = new ArrayList<>();
        for (DungeonTemplate t : templates) {
            if (!t.isAssigned()) {
                unassigned.add(t.getId());
                continue;
            }
            for (String rank : t.getRanks()) {
                byRank.computeIfAbsent(rank.toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(t.getId());
            }
        }
        for (Map.Entry<String, List<String>> entry : byRank.entrySet()) {
            if (entry.getValue().isEmpty()) {
                sender.sendMessage("§7" + entry.getKey() + ": §8(none)");
            } else {
                sender.sendMessage("§e" + entry.getKey() + " §7(" + entry.getValue().size() + "): §f"
                        + String.join("§7, §f", entry.getValue()));
            }
        }
        if (!unassigned.isEmpty()) {
            sender.sendMessage("§7Unassigned drafts: §f" + String.join("§7, §f", unassigned));
        }
    }

    private boolean hasDungeonCreate(CommandSender sender) {
        return sender.hasPermission(Constants.PERMISSION_DUNGEON_CREATE)
                || sender.hasPermission(Constants.PERMISSION_DUNGEON_ADMIN)
                || sender.hasPermission(Constants.PERMISSION_ADMIN);
    }

    private boolean hasDungeonAssign(CommandSender sender) {
        return sender.hasPermission(Constants.PERMISSION_DUNGEON_ASSIGN)
                || sender.hasPermission(Constants.PERMISSION_DUNGEON_REGISTER)
                || sender.hasPermission(Constants.PERMISSION_DUNGEON_ADMIN)
                || sender.hasPermission(Constants.PERMISSION_ADMIN);
    }

    private boolean hasDungeonRegion(CommandSender sender) {
        return hasDungeonCreate(sender) || hasDungeonAssign(sender);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== GateDungeon " + plugin.getDescription().getVersion() + " Commands ===");
        sender.sendMessage("§7/gd create <name> §8- Create a void dungeon template world");
        sender.sendMessage("§7/gd assign <name> <rank[,rank...]> §8- Allow those rank gates to use the template");
        sender.sendMessage("§7/gd spawn <rank> §8- Spawn a gate at your feet");
        sender.sendMessage("§7/gd remove <id> §8- Remove a gate");
        sender.sendMessage("§7/gd list §8- List all active gates");
        sender.sendMessage("§7/gd stats [player] §8- View dungeon completion statistics");
        sender.sendMessage("§7/gd reload §8- Reload configuration");
        sender.sendMessage("§7/gd dungeon list §8- List rank template pools");
        sender.sendMessage("§7/gd dungeon unregister <id> §8- Unregister a template");
        sender.sendMessage("§7/gd dungeon deleteworld <name> §8- Delete a dungeon world (must be unused)");
        sender.sendMessage("§7/gd dungeon addregion <template-id> <wave> <MOB|ELITE> §8- Add the selected wave region");
        sender.sendMessage("§7/gd dungeon removeregion <template-id> <region-id> §8- Remove a region");
        sender.sendMessage("§7/gd dungeon listregions <template-id> §8- List regions for a template");
        sender.sendMessage("§7/gd dungeon forceexit [player] §8- Force a player out of their dungeon (admin)");
    }
}