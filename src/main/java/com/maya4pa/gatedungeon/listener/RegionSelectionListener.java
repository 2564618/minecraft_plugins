package com.maya4pa.gatedungeon.listener;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.util.Constants;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RegionSelectionListener implements Listener {
    private static final int RAY_DISTANCE = 8;

    private final GateDungeonPlugin plugin;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public RegionSelectionListener(GateDungeonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!canSelect(player) || !isSelectionTool(player.getInventory().getItemInMainHand())) return;

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.RIGHT_CLICK_AIR) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            clicked = rayTraceBlock(player);
        }
        if (clicked == null) return;

        Location loc = clicked.getLocation();
        boolean firstPoint = action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR;
        if (firstPoint) {
            pos1.put(player.getUniqueId(), loc);
            player.sendMessage("§aPos 1 set at " + format(loc));
        } else {
            pos2.put(player.getUniqueId(), loc);
            player.sendMessage("§aPos 2 set at " + format(loc));
        }
        sendSelectionSummary(player);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!canSelect(player) || !isSelectionTool(player.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearSelection(event.getPlayer());
    }

    public Location getPos1(Player player) {
        Location location = pos1.get(player.getUniqueId());
        return location == null ? null : location.clone();
    }

    public Location getPos2(Player player) {
        Location location = pos2.get(player.getUniqueId());
        return location == null ? null : location.clone();
    }

    public boolean hasCompleteSelection(Player player) {
        Location first = pos1.get(player.getUniqueId());
        Location second = pos2.get(player.getUniqueId());
        return first != null && second != null
                && first.getWorld() != null && second.getWorld() != null
                && first.getWorld().equals(second.getWorld());
    }

    public void clearSelection(Player player) {
        UUID playerId = player.getUniqueId();
        pos1.remove(playerId);
        pos2.remove(playerId);
    }

    private boolean canSelect(Player player) {
        return player.hasPermission(Constants.PERMISSION_ADMIN)
                || player.hasPermission(Constants.PERMISSION_DUNGEON_ADMIN)
                || player.hasPermission(Constants.PERMISSION_DUNGEON_CREATE)
                || player.hasPermission(Constants.PERMISSION_DUNGEON_ASSIGN);
    }

    private boolean isSelectionTool(ItemStack item) {
        return plugin.getConfigManager().isDebugTool(item);
    }

    private Block rayTraceBlock(Player player) {
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                RAY_DISTANCE,
                FluidCollisionMode.NEVER,
                false);
        return result == null ? null : result.getHitBlock();
    }

    private void sendSelectionSummary(Player player) {
        Location first = getPos1(player);
        Location second = getPos2(player);
        if (first == null || second == null) return;
        if (first.getWorld() == null || second.getWorld() == null
                || !first.getWorld().equals(second.getWorld())) {
            player.sendMessage("§cBoth points must be in the same world.");
            return;
        }
        int dx = Math.abs(first.getBlockX() - second.getBlockX()) + 1;
        int dy = Math.abs(first.getBlockY() - second.getBlockY()) + 1;
        int dz = Math.abs(first.getBlockZ() - second.getBlockZ()) + 1;
        player.sendMessage("§7Selection: §e" + dx + "x" + dy + "x" + dz
                + " §7— /gd dungeon addregion <template> <wave> MOB");
    }

    private static String format(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}
