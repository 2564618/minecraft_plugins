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
import java.util.Arrays;
import java.util.List;

public class GateDungeonTabCompleter implements TabCompleter {

    private final GateDungeonPlugin plugin;
    private final List<String> SUBCOMMANDS = Arrays.asList("reload", "spawn", "remove", "list", "create", "assign", "dungeon");
    private final List<String> DUNGEON_SUBCOMMANDS = Arrays.asList(
            "register", "unregister", "list", "create", "assign", "deleteworld",
            "addregion", "removeregion", "listregions", "forceexit");
    private final List<String> RANKS = Arrays.asList("E", "D", "C", "B", "A", "S");

    public GateDungeonTabCompleter(GateDungeonPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : SUBCOMMANDS) {
                String perm = switch (sub) {
                    case "reload" -> "gatedungeon.reload";
                    case "spawn" -> "gatedungeon.gate.spawn";
                    case "remove" -> "gatedungeon.gate.remove";
                    case "list" -> "gatedungeon.gate.list";
                    case "create" -> "gatedungeon.dungeon.create";
                    case "assign" -> "gatedungeon.dungeon.assign";
                    case "dungeon" -> "gatedungeon.admin";
                    default -> "gatedungeon.admin";
                };
                if (sub.startsWith(partial) && (sender.hasPermission(perm) || sender.hasPermission("gatedungeon.admin"))) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String first = args[0].toLowerCase();
            if (first.equals("spawn")) {
                addRanks(completions, args[1]);
            } else if (first.equals("remove")) {
                String partial = args[1].toLowerCase();
                for (Gate gate : plugin.getGateManager().getAllGates()) {
                    if (gate.getId().startsWith(partial)) {
                        completions.add(gate.getId());
                    }
                }
            } else if (first.equals("assign")) {
                addDungeonNames(completions, args[1]);
            } else if (first.equals("dungeon")) {
                String partial = args[1].toLowerCase();
                for (String sub : DUNGEON_SUBCOMMANDS) {
                    if (sub.startsWith(partial) && (sender.hasPermission("gatedungeon.dungeon.admin")
                            || sender.hasPermission("gatedungeon.dungeon." + sub)
                            || sender.hasPermission("gatedungeon.admin"))) {
                        completions.add(sub);
                    }
                }
            }
        } else if (args.length == 3) {
            String first = args[0].toLowerCase();
            String second = args[1].toLowerCase();
            if (first.equals("assign")) {
                addRanks(completions, args[2]);
            } else if (first.equals("dungeon") && (second.equals("register") || second.equals("assign")
                    || second.equals("addregion") || second.equals("removeregion")
                    || second.equals("listregions") || second.equals("unregister"))) {
                addDungeonNames(completions, args[2]);
            } else if (first.equals("dungeon") && (second.equals("create") || second.equals("deleteworld") || second.equals("forceexit"))) {
                String partial = args[2].toLowerCase();
                if (second.equals("forceexit")) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(partial)) {
                            completions.add(p.getName());
                        }
                    }
                } else {
                    for (World world : Bukkit.getWorlds()) {
                        String name = world.getName();
                        if (Worlds.isInstanceWorld(name) || Worlds.isVanillaWorld(name)) continue;
                        if (name.toLowerCase().startsWith(partial)) {
                            completions.add(name);
                        }
                    }
                }
            }
        } else if (args.length == 4) {
            String first = args[0].toLowerCase();
            String second = args[1].toLowerCase();
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
                    String partial = args[3].toLowerCase();
                    for (RegionMarker r : template.getRegions()) {
                        if (r.getId().startsWith(partial)) {
                            completions.add(r.getId());
                        }
                    }
                }
            }
        } else if (args.length == 5) {
            String first = args[0].toLowerCase();
            String second = args[1].toLowerCase();
            if (first.equals("dungeon") && second.equals("register")) {
                String partial = args[4].toLowerCase();
                for (World world : Bukkit.getWorlds()) {
                    if (world.getName().toLowerCase().startsWith(partial)) {
                        completions.add(world.getName());
                    }
                }
            } else if (first.equals("dungeon") && second.equals("addregion")) {
                String partial = args[4].toUpperCase();
                if ("MOB".startsWith(partial)) completions.add("MOB");
                if ("ELITE".startsWith(partial)) completions.add("ELITE");
            }
        }
        return completions;
    }

    private void addRanks(List<String> completions, String raw) {
        String partial = raw.toUpperCase();
        for (String rank : RANKS) {
            if (rank.startsWith(partial)) {
                completions.add(rank);
            }
        }
    }

    private void addDungeonNames(List<String> completions, String raw) {
        String partial = raw.toLowerCase();
        for (DungeonTemplate t : plugin.getTemplateManager().getAllTemplates()) {
            if (t.getId().toLowerCase().startsWith(partial)) {
                completions.add(t.getId());
            }
        }
        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            if (Worlds.isInstanceWorld(name) || Worlds.isVanillaWorld(name)) continue;
            if (name.toLowerCase().startsWith(partial) && !completions.contains(name)) {
                completions.add(name);
            }
        }
    }
}
