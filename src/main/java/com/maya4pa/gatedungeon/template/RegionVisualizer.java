package com.maya4pa.gatedungeon.template;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.instance.DungeonInstance;
import com.maya4pa.gatedungeon.listener.RegionSelectionListener;
import com.maya4pa.gatedungeon.util.Constants;
import com.maya4pa.gatedungeon.util.RegionTypes;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Shows wave-region outlines only while an admin/builder is holding a debug tool.
 */
public class RegionVisualizer {
    private static final double VIEW_DISTANCE = 64.0;
    private static final double VIEW_DISTANCE_SQ = VIEW_DISTANCE * VIEW_DISTANCE;

    private final GateDungeonPlugin plugin;
    private BukkitTask task;

    public RegionVisualizer(GateDungeonPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 8L);
    }

    public void stop() {
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
            task = null;
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canView(player) || !isHoldingDebugTool(player)) continue;
            renderFor(player);
        }
    }

    private boolean canView(Player player) {
        return player.hasPermission(Constants.PERMISSION_ADMIN)
                || player.hasPermission(Constants.PERMISSION_DUNGEON_ADMIN)
                || player.hasPermission(Constants.PERMISSION_DUNGEON_CREATE)
                || player.hasPermission(Constants.PERMISSION_DUNGEON_ASSIGN);
    }

    private boolean isHoldingDebugTool(Player player) {
        return isDebugTool(player.getInventory().getItemInMainHand())
                || isDebugTool(player.getInventory().getItemInOffHand());
    }

    private boolean isDebugTool(ItemStack item) {
        return plugin.getConfigManager().isDebugTool(item);
    }

    private void renderFor(Player player) {
        World world = player.getWorld();
        if (world == null) return;

        DungeonTemplate template = resolveTemplate(player, world);
        RegionSelectionListener selection = plugin.getRegionSelectionListener();
        Location pos1 = selection.getPos1(player);
        Location pos2 = selection.getPos2(player);

        if (pos1 != null && pos2 != null && world.equals(pos1.getWorld()) && world.equals(pos2.getWorld())) {
            drawBox(player,
                    Math.min(pos1.getBlockX(), pos2.getBlockX()),
                    Math.min(pos1.getBlockY(), pos2.getBlockY()),
                    Math.min(pos1.getBlockZ(), pos2.getBlockZ()),
                    Math.max(pos1.getBlockX(), pos2.getBlockX()),
                    Math.max(pos1.getBlockY(), pos2.getBlockY()),
                    Math.max(pos1.getBlockZ(), pos2.getBlockZ()),
                    Color.YELLOW, 1.15f);
        } else if (pos1 != null && world.equals(pos1.getWorld())) {
            drawBox(player,
                    pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
                    pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
                    Color.LIME, 1.2f);
        }

        if (template == null) {
            if (pos1 != null || pos2 != null) {
                player.sendActionBar(ChatColor.YELLOW + "Selection ready — add it with /gd dungeon addregion <id> <wave> MOB");
            }
            return;
        }

        Location playerLoc = player.getLocation();
        RegionMarker nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (RegionMarker region : template.getRegions()) {
            if (region.distanceSquared(playerLoc.getBlockX(), playerLoc.getBlockY(), playerLoc.getBlockZ()) > VIEW_DISTANCE_SQ) {
                continue;
            }
            drawBox(player,
                    region.getMinX(), region.getMinY(), region.getMinZ(),
                    region.getMaxX(), region.getMaxY(), region.getMaxZ(),
                    colorFor(region), 1.05f);
            double dist = region.distanceSquared(playerLoc.getBlockX(), playerLoc.getBlockY(), playerLoc.getBlockZ());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = region;
            }
        }

        if (nearest != null && nearestDist <= 16 * 16) {
            player.sendActionBar(ChatColor.GOLD + "Wave " + nearest.getWave() + " "
                    + nearest.getType() + ChatColor.GRAY + "  " + nearest.getId()
                    + ChatColor.DARK_GRAY + "  "
                    + nearest.getSizeX() + "x" + nearest.getSizeY() + "x" + nearest.getSizeZ());
        } else if (!template.getRegions().isEmpty()) {
            player.sendActionBar(ChatColor.GRAY + "Showing " + template.getRegions().size()
                    + " wave region" + (template.getRegions().size() == 1 ? "" : "s")
                    + " for " + ChatColor.YELLOW + template.getId());
        } else {
            player.sendActionBar(ChatColor.GRAY + "No wave regions on " + ChatColor.YELLOW + template.getId()
                    + ChatColor.GRAY + " — select two points, then /gd dungeon addregion");
        }
    }

    private DungeonTemplate resolveTemplate(Player player, World world) {
        DungeonInstance instance = plugin.getInstanceManager().getPlayerInstance(player);
        if (instance != null) return instance.getTemplate();
        return plugin.getTemplateManager().getTemplateByWorld(world.getName());
    }

    private Color colorFor(RegionMarker region) {
        if (RegionTypes.ELITE.equals(region.getType())) return Color.FUCHSIA;
        return switch (region.getWave() % 4) {
            case 1 -> Color.AQUA;
            case 2 -> Color.ORANGE;
            case 3 -> Color.LIME;
            default -> Color.TEAL;
        };
    }

    private void drawBox(Player player, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                         Color color, float size) {
        double x1 = minX;
        double y1 = minY;
        double z1 = minZ;
        double x2 = maxX + 1;
        double y2 = maxY + 1;
        double z2 = maxZ + 1;
        Particle.DustOptions dust = new Particle.DustOptions(color, size);

        drawEdge(player, x1, y1, z1, x2, y1, z1, dust);
        drawEdge(player, x1, y1, z2, x2, y1, z2, dust);
        drawEdge(player, x1, y2, z1, x2, y2, z1, dust);
        drawEdge(player, x1, y2, z2, x2, y2, z2, dust);

        drawEdge(player, x1, y1, z1, x1, y1, z2, dust);
        drawEdge(player, x2, y1, z1, x2, y1, z2, dust);
        drawEdge(player, x1, y2, z1, x1, y2, z2, dust);
        drawEdge(player, x2, y2, z1, x2, y2, z2, dust);

        drawEdge(player, x1, y1, z1, x1, y2, z1, dust);
        drawEdge(player, x2, y1, z1, x2, y2, z1, dust);
        drawEdge(player, x1, y1, z2, x1, y2, z2, dust);
        drawEdge(player, x2, y1, z2, x2, y2, z2, dust);
    }

    private void drawEdge(Player player, double x1, double y1, double z1,
                          double x2, double y2, double z2, Particle.DustOptions dust) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.01) {
            spawn(player, x1, y1, z1, dust);
            return;
        }
        double step = plugin.getConfigManager().isReducedEffects() ? 1.0 : 0.5;
        int points = Math.max(1, (int) Math.ceil(length / step));
        points = Math.min(points, 48);
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            spawn(player, x1 + dx * t, y1 + dy * t, z1 + dz * t, dust);
        }
    }

    private void spawn(Player player, double x, double y, double z, Particle.DustOptions dust) {
        Location loc = new Location(player.getWorld(), x, y, z);
        if (player.getLocation().distanceSquared(loc) > VIEW_DISTANCE_SQ) return;
        player.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, dust);
    }
}
