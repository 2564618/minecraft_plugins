package com.maya4pa.gatedungeon.template;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonTemplateRanksTest {

    @Test
    void draftTemplatesHaveNoRankUntilAssigned() {
        DungeonTemplate template = new DungeonTemplate(
                "templatee1", "", "templatee1", "templatee1", "templatee1",
                new Location(null, 0.5, 128, 0.5), 1L);

        assertFalse(template.isAssigned());
        assertEquals("", template.getRank());
        assertTrue(template.getRanks().isEmpty());
    }

    @Test
    void canBelongToMultipleRanks() {
        DungeonTemplate template = new DungeonTemplate(
                "templatee1", "E", "templatee1", "templatee1", "templatee1",
                new Location(null, 0.5, 128, 0.5), 1L);

        template.setRanks(List.of("E", "D", "C"));

        assertTrue(template.isAssigned());
        assertEquals(List.of("E", "D", "C"), template.getRanks());
        assertEquals("E,D,C", template.getRanksSerialized());
        assertTrue(template.hasRank("d"));
        assertFalse(template.hasRank("S"));
    }
}
