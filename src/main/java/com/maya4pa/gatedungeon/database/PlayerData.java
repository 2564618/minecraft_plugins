package com.maya4pa.gatedungeon.database;

import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private String name;
    private int totalCompleted = 0;
    private int totalFailed = 0;
    private String highestRank = "E";

    public PlayerData(UUID uuid) { this.uuid = uuid; }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getTotalCompleted() { return totalCompleted; }
    public void setTotalCompleted(int v) { totalCompleted = v; }
    public int getTotalFailed() { return totalFailed; }
    public void setTotalFailed(int v) { totalFailed = v; }
    public String getHighestRank() { return highestRank; }
    public void setHighestRank(String r) { highestRank = r; }

    public void incrementCompleted() { totalCompleted++; }
    public void incrementFailed() { totalFailed++; }
    public void updateHighestRank(String rank) {
        if (rankValue(rank) > rankValue(highestRank)) highestRank = rank;
    }
private int rankValue(String r) {
        if (r == null) return 1;
        return switch (r.toUpperCase()) {
            case "S" -> 6; case "A" -> 5; case "B" -> 4;
            case "C" -> 3; case "D" -> 2; default -> 1;
        };
    }
}