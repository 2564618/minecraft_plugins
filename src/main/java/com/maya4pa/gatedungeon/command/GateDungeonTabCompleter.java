package com.maya4pa.gatedungeon.command;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.gate.Gate;
import com.maya4pa.gatedungeon.template.DungeonTemplate;
import com.maya4pa.gatedungeon.template.RegionMarker;
import com.maya4pa.gatedungeon.util.Constants;
import com.maya4pa.gatedungeon.util.Ranks;
import com.maya4pa.gatedungeon.util.Worlds;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class GateDungeonTabCompleter implements TabCompleter {

    private final GateDungeonPlugin plugin;
    private static final List<String> SUBCOMMANDS = List.of(
            "stats", "reload", "spawn", "remove", "list", "create", "assign", "dungeon");
    private static final List<String> DUNGEON_SUBCOMMANDS = List.of(
            "register", "unregister", "list", "create", "assign", "deleteworld",
            "addregion", "removeregion", "listregions", "forceexit");
    private static final List<String> REGION_TYPES = List.of("MOB", "MOBS", "ELITE");

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
                    case "stats" -> Constants.PERMISSION_STATS;
                    case "reload" -> Constants.PERMISSION_RELOAD;
                    case "spawn" -> Constants.PERMISSION_GATE_SPAWN;
                    case "remove" -> Constants.PERMISSION_GATE_REMOVE;
                    case "list" -> Constants.PERMISSION_GATE_LIST;
                    case "create" -> Constants.PERMISSION_DUNGEON_CREATE;
                    case "assign" -> Constants.PERMISSION_DUNGEON_ASSIGN;
                    case "dungeon" -> Constants.PERMISSION_DUNGEON_ADMIN;
                    default -> Constants.PERMISSION_ADMIN;
                };
                boolean permitted = sub.equals("dungeon")
                        ? canUseDungeonCommands(sender)
                        : sender.hasPermission(perm) || sender.hasPermission(Constants.PERMISSION_ADMIN);
                if (sub.startsWith(partial) && permitted) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("stats") && sender.hasPermission(Constants.PERMISSION_STATS_OTHERS)) {
                addPlayers(completions, args[1]);
            } else if (first.equals("spawn")) {
                addRanks(completions, args[1]);
            } else if (first.equals("remove")) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                for (Gate gate : plugin.getGateManager().getAllGates()) {
                    if (gate.getId().startsWith(partial)) {
                        completions.add(gate.getId());
                    }
                }
            } else if (first.equals("assign") || first.equals("create")) {
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
                    addPlayers(completions, args[2]);
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
            if (first.equals("assign")) {
                addRemainingRanks(completions, args, 2);
            } else if (first.equals("dungeon") && (second.equals("register") || second.equals("assign"))) {
                addRanks(completions, args[3]);
            } else if (first.equals("dungeon") && second.equals("addregion")) {
                addWaves(completions, args[2], args[3]);
            } else if (first.equals("dungeon") && second.equals("removeregion")) {
                addRegionIds(completions, args[2], args[3]);
            }
        } else if (args.length == 5) {
            String first = args[0].toLowerCase(Locale.ROOT);
            String second = args[1].toLowerCase(Locale.ROOT);
            if (first.equals("assign")) {
                addRemainingRanks(completions, args, 2);
            } else if (first.equals("dungeon") && second.equals("assign")) {
                addRemainingRanks(completions, args, 3);
            } else if (first.equals("dungeon") && second.equals("register")) {
                String partial = args[4].toLowerCase(Locale.ROOT);
                for (World world : Bukkit.getWorlds()) {
                    if (world.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                        completions.add(world.getName());
                    }
                }
            } else if (first.equals("dungeon") && second.equals("addregion")) {
                addRegionTypes(completions, args[4]);
            }
        } else if (args.length > 5) {
            String first = args[0].toLowerCase(Locale.ROOT);
            String second = args[1].toLowerCase(Locale.ROOT);
            if (first.equals("assign")) {
                addRemainingRanks(completions, args, 2);
            } else if (first.equals("dungeon") && second.equals("assign")) {
                addRemainingRanks(completions, args, 3);
            }
        }
        return completions;
    }

    private void addPlayers(List<String> completions, String raw) {
        String partial = raw.toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                completions.add(player.getName());
            }
        }
    }

    private void addRanks(List<String> completions, String raw) {
        String prefix;
        String last;
        int lastComma = raw.lastIndexOf(',');
        if (lastComma >= 0) {
            prefix = raw.substring(0, lastComma + 1);
            last = raw.substring(lastComma + 1);
        } else {
            prefix = "";
            last = raw;
        }
        Set<String> already = new LinkedHashSet<>(Ranks.parse(prefix));
        String partial = last.trim().toUpperCase(Locale.ROOT);
        for (String configuredRank : plugin.getConfigManager().getRanks()) {
            String rank = configuredRank.toUpperCase(Locale.ROOT);
            if (already.contains(rank)) continue;
            if (rank.startsWith(partial)) {
                completions.add(prefix + rank);
            }
        }
    }

    private void addRemainingRanks(List<String> completions, String[] args, int rankStart) {
        Set<String> already = new LinkedHashSet<>();
        for (int i = rankStart; i < args.length - 1; i++) {
            already.addAll(Ranks.parse(args[i]));
        }
        String current = args[args.length - 1];
        String prefix;
        String last;
        int lastComma = current.lastIndexOf(',');
        if (lastComma >= 0) {
            prefix = current.substring(0, lastComma + 1);
            last = current.substring(lastComma + 1);
            already.addAll(Ranks.parse(prefix));
        } else {
            prefix = "";
            last = current;
        }
        String partial = last.trim().toUpperCase(Locale.ROOT);
        for (String configuredRank : plugin.getConfigManager().getRanks()) {
            String rank = configuredRank.toUpperCase(Locale.ROOT);
            if (already.contains(rank)) continue;
            if (rank.startsWith(partial)) {
                completions.add(prefix + rank);
            }
        }
    }

    private void addWaves(List<String> completions, String templateId, String raw) {
        DungeonTemplate template = plugin.getTemplateManager().getTemplate(templateId);
        Set<String> waves = new LinkedHashSet<>();
        int max = 10;
        if (template != null) {
            max = Math.max(10, template.getMaxWave() + 1);
            for (Integer wave : template.getWaves()) {
                waves.add(String.valueOf(wave));
            }
        }
        for (int i = 1; i <= max; i++) {
            waves.add(String.valueOf(i));
        }
        String partial = raw == null ? "" : raw;
        for (String wave : waves) {
            if (wave.startsWith(partial)) completions.add(wave);
        }
    }

    private void addRegionIds(List<String> completions, String templateId, String raw) {
        DungeonTemplate template = plugin.getTemplateManager().getTemplate(templateId);
        if (template == null) return;
        String partial = raw.toLowerCase(Locale.ROOT);
        for (RegionMarker region : template.getRegions()) {
            if (region.getId().toLowerCase(Locale.ROOT).startsWith(partial)) {
                completions.add(region.getId());
            }
        }
    }

    private void addRegionTypes(List<String> completions, String raw) {
        String partial = raw.toUpperCase(Locale.ROOT);
        for (String type : REGION_TYPES) {
            if (type.startsWith(partial)) completions.add(type);
        }
    }

    private boolean canUseDungeonCommands(CommandSender sender) {
        return sender.hasPermission(Constants.PERMISSION_ADMIN)
                || sender.hasPermission(Constants.PERMISSION_DUNGEON_ADMIN)
                || sender.hasPermission(Constants.PERMISSION_DUNGEON_REGISTER)
                || sender.hasPermission(Constants.PERMISSION_DUNGEON_UNREGISTER)
                || sender.hasPermission(Constants.PERMISSION_DUNGEON_CREATE)
                || sender.hasPermission(Constants.PERMISSION_DUNGEON_ASSIGN);
    }

    private boolean canUseDungeonSubcommand(CommandSender sender, String subcommand) {
        if (sender.hasPermission(Constants.PERMISSION_ADMIN)
                || sender.hasPermission(Constants.PERMISSION_DUNGEON_ADMIN)) {
            return true;
        }
        return switch (subcommand) {
            case "register", "assign" -> sender.hasPermission(Constants.PERMISSION_DUNGEON_REGISTER)
                    || sender.hasPermission(Constants.PERMISSION_DUNGEON_ASSIGN);
            case "create" -> sender.hasPermission(Constants.PERMISSION_DUNGEON_CREATE);
            case "unregister" -> sender.hasPermission(Constants.PERMISSION_DUNGEON_UNREGISTER);
            case "addregion", "removeregion", "listregions" ->
                    sender.hasPermission(Constants.PERMISSION_DUNGEON_CREATE)
                            || sender.hasPermission(Constants.PERMISSION_DUNGEON_ASSIGN);
            default -> false;
        };
    }

    private void addDungeonNames(List<String> completions, String raw) {
        String partial = raw.toLowerCase(Locale.ROOT);
        for (DungeonTemplate template : plugin.getTemplateManager().getAllTemplates()) {
            if (template.getId().toLowerCase(Locale.ROOT).startsWith(partial)) {
                completions.add(template.getId());
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
