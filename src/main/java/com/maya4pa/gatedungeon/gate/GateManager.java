package com.maya4pa.gatedungeon.gate;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.util.Constants;
import com.maya4pa.gatedungeon.util.Worlds;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GateManager {
    private final GateDungeonPlugin plugin;
    private final Map<String, Gate> gates = new ConcurrentHashMap<>();
    private final GateVisualizer visualizer;

    public GateManager(GateDungeonPlugin plugin) {
        this.plugin = plugin;
        this.visualizer = new GateVisualizer(plugin);
    }

    public void loadFromDatabase() {
        visualizer.removeAll();
        gates.clear();

        List<Gate> loaded = plugin.getDatabaseManager().loadAllGates();
        int maxPlayers = plugin.getConfigManager().getMaxPlayersPerGate();
        int expirationMinutes = plugin.getConfigManager().getGateExpirationMinutes();

        for (Gate gate : loaded) {
            if (expirationMinutes > 0 && gate.isExpired(expirationMinutes)) {
                plugin.getLogger().info("Gate " + gate.getId() + " expired – removing from database.");
                plugin.getDatabaseManager().deleteGate(gate.getId());
                continue;
            }

            gate.setMaxPlayers(maxPlayers);
            gates.put(gate.getId(), gate);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (gates.containsKey(gate.getId())) {
                    visualizer.spawn(gate);
                }
            }, Constants.TASK_DELAY_MEDIUM);

            scheduleAutoRemove(gate.getId());
        }
        plugin.getLogger().info("Loaded " + gates.size() + " gates.");
    }

    public Gate createGate(Location location, String rank, Player creator) {
        if (location == null || location.getWorld() == null || rank == null || creator == null || !creator.isOnline()) {
            return null;
        }
        if (!plugin.getConfigManager().isValidRank(rank)) {
            return null;
        }

        int minDist = plugin.getConfigManager().getMinDistanceBetweenGates();
        if (minDist > 0 && isTooClose(location, minDist)) {
            return null;
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        Gate gate = new Gate(id, location, rank.toUpperCase(), creator.getUniqueId(), System.currentTimeMillis());
        gate.setMaxPlayers(plugin.getConfigManager().getMaxPlayersPerGate());
        gates.put(id, gate);
        plugin.getDatabaseManager().saveGate(gate);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (gates.containsKey(id)) {
                visualizer.spawn(gate);
            }
        }, Constants.TASK_DELAY_MEDIUM);

        scheduleAutoRemove(id);
        return gate;
    }

    private boolean isTooClose(Location location, int minDist) {
        for (Gate existing : gates.values()) {
            double d = Worlds.distanceSquared(existing.getLocation(), location);
            if (d < (double) minDist * minDist) {
                return true;
            }
        }
        return false;
    }

    private void scheduleAutoRemove(String id) {
        int autoRemove = plugin.getConfigManager().getAutoRemoveTimer();
        if (autoRemove <= 0) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Gate currentGate = gates.get(id);
            if (currentGate != null && currentGate.isActive() && !currentGate.isClosing()
                    && currentGate.getPlayersInside() == 0) {
                removeGate(id);
            }
        }, autoRemove * 20L);
    }

    public boolean removeGate(String id) {
        Gate gate = gates.remove(id);
        if (gate == null) {
            return false;
        }
        visualizer.despawn(gate);
        plugin.getDatabaseManager().deleteGate(id);
        return true;
    }

    public Gate getGate(String id) {
        return gates.get(id);
    }

    public Collection<Gate> getAllGates() {
        return Collections.unmodifiableCollection(gates.values());
    }

    public Gate getGateAtLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        String worldName = location.getWorld().getName();
        if (Worlds.isInstanceWorld(worldName)) {
            return null;
        }
        double radiusSq = Constants.GATE_ENTER_RADIUS * Constants.GATE_ENTER_RADIUS;
        Gate closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Gate gate : gates.values()) {
            if (gate.getWorldName() != null && !gate.getWorldName().isEmpty()
                    && !gate.getWorldName().equals(worldName)) {
                continue;
            }
            double d = Worlds.distanceSquared(gate.getLocation(), location);
            if (d <= radiusSq && d < closestDist) {
                closest = gate;
                closestDist = d;
            }
        }
        return closest;
    }

    public void saveAll() {
        for (Gate gate : gates.values()) {
            plugin.getDatabaseManager().saveGate(gate);
        }
    }

    public GateVisualizer getVisualizer() {
        return visualizer;
    }

    public void onDisable() {
        visualizer.removeAll();
        saveAll();
    }
}
