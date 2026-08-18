package com.maya4pa.gatedungeon.listener;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.gate.Gate;
import com.maya4pa.gatedungeon.instance.DungeonInstance;
import com.maya4pa.gatedungeon.util.Constants;
import com.maya4pa.gatedungeon.util.Worlds;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GateInteractionListener implements Listener {
    private final GateDungeonPlugin plugin;
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public GateInteractionListener(GateDungeonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        // Ignore look-only movement
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isOnline() || player.isDead()) return;

        DungeonInstance current = plugin.getInstanceManager().getPlayerInstance(player);
        if (current != null) {
            if (to.getWorld() != null && Worlds.isInstanceWorld(to.getWorld().getName())
                    && to.getY() < to.getWorld().getMinHeight() + 8) {
                current.rescueToEntrance(player);
            }
            return;
        }
        String worldName = to.getWorld() != null ? to.getWorld().getName() : "";
        if (Worlds.isInstanceWorld(worldName)) {
            return;
        }

        Gate gate = plugin.getGateManager().getGateAtLocation(to);
        if (gate == null) return;

        if (!gate.canEnter()) {
            if (gate.isClosing()) {
                player.sendTitle("", "§cThis gate is closing!",
                        Constants.TITLE_FADE_IN, Constants.TITLE_FADE_STAY, Constants.TITLE_FADE_OUT);
            } else if (gate.getPlayersInside() >= gate.getMaxPlayers()) {
                player.sendTitle("", "§cGate is full!",
                        Constants.TITLE_FADE_IN, Constants.TITLE_FADE_STAY, Constants.TITLE_FADE_OUT);
            }
            return;
        }

        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        Long last = cooldowns.get(playerId);
        if (last != null && now - last < Constants.PLAYER_INTERACTION_COOLDOWN) {
            return;
        }
        cooldowns.put(playerId, now);
        plugin.getInstanceManager().createInstance(gate, player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        DungeonInstance inst = plugin.getInstanceManager().getPlayerInstance(player);
        if (inst != null) {
            inst.teleportOut(player);
        }
        cooldowns.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DungeonInstance inst = plugin.getInstanceManager().getPlayerInstance(player);
        if (inst != null) {
            inst.handleDeath(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        DungeonInstance inst = plugin.getInstanceManager().getPlayerInstance(player);
        if (inst != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    inst.teleportOut(player);
                }
            });
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        // Prevent accidental re-entry spam after leaving
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            cooldowns.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        DungeonInstance inst = plugin.getInstanceManager().getPlayerInstance(p);
        if (inst == null) return;
        DungeonInstance.State state = inst.getState();
        if (state == DungeonInstance.State.COMPLETED
                || state == DungeonInstance.State.CREATING
                || state == DungeonInstance.State.PREPARING) {
            event.setCancelled(true);
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID
                || cause == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            if (cause == EntityDamageEvent.DamageCause.VOID) {
                inst.rescueToEntrance(p);
            }
        }
    }
}
