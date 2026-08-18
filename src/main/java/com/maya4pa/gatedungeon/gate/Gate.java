package com.maya4pa.gatedungeon.gate;

import org.bukkit.Location;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Gate {
private final String id;
    private final Location location;
    private final String worldName;
    private final String rank;
    private final UUID creator;
    private final long creationTime;
    private boolean active = true;
    private boolean closing = false;
    private int maxPlayers = 1;
    private final Set<UUID> playersInside = new HashSet<>();
    private String selectedTemplateId = null;

    public Gate(String id, Location location, String rank, UUID creator, long creationTime) {
this.id = id;
        this.location = location.clone();
        this.worldName = location.getWorld() != null ? location.getWorld().getName() : "";
        this.rank = rank;
        this.creator = creator;
        this.creationTime = creationTime;
    }

public String getId() { return id; }
    public Location getLocation() { return location.clone(); }
    public String getWorldName() { return worldName; }
    public String getRank() { return rank; }
    public UUID getCreator() { return creator; }
    public long getCreationTime() { return creationTime; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isClosing() { return closing; }
    public void setClosing(boolean closing) { this.closing = closing; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int max) { maxPlayers = max; }
    public String getSelectedTemplateId() { return selectedTemplateId; }
    public void setSelectedTemplateId(String selectedTemplateId) { this.selectedTemplateId = selectedTemplateId; }

    public boolean isExpired(int minutes) {
        if (minutes < 0) return false;
        return System.currentTimeMillis() - creationTime > minutes * 60_000L;
    }
    public long getTimeLeft(int minutes) {
        if (minutes < 0) return -1;
        long left = minutes * 60_000L - (System.currentTimeMillis() - creationTime);
        return Math.max(0, left / 1000);
    }

    public void addPlayer(UUID playerId) {
        playersInside.add(playerId);
    }

    public void removePlayer(UUID playerId) {
        playersInside.remove(playerId);
    }

    public int getPlayersInside() {
        return playersInside.size();
    }

    public Set<UUID> getPlayersInsideSet() {
        return new HashSet<>(playersInside);
    }

    public boolean canEnter() {
        // Must be active, not closing, and not full
        return active && !closing && playersInside.size() < maxPlayers;
    }
}