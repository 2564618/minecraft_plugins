package com.maya4pa.gatedungeon.template;

import com.maya4pa.gatedungeon.mob.BossConfig;
import com.maya4pa.gatedungeon.mob.MobSpawnConfig;
import com.maya4pa.gatedungeon.util.Ranks;
import com.maya4pa.gatedungeon.util.RegionTypes;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DungeonTemplate {
    private final String id;
    private final LinkedHashSet<String> ranks = new LinkedHashSet<>();
    private final String name;
    private final String worldName;
    private final String schematicFile;
    private Location entrance;
    private final long registeredTime;
    private final List<Marker> markers = new ArrayList<>();
    private final Map<String, List<Marker>> markersByType = new HashMap<>();
    private final List<MobSpawnConfig> mobSpawns = new ArrayList<>();
    private final BossConfig bossConfig = new BossConfig();
    private final List<RegionMarker> regions = new ArrayList<>();

    public DungeonTemplate(String id, String rank, String name, String worldName, String schematicFile,
                           Location entrance, long registeredTime) {
        this.id = id;
        this.ranks.addAll(Ranks.parse(rank));
        this.name = name;
        this.worldName = worldName;
        this.schematicFile = schematicFile;
        this.entrance = entrance == null ? null : entrance.clone();
        this.registeredTime = registeredTime;
    }

    public String getId() { return id; }

    /** First assigned rank, or an empty string for an unassigned draft template. */
    public String getRank() {
        return ranks.isEmpty() ? "" : ranks.iterator().next();
    }

    public List<String> getRanks() {
        return new ArrayList<>(ranks);
    }

    public String getRanksSerialized() {
        return Ranks.format(ranks);
    }

    public void setRank(String rank) {
        ranks.clear();
        ranks.addAll(Ranks.parse(rank));
    }

    public void setRanks(Collection<String> newRanks) {
        ranks.clear();
        ranks.addAll(Ranks.parse(newRanks));
    }

    public boolean hasRank(String rank) {
        return rank != null && ranks.contains(rank.toUpperCase(java.util.Locale.ROOT));
    }

    public boolean isAssigned() {
        return !ranks.isEmpty();
    }

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

    public void addRegion(RegionMarker region) {
        regions.add(region);
    }

    public boolean removeRegion(String regionId) {
        return regions.removeIf(r -> r.getId().equals(regionId));
    }

    public RegionMarker getRegion(String regionId) {
        if (regionId == null) return null;
        for (RegionMarker region : regions) {
            if (region.getId().equalsIgnoreCase(regionId)) return region;
        }
        return null;
    }

    public List<RegionMarker> getRegions() {
        return new ArrayList<>(regions);
    }

    public List<RegionMarker> getRegionsByWave(int wave) {
        return regions.stream().filter(r -> r.getWave() == wave).toList();
    }

    public List<RegionMarker> getRegionsByWaveAndType(int wave, String type) {
        String normalized = RegionTypes.normalize(type);
        if (normalized == null) return getRegionsByWave(wave);
        return regions.stream()
                .filter(r -> r.getWave() == wave && normalized.equals(r.getType()))
                .toList();
    }

    public int getMaxWave() {
        return regions.stream().mapToInt(RegionMarker::getWave).max().orElse(0);
    }

    public Set<Integer> getWaves() {
        LinkedHashSet<Integer> waves = new LinkedHashSet<>();
        for (RegionMarker region : regions) {
            waves.add(region.getWave());
        }
        return waves;
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
