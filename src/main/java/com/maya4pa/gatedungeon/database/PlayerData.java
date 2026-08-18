package com.maya4pa.gatedungeon.database;

import java.util.Locale;
import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private String name;
    private int totalCompleted;
    private int totalFailed;
    private String highestRank = "E";

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public String getDisplayName() { return name == null || name.isBlank() ? "Unknown" : name; }
    public void setName(String name) { this.name = name; }
    public int getTotalCompleted() { return totalCompleted; }
    public void setTotalCompleted(int value) { totalCompleted = Math.max(0, value); }
    public int getTotalFailed() { return totalFailed; }
    public void setTotalFailed(int value) { totalFailed = Math.max(0, value); }
    public String getHighestRank() { return highestRank; }
    public void setHighestRank(String rank) {
        highestRank = rank == null || rank.isBlank() ? "E" : rank.toUpperCase(Locale.ROOT);
    }

    public void incrementCompleted() { totalCompleted++; }
    public void incrementFailed() { totalFailed++; }

    public void updateHighestRank(String rank) {
        if (rankValue(rank) > rankValue(highestRank)) {
            setHighestRank(rank);
        }
    }

    private int rankValue(String rank) {
        if (rank == null) return 1;
        return switch (rank.toUpperCase(Locale.ROOT)) {
            case "S" -> 6;
            case "A" -> 5;
            case "B" -> 4;
            case "C" -> 3;
            case "D" -> 2;
            default -> 1;
        };
    }
}