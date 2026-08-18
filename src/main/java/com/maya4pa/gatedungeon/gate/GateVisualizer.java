package com.maya4pa.gatedungeon.gate;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GateVisualizer {
    private final GateDungeonPlugin plugin;
    private final Map<String, BukkitRunnable> tasks = new ConcurrentHashMap<>();
    private final Map<String, ArmorStand> holograms = new ConcurrentHashMap<>();
    private final Map<String, Particle.DustOptions> rankColors = new ConcurrentHashMap<>();

    public GateVisualizer(GateDungeonPlugin plugin) {
        this.plugin = plugin;
        reloadColors();
    }

    public void reloadColors() {
        rankColors.clear();
        for (String rank : plugin.getConfigManager().getRanks()) {
            String colorName = plugin.getConfigManager().getRankColor(rank);
            rankColors.put(rank.toUpperCase(), new Particle.DustOptions(parseColor(colorName), 1.0f));
        }
    }

    private Color parseColor(String colorCode) {
        if (colorCode == null) return Color.WHITE;
        return switch (colorCode.replace("&", "").toLowerCase()) {
            case "7" -> Color.GRAY;
            case "a" -> Color.LIME;
            case "9" -> Color.BLUE;
            case "5" -> Color.PURPLE;
            case "c" -> Color.RED;
            case "6" -> Color.ORANGE;
            default -> Color.WHITE;
        };
    }

    public void spawn(Gate gate) {
        despawn(gate);
        Location loc = gate.getLocation();
        if (loc.getWorld() == null) return;
        loc = loc.clone().add(0.5, 0, 0.5);

        ArmorStand hologram = createHologram(loc.clone().add(0, 2.2, 0), gate);
        holograms.put(gate.getId(), hologram);

        final Location center = loc;
        BukkitRunnable task = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!gate.isActive() || center.getWorld() == null) {
                    cancel();
                    return;
                }
                renderParticles(center, gate.getRank(), tick);
                if (tick % 20 == 0) {
                    updateHologram(holograms.get(gate.getId()), gate);
                }
                tick++;
            }
        };
        task.runTaskTimer(plugin, 0L, 2L);
        tasks.put(gate.getId(), task);
    }

    private String buildHologramText(Gate gate) {
        String rank = gate.getRank();
        String color = plugin.getConfigManager().getRankColor(rank);
        String status = gate.isClosing() ? "&cCLOSING" : (gate.isActive() ? "&aACTIVE" : "&7INACTIVE");
        int expirationMinutes = plugin.getConfigManager().getGateExpirationMinutes();
        long left = gate.getTimeLeft(expirationMinutes);
        String time = expirationMinutes < 0 ? "∞" : formatTime(left);
        return color + rank + "-Rank Gate &7| " + gate.getPlayersInside() + "/" + gate.getMaxPlayers()
                + " &7| " + time + " " + status;
    }

    private String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private void renderParticles(Location center, String rank, int tick) {
        World world = center.getWorld();
        if (world == null) return;
        Particle.DustOptions color = rankColors.getOrDefault(
                rank.toUpperCase(), new Particle.DustOptions(Color.WHITE, 1));
        int radius = switch (rank.toUpperCase()) {
            case "S" -> 5;
            case "A", "B" -> 4;
            case "C" -> 3;
            default -> 2;
        };
        boolean reduced = plugin.getConfigManager().isReducedEffects();
        // Reuse one DustOptions per beam instead of allocating one per particle.
        Particle.DustOptions beamColor = new Particle.DustOptions(color.getColor(), 0.6f);

        int particles = radius * 8;
        for (int i = 0; i < particles; i++) {
            double angle = 2 * Math.PI * i / particles;
            double x = radius * Math.cos(angle + tick * 0.02);
            double z = radius * Math.sin(angle + tick * 0.02);
            world.spawnParticle(Particle.DUST, center.clone().add(x, 0.1, z), 1, 0, 0, 0, 0, color);
        }

        if (!reduced) {
            for (int i = 0; i < 8; i++) {
                double angle = 2 * Math.PI * i / 8 + tick * 0.05;
                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);
                for (double y = 0; y <= 3; y += 0.6) {
                    world.spawnParticle(Particle.DUST, center.clone().add(x, y, z), 1, 0, 0, 0, 0, beamColor);
                }
            }
        }
    }

    private ArmorStand createHologram(Location location, Gate gate) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setCustomNameVisible(true);
        stand.setPersistent(false);
        updateHologram(stand, gate);
        return stand;
    }

    private void updateHologram(ArmorStand stand, Gate gate) {
        if (stand == null || stand.isDead()) return;
        stand.setCustomName(ChatColor.translateAlternateColorCodes('&', buildHologramText(gate)));
    }

    public void despawn(Gate gate) {
        BukkitRunnable task = tasks.remove(gate.getId());
        if (task != null) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
        ArmorStand holo = holograms.remove(gate.getId());
        if (holo != null && !holo.isDead()) holo.remove();
    }

    public void removeAll() {
        for (String id : new ArrayList<>(tasks.keySet())) {
            BukkitRunnable task = tasks.remove(id);
            if (task != null) {
                try { task.cancel(); } catch (Exception ignored) {}
            }
        }
        for (ArmorStand holo : holograms.values()) {
            if (holo != null && !holo.isDead()) holo.remove();
        }
        holograms.clear();
    }
}
