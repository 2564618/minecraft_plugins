package com.maya4pa.gatedungeon.instance;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.gate.Gate;
import com.maya4pa.gatedungeon.template.DungeonTemplate;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InstanceManager {
    private final GateDungeonPlugin plugin;
    private final Map<String, DungeonInstance> instances = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerInstanceMap = new ConcurrentHashMap<>();

    public InstanceManager(GateDungeonPlugin plugin) {
        this.plugin = plugin;
    }

    public DungeonInstance createInstance(Gate gate, Player player) {
        if (playerInstanceMap.containsKey(player.getUniqueId())) {
            player.sendMessage("§cYou are already in a dungeon!");
            return null;
        }

        for (DungeonInstance instance : instances.values()) {
            if (instance.getGate().getId().equals(gate.getId())
                    && instance.getState() != DungeonInstance.State.DESTROYED
                    && instance.getState() != DungeonInstance.State.CLEANUP
                    && instance.getState() != DungeonInstance.State.COMPLETED) {
                if (!gate.canEnter()) {
                    player.sendMessage("§cThis gate is full.");
                    return null;
                }
                playerInstanceMap.put(player.getUniqueId(), instance.getId());
                instance.beginEntry(player);
                return instance;
            }
        }

        if (instances.size() >= plugin.getConfigManager().getMaxActiveInstances()) {
            player.sendMessage("§cToo many active instances! Try again later.");
            return null;
        }

        DungeonTemplate template = null;
        String templateId = gate.getSelectedTemplateId();
        if (templateId != null) {
            template = plugin.getTemplateManager().getTemplate(templateId);
        }
        if (template == null) {
            template = plugin.getTemplateManager().selectRandomTemplate(gate.getRank());
            if (template != null) {
                gate.setSelectedTemplateId(template.getId());
            }
        }
        if (template == null) {
            player.sendMessage("§cNo dungeon template available for rank " + gate.getRank());
            return null;
        }

        plugin.getLogger().info("Selected template '" + template.getId() + "' for " + gate.getRank() + "-rank gate " + gate.getId());
        player.sendTitle("§6✦ Opening the Gate", "§eBuilding instance — stay put...", 0, 200, 10);
        player.sendMessage("§ePreparing dungeon... you will teleport when the world is fully loaded.");
        String worldName = plugin.getWorldManager().nextInstanceWorldName();
        World world = plugin.getWorldManager().createInstanceWorld(worldName, template);
        if (world == null) {
            player.sendMessage("§cFailed to create instance world!");
            player.resetTitle();
            return null;
        }

        String instanceId = UUID.randomUUID().toString().substring(0, 8);
        DungeonInstance instance = new DungeonInstance(instanceId, template, gate, player, world);
        instances.put(instanceId, instance);
        playerInstanceMap.put(player.getUniqueId(), instanceId);
        instance.beginEntry(player);
        return instance;
    }

    /** Called by DungeonInstance.cleanup — does not recurse back into cleanup. */
    public void unregisterInstance(String id) {
        DungeonInstance instance = instances.remove(id);
        if (instance != null) {
            for (UUID uuid : instance.getPlayers()) {
                playerInstanceMap.remove(uuid);
            }
        }
    }

    public void removeInstance(String id) {
        DungeonInstance instance = instances.get(id);
        if (instance != null) {
            instance.cleanup();
        }
    }

    public void removePlayerFromInstance(UUID playerId) {
        playerInstanceMap.remove(playerId);
    }

    public DungeonInstance getPlayerInstance(Player player) {
        String id = playerInstanceMap.get(player.getUniqueId());
        if (id == null) return null;
        DungeonInstance inst = instances.get(id);
        if (inst == null) {
            playerInstanceMap.remove(player.getUniqueId());
            return null;
        }
        return inst;
    }

    public void startCleanupTask() {
        int interval = Math.max(10, plugin.getConfigManager().getCleanupInterval());
        new BukkitRunnable() {
            @Override
            public void run() {
                for (DungeonInstance inst : new ArrayList<>(instances.values())) {
                    if (inst.getState() == DungeonInstance.State.DESTROYED) {
                        instances.remove(inst.getId());
                    } else if (inst.getPlayers().isEmpty()
                            && inst.getState() != DungeonInstance.State.CREATING
                            && inst.getState() != DungeonInstance.State.PREPARING) {
                        inst.cleanup();
                    }
                }
            }
        }.runTaskTimer(plugin, interval * 20L, interval * 20L);
    }

    public void cleanupAll() {
        for (DungeonInstance inst : new ArrayList<>(instances.values())) {
            inst.cleanup();
        }
        instances.clear();
        playerInstanceMap.clear();
    }

    public Map<String, DungeonInstance> getActiveInstances() {
        return new ConcurrentHashMap<>(instances);
    }
}
