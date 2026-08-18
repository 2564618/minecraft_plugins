package com.maya4pa.gatedungeon.command;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.template.DungeonTemplate;
import com.maya4pa.gatedungeon.template.RegionMarker;
import com.maya4pa.gatedungeon.instance.DungeonInstance;
import com.maya4pa.gatedungeon.util.Constants;
import com.maya4pa.gatedungeon.util.MessageUtils;
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

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("gatedungeon.reload")) {
                    MessageUtils.send(sender, "no-permission");
                    return true;
                }
plugin.getConfigManager().loadConfigs();
                MessageUtils.init(plugin);
                plugin.getGateManager().getVisualizer().reloadColors();
                plugin.getTemplateManager().loadFromDatabase();
                plugin.getGateManager().loadFromDatabase();
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
                String rank = args[1].toUpperCase();
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
                    sender.sendMessage(ChatColor.RED + "Usage: /gd assign <name> <rank>");
                    return true;
                }
                handleAssign(sender, args[1], args[2]);
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
        String sub = args[1].toLowerCase();
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
                plugin.getTemplateManager().assign(sender, args[2], args[3].toUpperCase(), args[4]);
            }
            case "assign" -> {
                if (!hasDungeonAssign(sender)) {
                    MessageUtils.send(sender, "no-permission");
                    return;
                }
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /gd dungeon assign <name> <rank>");
                    return;
                }
                handleAssign(sender, args[2], args[3]);
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
                plugin.getTemplateManager().unregisterTemplate(args[2]);
                MessageUtils.send(sender, "template-unregistered", "id", args[2]);
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
                String worldName = Worlds.sanitizeWorldName(args[2]);
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
                if (!sender.hasPermission("gatedungeon.dungeon.admin")) {
                    MessageUtils.send(sender, "no-permission");
                    return;
                }
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /gd dungeon addregion <template-id> <wave> <type>");
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
                String type = args.length >= 5 ? args[4].toUpperCase() : "MOB";
                if (!type.equals("MOB") && !type.equals("ELITE")) {
                    sender.sendMessage(ChatColor.RED + "Type must be MOB or ELITE.");
                    return;
                }
                var listener = plugin.getRegionSelectionListener();
                Location pos1 = listener.getPos1(player);
                Location pos2 = listener.getPos2(player);
                if (pos1 == null || pos2 == null) {
                    sender.sendMessage(ChatColor.RED + "You must select two points first (right-click and sneak+right-click with a stick).");
                    return;
                }
                if (!pos1.getWorld().getName().equals(pos2.getWorld().getName())) {
                    sender.sendMessage(ChatColor.RED + "Both points must be in the same world.");
                    return;
                }
                DungeonTemplate template = plugin.getTemplateManager().getTemplate(templateId);
                if (template == null) {
                    sender.sendMessage(ChatColor.RED + "Template not found: " + templateId);
                    return;
                }
                if (!template.getWorldName().equalsIgnoreCase(pos1.getWorld().getName())) {
                    sender.sendMessage(ChatColor.RED + "The selected region must be inside the template's world (" + template.getWorldName() + ").");
                    return;
                }
                plugin.getTemplateManager().addRegion(templateId, wave, pos1, pos2, type);
                listener.clearSelection(player);
                sender.sendMessage(ChatColor.GREEN + "Region added to template '" + templateId + "' for wave " + wave + " (" + type + ").");
            }
            case "removeregion" -> {
                if (!sender.hasPermission("gatedungeon.dungeon.admin")) {
                    MessageUtils.send(sender, "no-permission");
                    return;
                }
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /gd dungeon removeregion <template-id> <region-id>");
                    return;
                }
                String templateId = args[2];
                String regionId = args[3];
                plugin.getTemplateManager().removeRegion(templateId, regionId);
                sender.sendMessage(ChatColor.GREEN + "Region removed.");
            }
            case "listregions" -> {
                if (!sender.hasPermission("gatedungeon.dungeon.admin")) {
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
        String name = Worlds.sanitizeWorldName(rawName);
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
            player.teleport(new Location(existing, 0.5, 128, 0.5));
            MessageUtils.send(player, "dungeon-exists", "name", name);
            return;
        }
        World world = plugin.getWorldManager().createVoidWorld(name, true);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Failed to create world '" + name + "'.");
            return;
        }
        player.teleport(new Location(world, 0.5, 128, 0.5));
        MessageUtils.send(player, "dungeon-created", "name", name);
        player.sendMessage(ChatColor.YELLOW + "You're at the spawn point. Build your dungeon, then /gd assign " + name + " <rank>");
    }

    private void handleAssign(CommandSender sender, String rawName, String rank) {
        String name = Worlds.sanitizeWorldName(rawName);
        if (name.isEmpty()) {
            MessageUtils.send(sender, "dungeon-invalid-name");
            return;
        }
        plugin.getTemplateManager().assign(sender, name, rank.toUpperCase());
    }

    private void listTemplates(CommandSender sender) {
        var templates = plugin.getTemplateManager().getAllTemplates();
        if (templates.isEmpty()) {
            sender.sendMessage("§eNo templates assigned. Use /gd create <name> then /gd assign <name> <rank>.");
            return;
        }
        sender.sendMessage("§6=== Rank template pools ===");
        Map<String, List<String>> byRank = new LinkedHashMap<>();
        for (String rank : plugin.getConfigManager().getRanks()) {
            byRank.put(rank.toUpperCase(), new ArrayList<>());
        }
        for (DungeonTemplate t : templates) {
            byRank.computeIfAbsent(t.getRank().toUpperCase(), k -> new ArrayList<>()).add(t.getId());
        }
        for (Map.Entry<String, List<String>> entry : byRank.entrySet()) {
            if (entry.getValue().isEmpty()) {
                sender.sendMessage("§7" + entry.getKey() + ": §8(none)");
            } else {
                sender.sendMessage("§e" + entry.getKey() + " §7(" + entry.getValue().size() + "): §f"
                        + String.join("§7, §f", entry.getValue()));
            }
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== GateDungeon " + plugin.getDescription().getVersion() + " Commands ===");
        sender.sendMessage("§7/gd create <name> §8- Create a void dungeon world");
        sender.sendMessage("§7/gd assign <name> <rank> §8- Add that dungeon to the rank's random pool");
        sender.sendMessage("§7/gd spawn <rank> §8- Spawn a gate at your feet");
        sender.sendMessage("§7/gd remove <id> §8- Remove a gate");
        sender.sendMessage("§7/gd list §8- List all active gates");
        sender.sendMessage("§7/gd reload §8- Reload config and database");
        sender.sendMessage("§7/gd dungeon list §8- List rank template pools");
        sender.sendMessage("§7/gd dungeon unregister <id> §8- Unregister a template");
        sender.sendMessage("§7/gd dungeon deleteworld <name> §8- Delete a dungeon world (must be unused)");
        sender.sendMessage("§7/gd dungeon addregion <template-id> <wave> <type> §8- Add a region for wave (type: MOB/ELITE)");
        sender.sendMessage("§7/gd dungeon removeregion <template-id> <region-id> §8- Remove a region");
        sender.sendMessage("§7/gd dungeon listregions <template-id> §8- List regions for a template");
        sender.sendMessage("§7/gd dungeon forceexit [player] §8- Force a player out of their dungeon (admin)");
    }
}