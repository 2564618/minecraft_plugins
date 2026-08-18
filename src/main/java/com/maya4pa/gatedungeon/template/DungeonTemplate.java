package com.maya4pa.gatedungeon.template;

import com.maya4pa.gatedungeon.mob.BossConfig;
import com.maya4pa.gatedungeon.mob.MobSpawnConfig;
import org.bukkit.Location;
import java.util.*;

public class DungeonTemplate {
    private final String id;
    private String rank;
    private final String name;
    private final String worldName;
    private final String schematicFile;
    private Location entrance;
    private final long registeredTime;
    private final List<Marker> markers = new ArrayList<>();
    private final Map<String, List<Marker>> markersByType = new HashMap<>();
    private final List<MobSpawnConfig> mobSpawns = new ArrayList<>();
    private final BossConfig bossConfig = new BossConfig();
    private final List<RegionMarker> regions = new ArrayList<>(); // New

    public DungeonTemplate(String id, String rank, String name, String worldName, String schematicFile,
                           Location entrance, long registeredTime) {
        this.id = id;
        this.rank = rank;
        this.name = name;
        this.worldName = worldName;
        this.schematicFile = schematicFile;
        this.entrance = entrance.clone();
        this.registeredTime = registeredTime;
    }

    public String getId() { return id; }
    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }
    public String getName() { return name; }
    public String getWorldName() { return worldName; }
    public String getSchematicFile() { return schematicFile; }
    public Location getEntrance() { return entrance == null ? null : entrance.clone(); }
    public void setEntrance(Location entrance) { this.entrance = entrance == null ? null : entrance.clone(); }
    public long getRegisteredTime() { return registeredTime; }
    public List<Marker> getMarkers() { return new ArrayList<>(markers); }

    public void addMarker(Marker marker) {
        markers.add(marker);
        markersByType.computeIfAbsent(marker.getType(), k -> new ArrayList<>()).add(marker);
    }

    public void clearMarkers() {
        markers.clear();
        markersByType.clear();
    }

    public List<Marker> getMarkersByType(String type) {
        return new ArrayList<>(markersByType.getOrDefault(type, new ArrayList<>()));
    }

    public Marker getMarkerByType(String type) {
        List<Marker> list = markersByType.get(type);
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }

    // Region management
    public void addRegion(RegionMarker region) {
        regions.add(region);
    }

    public void removeRegion(String regionId) {
        regions.removeIf(r -> r.getId().equals(regionId));
    }

    public List<RegionMarker> getRegions() {
        return new ArrayList<>(regions);
    }

    public List<RegionMarker> getRegionsByWave(int wave) {
        return regions.stream().filter(r -> r.getWave() == wave).toList();
    }

    public int getMaxWave() {
        return regions.stream().mapToInt(RegionMarker::getWave).max().orElse(0);
    }

    public void addMobSpawn(MobSpawnConfig config) {
        mobSpawns.add(config);
    }

    public List<MobSpawnConfig> getMobSpawns() {
        return new ArrayList<>(mobSpawns);
    }

    public BossConfig getBossConfig() {
        return bossConfig;
    }

    public void setBossConfig(BossConfig config) {
        this.bossConfig.setMobType(config.getMobType());
        this.bossConfig.setHealth(config.getHealth());
        this.bossConfig.setDamage(config.getDamage());
        this.bossConfig.setName(config.getName());
        this.bossConfig.setSpawnMarker(config.getSpawnMarker());
    }
}