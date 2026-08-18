package com.maya4pa.gatedungeon.database;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.gate.Gate;
import com.maya4pa.gatedungeon.template.DungeonTemplate;
import com.maya4pa.gatedungeon.template.Marker;
import com.maya4pa.gatedungeon.template.RegionMarker;
import org.bukkit.Location;
import org.bukkit.World;
import java.io.File;
import java.sql.*;
import java.util.*;

public class DatabaseManager {
    private final GateDungeonPlugin plugin;
    private final File dbFile;
    private Connection connection;

    public DatabaseManager(GateDungeonPlugin plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "gatedungeon.db");
    }

    public void initialize() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON");
                pragma.execute("PRAGMA journal_mode = WAL");
            }
            createTables();
            plugin.getLogger().info("✅ Database initialized at " + dbFile.getAbsolutePath());
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Failed to init database: " + e.getMessage());
            // Fail fast instead of letting every later call NPE on a null connection.
            throw new IllegalStateException("GateDungeon cannot start without its database", e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS gates (
                    id TEXT PRIMARY KEY,
                    world TEXT NOT NULL,
                    x INT NOT NULL,
                    y INT NOT NULL,
                    z INT NOT NULL,
                    rank TEXT NOT NULL,
                    creator_uuid TEXT NOT NULL,
                    creation_time LONG NOT NULL,
                    active INT DEFAULT 1
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS templates (
                    id TEXT PRIMARY KEY,
                    rank TEXT NOT NULL,
                    name TEXT NOT NULL,
                    world_name TEXT NOT NULL,
                    schematic_file TEXT NOT NULL,
                    entrance_x INT NOT NULL,
                    entrance_y INT NOT NULL,
                    entrance_z INT NOT NULL,
                    registered_time LONG NOT NULL
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS template_markers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    template_id TEXT NOT NULL,
                    type TEXT NOT NULL,
                    x INT NOT NULL,
                    y INT NOT NULL,
                    z INT NOT NULL,
                    metadata TEXT,
                    FOREIGN KEY (template_id) REFERENCES templates(id) ON DELETE CASCADE
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS template_regions (
                    id TEXT PRIMARY KEY,
                    template_id TEXT NOT NULL,
                    wave INT NOT NULL,
                    world TEXT NOT NULL,
                    min_x INT NOT NULL,
                    min_y INT NOT NULL,
                    min_z INT NOT NULL,
                    max_x INT NOT NULL,
                    max_y INT NOT NULL,
                    max_z INT NOT NULL,
                    type TEXT NOT NULL,
                    FOREIGN KEY (template_id) REFERENCES templates(id) ON DELETE CASCADE
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    total_completed INT DEFAULT 0,
                    total_failed INT DEFAULT 0,
                    highest_rank TEXT DEFAULT 'E'
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS completions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    template_id TEXT NOT NULL,
                    completion_time LONG NOT NULL,
                    FOREIGN KEY (player_uuid) REFERENCES players(uuid)
                )
            """);
        }
        plugin.getLogger().info("✅ Tables created/verified.");
    }

    // ----- GATE OPERATIONS -----
    public void saveGate(Gate gate) {
        String sql = "INSERT OR REPLACE INTO gates (id, world, x, y, z, rank, creator_uuid, creation_time, active) VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
Location loc = gate.getLocation();
            if (loc.getWorld() == null) {
                plugin.getLogger().warning("Cannot save gate " + gate.getId() + ": world unloaded");
                return;
            }
            ps.setString(1, gate.getId());
            ps.setString(2, loc.getWorld().getName());
            ps.setInt(3, loc.getBlockX());
            ps.setInt(4, loc.getBlockY());
            ps.setInt(5, loc.getBlockZ());
            ps.setString(6, gate.getRank());
            ps.setString(7, gate.getCreator().toString());
            ps.setLong(8, gate.getCreationTime());
            ps.setInt(9, gate.isActive() ? 1 : 0);
            ps.executeUpdate();
            plugin.getLogger().info("✅ Saved gate: " + gate.getId());
        } catch (SQLException e) {
            plugin.getLogger().warning("❌ Failed to save gate: " + e.getMessage());
        }
    }

    public void deleteGate(String id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM gates WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
            plugin.getLogger().info("✅ Deleted gate: " + id);
        } catch (SQLException e) {
            plugin.getLogger().warning("❌ Failed to delete gate: " + e.getMessage());
        }
    }

    public List<Gate> loadAllGates() {
        List<Gate> gates = new ArrayList<>();
        String sql = "SELECT * FROM gates WHERE active = 1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String id = rs.getString("id");
                String worldName = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                String rank = rs.getString("rank");
                UUID creator;
                try {
                    creator = UUID.fromString(rs.getString("creator_uuid"));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Skipping gate " + id
                            + " with malformed creator_uuid '" + rs.getString("creator_uuid") + "'");
                    continue;
                }
                long time = rs.getLong("creation_time");

                World world = plugin.getServer().getWorld(worldName);
                if (world != null) {
                    Location loc = new Location(world, x, y, z);
                    Gate gate = new Gate(id, loc, rank, creator, time);
                    gates.add(gate);
                }
            }
            plugin.getLogger().info("✅ Loaded " + gates.size() + " gates.");
        } catch (SQLException e) {
            plugin.getLogger().warning("❌ Failed to load gates: " + e.getMessage());
        }
        return gates;
    }

    // ----- TEMPLATE OPERATIONS -----
    public void saveTemplate(DungeonTemplate template) {
        plugin.getLogger().info("💾 Saving template: " + template.getId());
        try {
            // Save template
            String sql = "INSERT OR REPLACE INTO templates (id, rank, name, world_name, schematic_file, entrance_x, entrance_y, entrance_z, registered_time) VALUES (?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, template.getId());
                ps.setString(2, template.getRank());
                ps.setString(3, template.getName());
                ps.setString(4, template.getWorldName());
                ps.setString(5, template.getSchematicFile());
                Location ent = template.getEntrance();
                ps.setInt(6, ent.getBlockX());
                ps.setInt(7, ent.getBlockY());
                ps.setInt(8, ent.getBlockZ());
                ps.setLong(9, template.getRegisteredTime());
                ps.executeUpdate();
            }

            // Delete old markers
            try (PreparedStatement del = connection.prepareStatement("DELETE FROM template_markers WHERE template_id = ?")) {
                del.setString(1, template.getId());
                del.executeUpdate();
            }

            // Insert new markers
            String markerSql = "INSERT INTO template_markers (template_id, type, x, y, z, metadata) VALUES (?,?,?,?,?,?)";
            try (PreparedStatement ps = connection.prepareStatement(markerSql)) {
                for (var marker : template.getMarkers()) {
                    ps.setString(1, template.getId());
                    ps.setString(2, marker.getType());
                    ps.setInt(3, marker.getLocation().getBlockX());
                    ps.setInt(4, marker.getLocation().getBlockY());
                    ps.setInt(5, marker.getLocation().getBlockZ());
                    ps.setString(6, marker.getMetadata());
                    ps.addBatch();
                }
                ps.executeBatch();
                plugin.getLogger().info("✅ Saved " + template.getMarkers().size() + " markers.");
            }

            // Save regions
            saveRegions(template);

            plugin.getLogger().info("✅ Saved template: " + template.getId());
        } catch (SQLException e) {
            plugin.getLogger().warning("❌ Failed to save template: " + e.getMessage());
        }
    }

    public void saveRegions(DungeonTemplate template) {
        plugin.getLogger().info("💾 Saving regions for template: " + template.getId());
        try {
            // Delete old regions
            String deleteSql = "DELETE FROM template_regions WHERE template_id = ?";
            try (PreparedStatement ps = connection.prepareStatement(deleteSql)) {
                ps.setString(1, template.getId());
                ps.executeUpdate();
            }

            // Insert new regions
            String insertSql = "INSERT INTO template_regions (id, template_id, wave, world, min_x, min_y, min_z, max_x, max_y, max_z, type) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                for (RegionMarker r : template.getRegions()) {
                    ps.setString(1, r.getId());
                    ps.setString(2, template.getId());
                    ps.setInt(3, r.getWave());
                    ps.setString(4, r.getWorldName());
                    ps.setInt(5, r.getMinX());
                    ps.setInt(6, r.getMinY());
                    ps.setInt(7, r.getMinZ());
                    ps.setInt(8, r.getMaxX());
                    ps.setInt(9, r.getMaxY());
                    ps.setInt(10, r.getMaxZ());
                    ps.setString(11, r.getType());
                    ps.addBatch();
                }
                ps.executeBatch();
                plugin.getLogger().info("✅ Saved " + template.getRegions().size() + " regions.");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("❌ Failed to save regions: " + e.getMessage());
        }
    }

public void deleteTemplate(String id) {
        try {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM template_markers WHERE template_id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM template_regions WHERE template_id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM templates WHERE id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            plugin.getLogger().info("✅ Deleted template: " + id);
        } catch (SQLException e) {
            plugin.getLogger().warning("❌ Failed to delete template: " + e.getMessage());
        }
    }

    public List<DungeonTemplate> loadAllTemplates() {
        List<DungeonTemplate> templates = new ArrayList<>();
        String sql = "SELECT * FROM templates";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String id = rs.getString("id");
                String rank = rs.getString("rank");
                String name = rs.getString("name");
                String worldName = rs.getString("world_name");
                String schematic = rs.getString("schematic_file");
                int ex = rs.getInt("entrance_x");
                int ey = rs.getInt("entrance_y");
                int ez = rs.getInt("entrance_z");
                long time = rs.getLong("registered_time");

                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    world = plugin.getWorldManager().createVoidWorld(worldName);
                    if (world == null) continue;
                }
                Location entrance = new Location(world, ex, ey, ez);
                DungeonTemplate template = new DungeonTemplate(id, rank, name, worldName, schematic, entrance, time);
                loadMarkers(template);
                loadRegions(template);
                templates.add(template);
            }
            plugin.getLogger().info("✅ Loaded " + templates.size() + " templates.");
        } catch (SQLException e) {
            plugin.getLogger().warning("❌ Failed to load templates: " + e.getMessage());
        }
        return templates;
    }

    private void loadMarkers(DungeonTemplate template) {
        String sql = "SELECT * FROM template_markers WHERE template_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, template.getId());
            try (ResultSet rs = ps.executeQuery()) {
            World world = plugin.getServer().getWorld(template.getWorldName());
            if (world == null) return;
            while (rs.next()) {
                String type = rs.getString("type");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                String meta = rs.getString("metadata");
                Location loc = new Location(world, x, y, z);
                template.addMarker(new Marker(type, loc, meta));
            }
            plugin.getLogger().info("✅ Loaded " + template.getMarkers().size() + " markers for " + template.getId());
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("❌ Failed to load markers for " + template.getId() + ": " + e.getMessage());
        }
    }

    private void loadRegions(DungeonTemplate template) {
        String sql = "SELECT * FROM template_regions WHERE template_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, template.getId());
            try (ResultSet rs = ps.executeQuery()) {
            int count = 0;
            while (rs.next()) {
                String id = rs.getString("id");
                int wave = rs.getInt("wave");
                String worldName = rs.getString("world");
                int minX = rs.getInt("min_x");
                int minY = rs.getInt("min_y");
                int minZ = rs.getInt("min_z");
                int maxX = rs.getInt("max_x");
                int maxY = rs.getInt("max_y");
                int maxZ = rs.getInt("max_z");
                String type = rs.getString("type");
                RegionMarker region = new RegionMarker(id, wave, worldName, minX, minY, minZ, maxX, maxY, maxZ, type);
                template.addRegion(region);
                count++;
            }
            plugin.getLogger().info("✅ Loaded " + count + " regions for " + template.getId());
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("❌ Failed to load regions for " + template.getId() + ": " + e.getMessage());
        }
    }

    // ----- PLAYER OPERATIONS -----
    public PlayerData loadPlayer(UUID uuid) {
        String sql = "SELECT * FROM players WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PlayerData data = new PlayerData(uuid);
                    data.setName(rs.getString("name"));
                    data.setTotalCompleted(rs.getInt("total_completed"));
                    data.setTotalFailed(rs.getInt("total_failed"));
                    data.setHighestRank(rs.getString("highest_rank"));
                    return data;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("❌ Failed to load player: " + e.getMessage());
        }
        return new PlayerData(uuid);
    }

    public void savePlayer(PlayerData data) {
        String sql = "INSERT OR REPLACE INTO players (uuid, name, total_completed, total_failed, highest_rank) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
ps.setString(1, data.getUuid().toString());
            ps.setString(2, data.getName() != null ? data.getName() : "Unknown");
            ps.setInt(3, data.getTotalCompleted());
            ps.setInt(4, data.getTotalFailed());
            ps.setString(5, data.getHighestRank());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("❌ Failed to save player: " + e.getMessage());
        }
    }

    public void addCompletion(UUID playerUuid, String templateId) {
        String sql = "INSERT INTO completions (player_uuid, template_id, completion_time) VALUES (?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, templateId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("❌ Failed to add completion: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
            plugin.getLogger().info("✅ Database connection closed.");
        } catch (SQLException e) {
            plugin.getLogger().warning("❌ Error closing DB: " + e.getMessage());
        }
    }
}