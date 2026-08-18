package com.maya4pa.gatedungeon.database;

import com.maya4pa.gatedungeon.GateDungeonPlugin;
import com.maya4pa.gatedungeon.gate.Gate;
import com.maya4pa.gatedungeon.template.DungeonTemplate;
import com.maya4pa.gatedungeon.template.Marker;
import com.maya4pa.gatedungeon.template.RegionMarker;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IllegalStateException("Could not create plugin data directory");
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON");
                pragma.execute("PRAGMA journal_mode = WAL");
            }
            createTables();
            plugin.getLogger().info("Database initialized at " + dbFile.getAbsolutePath());
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
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
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_players_name ON players(name COLLATE NOCASE)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_completions_player ON completions(player_uuid)");
        }
    }

    // ----- GATE OPERATIONS -----

    public void saveGate(Gate gate) {
        String sql = """
                INSERT INTO gates (id, world, x, y, z, rank, creator_uuid, creation_time, active)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    world = excluded.world,
                    x = excluded.x,
                    y = excluded.y,
                    z = excluded.z,
                    rank = excluded.rank,
                    creator_uuid = excluded.creator_uuid,
                    creation_time = excluded.creation_time,
                    active = excluded.active
                """;
        Location loc = gate.getLocation();
        if (loc.getWorld() == null) {
            plugin.getLogger().warning("Cannot save gate " + gate.getId() + ": world is unloaded");
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save gate " + gate.getId() + ": " + e.getMessage());
        }
    }

    public void deleteGate(String id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM gates WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to delete gate " + id + ": " + e.getMessage());
        }
    }

    public List<Gate> loadAllGates() {
        List<Gate> gates = new ArrayList<>();
        String sql = "SELECT * FROM gates WHERE active = 1";
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String id = rs.getString("id");
                String worldName = rs.getString("world");
                UUID creator;
                try {
                    creator = UUID.fromString(rs.getString("creator_uuid"));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Skipping gate " + id + " with malformed creator UUID");
                    continue;
                }

                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("Skipping gate " + id + ": world '" + worldName + "' is not loaded");
                    continue;
                }
                Location loc = new Location(world,
                        rs.getInt("x") + 0.5,
                        rs.getInt("y"),
                        rs.getInt("z") + 0.5);
                gates.add(new Gate(id, loc, rs.getString("rank"), creator, rs.getLong("creation_time")));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load gates: " + e.getMessage());
        }
        return gates;
    }

    // ----- TEMPLATE OPERATIONS -----

    public void saveTemplate(DungeonTemplate template) {
        Location entrance = template.getEntrance();
        if (entrance == null) {
            plugin.getLogger().warning("Cannot save template " + template.getId() + ": entrance is missing");
            return;
        }

        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            upsertTemplate(template, entrance);
            replaceMarkers(template);
            replaceRegions(template);
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly();
            plugin.getLogger().warning("Failed to save template " + template.getId() + ": " + e.getMessage());
        } finally {
            restoreAutoCommit(previousAutoCommit);
        }
    }

    private void upsertTemplate(DungeonTemplate template, Location entrance) throws SQLException {
        String sql = """
                INSERT INTO templates
                    (id, rank, name, world_name, schematic_file, entrance_x, entrance_y, entrance_z, registered_time)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    rank = excluded.rank,
                    name = excluded.name,
                    world_name = excluded.world_name,
                    schematic_file = excluded.schematic_file,
                    entrance_x = excluded.entrance_x,
                    entrance_y = excluded.entrance_y,
                    entrance_z = excluded.entrance_z,
                    registered_time = excluded.registered_time
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, template.getId());
            ps.setString(2, template.getRanksSerialized());
            ps.setString(3, template.getName());
            ps.setString(4, template.getWorldName());
            ps.setString(5, template.getSchematicFile());
            ps.setInt(6, entrance.getBlockX());
            ps.setInt(7, entrance.getBlockY());
            ps.setInt(8, entrance.getBlockZ());
            ps.setLong(9, template.getRegisteredTime());
            ps.executeUpdate();
        }
    }

    private void replaceMarkers(DungeonTemplate template) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM template_markers WHERE template_id = ?")) {
            ps.setString(1, template.getId());
            ps.executeUpdate();
        }

        String sql = "INSERT INTO template_markers (template_id, type, x, y, z, metadata) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Marker marker : template.getMarkers()) {
                Location location = marker.getLocation();
                ps.setString(1, template.getId());
                ps.setString(2, marker.getType());
                ps.setInt(3, location.getBlockX());
                ps.setInt(4, location.getBlockY());
                ps.setInt(5, location.getBlockZ());
                ps.setString(6, marker.getMetadata());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void saveRegions(DungeonTemplate template) {
        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            replaceRegions(template);
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly();
            plugin.getLogger().warning("Failed to save regions for " + template.getId() + ": " + e.getMessage());
        } finally {
            restoreAutoCommit(previousAutoCommit);
        }
    }

    private void replaceRegions(DungeonTemplate template) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM template_regions WHERE template_id = ?")) {
            ps.setString(1, template.getId());
            ps.executeUpdate();
        }

        String sql = """
                INSERT INTO template_regions
                    (id, template_id, wave, world, min_x, min_y, min_z, max_x, max_y, max_z, type)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (RegionMarker region : template.getRegions()) {
                ps.setString(1, region.getId());
                ps.setString(2, template.getId());
                ps.setInt(3, region.getWave());
                ps.setString(4, region.getWorldName());
                ps.setInt(5, region.getMinX());
                ps.setInt(6, region.getMinY());
                ps.setInt(7, region.getMinZ());
                ps.setInt(8, region.getMaxX());
                ps.setInt(9, region.getMaxY());
                ps.setInt(10, region.getMaxZ());
                ps.setString(11, region.getType());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public boolean deleteTemplate(String id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM templates WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to delete template " + id + ": " + e.getMessage());
            return false;
        }
    }

    public List<DungeonTemplate> loadAllTemplates() {
        List<DungeonTemplate> templates = new ArrayList<>();
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM templates")) {
            while (rs.next()) {
                String id = rs.getString("id");
                String worldName = rs.getString("world_name");
                World world = plugin.getWorldManager().getOrLoadWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("Skipping template " + id + ": world folder '" + worldName + "' is missing");
                    continue;
                }

                Location entrance = new Location(world,
                        rs.getInt("entrance_x") + 0.5,
                        rs.getInt("entrance_y"),
                        rs.getInt("entrance_z") + 0.5);
                DungeonTemplate template = new DungeonTemplate(
                        id,
                        rs.getString("rank"),
                        rs.getString("name"),
                        worldName,
                        rs.getString("schematic_file"),
                        entrance,
                        rs.getLong("registered_time"));
                loadMarkers(template, world);
                loadRegions(template);
                templates.add(template);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load templates: " + e.getMessage());
        }
        return templates;
    }

    private void loadMarkers(DungeonTemplate template, World world) {
        String sql = "SELECT * FROM template_markers WHERE template_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, template.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Location location = new Location(world,
                            rs.getInt("x") + 0.5,
                            rs.getInt("y"),
                            rs.getInt("z") + 0.5);
                    template.addMarker(new Marker(
                            rs.getString("type"), location, rs.getString("metadata")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load markers for " + template.getId() + ": " + e.getMessage());
        }
    }

    private void loadRegions(DungeonTemplate template) {
        String sql = "SELECT * FROM template_regions WHERE template_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, template.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    template.addRegion(new RegionMarker(
                            rs.getString("id"),
                            rs.getInt("wave"),
                            rs.getString("world"),
                            rs.getInt("min_x"),
                            rs.getInt("min_y"),
                            rs.getInt("min_z"),
                            rs.getInt("max_x"),
                            rs.getInt("max_y"),
                            rs.getInt("max_z"),
                            rs.getString("type")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load regions for " + template.getId() + ": " + e.getMessage());
        }
    }

    // ----- PLAYER OPERATIONS -----

    public PlayerData loadPlayer(UUID uuid) {
        String sql = "SELECT * FROM players WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readPlayer(rs);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load player " + uuid + ": " + e.getMessage());
        }
        return new PlayerData(uuid);
    }

    public Optional<PlayerData> findPlayerByName(String name) {
        String sql = "SELECT * FROM players WHERE name = ? COLLATE NOCASE LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readPlayer(rs));
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            plugin.getLogger().warning("Failed to find player '" + name + "': " + e.getMessage());
        }
        return Optional.empty();
    }

    private PlayerData readPlayer(ResultSet rs) throws SQLException {
        PlayerData data = new PlayerData(UUID.fromString(rs.getString("uuid")));
        data.setName(rs.getString("name"));
        data.setTotalCompleted(rs.getInt("total_completed"));
        data.setTotalFailed(rs.getInt("total_failed"));
        data.setHighestRank(rs.getString("highest_rank"));
        return data;
    }

    public void savePlayer(PlayerData data) {
        try {
            upsertPlayer(data);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save player " + data.getUuid() + ": " + e.getMessage());
        }
    }

    private void upsertPlayer(PlayerData data) throws SQLException {
        String sql = """
                INSERT INTO players (uuid, name, total_completed, total_failed, highest_rank)
                VALUES (?,?,?,?,?)
                ON CONFLICT(uuid) DO UPDATE SET
                    name = excluded.name,
                    total_completed = excluded.total_completed,
                    total_failed = excluded.total_failed,
                    highest_rank = excluded.highest_rank
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, data.getUuid().toString());
            ps.setString(2, data.getName() != null ? data.getName() : "Unknown");
            ps.setInt(3, data.getTotalCompleted());
            ps.setInt(4, data.getTotalFailed());
            ps.setString(5, data.getHighestRank());
            ps.executeUpdate();
        }
    }

    /** Saves updated totals and the completion history as one atomic operation. */
    public void recordCompletion(PlayerData data, String templateId) {
        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            upsertPlayer(data);
            insertCompletion(data.getUuid(), templateId);
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly();
            plugin.getLogger().warning("Failed to record completion for " + data.getUuid() + ": " + e.getMessage());
        } finally {
            restoreAutoCommit(previousAutoCommit);
        }
    }

    private void insertCompletion(UUID playerUuid, String templateId) throws SQLException {
        String sql = "INSERT INTO completions (player_uuid, template_id, completion_time) VALUES (?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, templateId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            plugin.getLogger().warning("Failed to roll back database transaction: " + rollbackError.getMessage());
        }
    }

    private void restoreAutoCommit(boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to restore database auto-commit: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing database: " + e.getMessage());
        }
    }
}