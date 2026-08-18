package com.maya4pa.gatedungeon.hologram;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

public class HologramManager {
    private final GateDungeonPlugin plugin;

    public HologramManager(GateDungeonPlugin plugin) {
        this.plugin = plugin;
    }

    public ArmorStand createHologram(Location location, String text) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setCustomNameVisible(true);
        updateHologram(stand, text);
        return stand;
    }

    public void updateHologram(ArmorStand hologram, String text) {
        if (hologram != null && !hologram.isDead()) {
            hologram.setCustomName(org.bukkit.ChatColor.translateAlternateColorCodes('&', text));
        }
    }

    public void removeHologram(ArmorStand hologram) {
        if (hologram != null && !hologram.isDead()) {
            hologram.remove();
        }
    }

    public void cleanup() {
        // holograms are managed by GateVisualizer; no action needed here
    }
}