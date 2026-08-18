package com.maya4pa.gatedungeon.util;

import com.maya4pa.gatedungeon.config.ConfigManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionTypesTest {

    @Test
    void acceptsMobAndEliteAliases() {
        assertEquals("MOB", RegionTypes.normalize("Mobs"));
        assertEquals("MOB", RegionTypes.normalize("mob"));
        assertEquals("ELITE", RegionTypes.normalize("ELITE"));
        assertEquals("ELITE", RegionTypes.normalize("elites"));
        assertNull(RegionTypes.normalize("BOSS"));
        assertFalse(RegionTypes.isValid("wave"));
        assertTrue(RegionTypes.isElite("ELITE"));
        assertTrue(RegionTypes.isMob("MOBS"));
    }

    @Test
    void infersEliteMobsFromNameOrFlag() {
        assertEquals("ELITE", ConfigManager.MobEntry.inferCategory("§cElite Goblin", null));
        assertEquals("ELITE", ConfigManager.MobEntry.inferCategory("Zombie", "true"));
        assertEquals("MOB", ConfigManager.MobEntry.inferCategory("Goblin", null));
        assertEquals("MOB", ConfigManager.MobEntry.inferCategory("Goblin", "false"));
    }
}
