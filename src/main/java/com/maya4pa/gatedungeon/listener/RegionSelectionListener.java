package com.maya4pa.gatedungeon.listener;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RegionSelectionListener implements Listener {
    private final GateDungeonPlugin plugin;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    private static final Material TOOL = Material.STICK;

    public RegionSelectionListener(GateDungeonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("gatedungeon.dungeon.admin")) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != TOOL) return;

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            // Left click = do nothing (maybe clear?)
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            Location loc = event.getClickedBlock().getLocation();
            if (player.isSneaking()) {
                pos2.put(player.getUniqueId(), loc);
                player.sendMessage("§aSecond point set at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                event.setCancelled(true);
            } else {
                pos1.put(player.getUniqueId(), loc);
                player.sendMessage("§aFirst point set at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                event.setCancelled(true);
            }
        }
    }

    public Location getPos1(Player player) {
        return pos1.get(player.getUniqueId());
    }

    public Location getPos2(Player player) {
        return pos2.get(player.getUniqueId());
    }

    public void clearSelection(Player player) {
        pos1.remove(player.getUniqueId());
        pos2.remove(player.getUniqueId());
    }
}