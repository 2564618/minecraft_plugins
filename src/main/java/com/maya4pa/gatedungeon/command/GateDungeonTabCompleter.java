package com.maya4pa.gatedungeon.command;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.gate.Gate;
import com.maya4pa.gatedungeon.template.DungeonTemplate;
import com.maya4pa.gatedungeon.template.RegionMarker;
import com.maya4pa.gatedungeon.util.Worlds;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GateDungeonTabCompleter implements TabCompleter {

    private final GateDungeonPlugin plugin;
    private static final List<String> SUBCOMMANDS = List.of(
            "stats", "reload", "spawn", "remove", "list", "create", "assign", "dungeon");
    private static final List<String> DUNGEON_SUBCOMMANDS = List.of(
            "register", "unregister", "list", "create", "assign", "deleteworld",
            "addregion", "removeregion", "listregions", "forceexit");

    public GateDungeonTabCompleter(GateDungeonPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                String perm = switch (sub) {
                    case "stats" -> "gatedungeon.stats";
                    case "reload" -> "gatedungeon.reload";
                    case "spawn" -> "gatedungeon.gate.spawn";
                    case "remove" -> "gatedungeon.gate.remove";
                    case "list" -> "gatedungeon.gate.list";
                    case "create" -> "gatedungeon.dungeon.create";
                    case "assign" -> "gatedungeon.dungeon.assign";
                    case "dungeon" -> "gatedungeon.dungeon.admin";
                    default -> "gatedungeon.admin";
                };
                boolean permitted = sub.equals("dungeon")
                        ? canUseDungeonCommands(sender)
                        : sender.hasPermission(perm) || sender.hasPermission("gatedungeon.admin");
                if (sub.startsWith(partial) && permitted) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("stats") && sender.hasPermission("gatedungeon.stats.others")) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                        completions.add(player.getName());
                    }
                }
            } else if (first.equals("spawn")) {
                addRanks(completions, args[1]);
            } else if (first.equals("remove")) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                for (Gate gate : plugin.getGateManager().getAllGates()) {
                    if (gate.getId().startsWith(partial)) {
                        completions.add(gate.getId());
                    }
                }
            } else if (first.equals("assign")) {
                addDungeonNames(completions, args[1]);
            } else if (first.equals("dungeon")) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                for (String sub : DUNGEON_SUBCOMMANDS) {
                    if (sub.startsWith(partial) && canUseDungeonSubcommand(sender, sub)) {
                        completions.add(sub);
                    }
                }
            }
        } else if (args.length == 3) {
            String first = args[0].toLowerCase(Locale.ROOT);
            String second = args[1].toLowerCase(Locale.ROOT);
            if (first.equals("assign")) {
                addRanks(completions, args[2]);
            } else if (first.equals("dungeon") && (second.equals("register") || second.equals("assign")
                    || second.equals("addregion") || second.equals("removeregion")
                    || second.equals("listregions") || second.equals("unregister"))) {
                addDungeonNames(completions, args[2]);
            } else if (first.equals("dungeon") && (second.equals("create") || second.equals("deleteworld") || second.equals("forceexit"))) {
                String partial = args[2].toLowerCase(Locale.ROOT);
                if (second.equals("forceexit")) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                            completions.add(p.getName());
                        }
                    }
                } else {
                    for (World world : Bukkit.getWorlds()) {
                        String name = world.getName();
                        if (Worlds.isInstanceWorld(name) || Worlds.isVanillaWorld(name)) continue;
                        if (name.toLowerCase(Locale.ROOT).startsWith(partial)) {
                            completions.add(name);
                        }
                    }
                }
            }
        } else if (args.length == 4) {
            String first = args[0].toLowerCase(Locale.ROOT);
            String second = args[1].toLowerCase(Locale.ROOT);
            if (first.equals("dungeon") && (second.equals("register") || second.equals("assign"))) {
                addRanks(completions, args[3]);
            } else if (first.equals("dungeon") && second.equals("addregion")) {
                String partial = args[3];
                for (int i = 1; i <= 10; i++) {
                    if (String.valueOf(i).startsWith(partial)) {
                        completions.add(String.valueOf(i));
                    }
                }
            } else if (first.equals("dungeon") && second.equals("removeregion")) {
                String templateId = args[2];
                DungeonTemplate template = plugin.getTemplateManager().getTemplate(templateId);
                if (template != null) {
                    String partial = args[3].toLowerCase(Locale.ROOT);
                    for (RegionMarker r : template.getRegions()) {
                        if (r.getId().startsWith(partial)) {
                            completions.add(r.getId());
                        }
                    }
                }
            }
        } else if (args.length == 5) {
            String first = args[0].toLowerCase(Locale.ROOT);
            String second = args[1].toLowerCase(Locale.ROOT);
            if (first.equals("dungeon") && second.equals("register")) {
                String partial = args[4].toLowerCase(Locale.ROOT);
                for (World world : Bukkit.getWorlds()) {
                    if (world.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                        completions.add(world.getName());
                    }
                }
            } else if (first.equals("dungeon") && second.equals("addregion")) {
                String partial = args[4].toUpperCase(Locale.ROOT);
                if ("MOB".startsWith(partial)) completions.add("MOB");
                if ("ELITE".startsWith(partial)) completions.add("ELITE");
            }
        }
        return completions;
    }

    private void addRanks(List<String> completions, String raw) {
        String partial = raw.toUpperCase(Locale.ROOT);
        for (String configuredRank : plugin.getConfigManager().getRanks()) {
            String rank = configuredRank.toUpperCase(Locale.ROOT);
            if (rank.startsWith(partial)) {
                completions.add(rank);
            }
        }
    }

    private boolean canUseDungeonCommands(CommandSender sender) {
        return sender.hasPermission("gatedungeon.admin")
                || sender.hasPermission("gatedungeon.dungeon.admin")
                || sender.hasPermission("gatedungeon.dungeon.register")
                || sender.hasPermission("gatedungeon.dungeon.unregister")
                || sender.hasPermission("gatedungeon.dungeon.create")
                || sender.hasPermission("gatedungeon.dungeon.assign");
    }

    private boolean canUseDungeonSubcommand(CommandSender sender, String subcommand) {
        if (sender.hasPermission("gatedungeon.admin") || sender.hasPermission("gatedungeon.dungeon.admin")) {
            return true;
        }
        return switch (subcommand) {
            case "register", "assign" -> sender.hasPermission("gatedungeon.dungeon.register")
                    || sender.hasPermission("gatedungeon.dungeon.assign");
            case "create" -> sender.hasPermission("gatedungeon.dungeon.create");
            case "unregister" -> sender.hasPermission("gatedungeon.dungeon.unregister");
            default -> false;
        };
    }

    private void addDungeonNames(List<String> completions, String raw) {
        String partial = raw.toLowerCase(Locale.ROOT);
        for (DungeonTemplate t : plugin.getTemplateManager().getAllTemplates()) {
            if (t.getId().toLowerCase(Locale.ROOT).startsWith(partial)) {
                completions.add(t.getId());
            }
        }
        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            if (Worlds.isInstanceWorld(name) || Worlds.isVanillaWorld(name)) continue;
            if (name.toLowerCase(Locale.ROOT).startsWith(partial) && !completions.contains(name)) {
                completions.add(name);
            }
        }
    }
}
