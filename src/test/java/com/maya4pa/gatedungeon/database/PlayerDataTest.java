package com.maya4pa.gatedungeon.database;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerDataTest {

    @Test
    void updatesHighestRankWithoutAllowingDowngrade() {
        PlayerData data = new PlayerData(UUID.randomUUID());

        data.updateHighestRank("B");
        data.updateHighestRank("D");

        assertEquals("B", data.getHighestRank());
    }

    @Test
    void normalizesPersistedValues() {
        PlayerData data = new PlayerData(UUID.randomUUID());

        data.setTotalCompleted(-4);
        data.setTotalFailed(-2);
        data.setHighestRank("a");
        data.setName(" ");

        assertEquals(0, data.getTotalCompleted());
        assertEquals(0, data.getTotalFailed());
        assertEquals("A", data.getHighestRank());
        assertEquals("Unknown", data.getDisplayName());
    }
}
