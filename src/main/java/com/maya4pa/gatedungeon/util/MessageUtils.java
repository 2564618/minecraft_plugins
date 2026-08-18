package com.maya4pa.gatedungeon.util;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

public class MessageUtils {
    private static GateDungeonPlugin plugin;
    private static YamlConfiguration messages;

    public static void init(GateDungeonPlugin p) {
        plugin = p;
        File file = new File(plugin.getDataFolder(), "messages.yml");
        messages = YamlConfiguration.loadConfiguration(file);
    }

public static String get(String key, Object... replacements) {
        if (messages == null) {
            return ChatColor.RED + key;
        }
        String msg = messages.getString(key, "&cMissing message: " + key);
        String prefix = messages.getString("prefix", "&8[&6GateDungeon&8] ");
        msg = prefix + msg;
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                msg = msg.replace("{" + replacements[i] + "}", String.valueOf(replacements[i+1]));
            }
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public static void send(CommandSender sender, String key, Object... replacements) {
        sender.sendMessage(get(key, replacements));
    }
}