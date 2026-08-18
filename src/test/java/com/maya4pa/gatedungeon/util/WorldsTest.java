package com.maya4pa.gatedungeon.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldsTest {

    @Test
    void validatesBuilderWorldNamesWithoutSilentlyChangingThem() {
        assertTrue(Worlds.isValidWorldName("forest_01"));
        assertTrue(Worlds.isValidWorldName("Forest"));
        assertFalse(Worlds.isValidWorldName("../world"));
        assertFalse(Worlds.isValidWorldName("forest-cave"));
        assertFalse(Worlds.isValidWorldName(""));
        assertEquals("forest", Worlds.normalizeWorldName("Forest"));
    }

    @Test
    void safeWorldReferencesRejectPathsButAllowConventionalNames() {
        assertTrue(Worlds.isSafeWorldReference("world_nether"));
        assertTrue(Worlds.isSafeWorldReference("custom-world.2"));
        assertFalse(Worlds.isSafeWorldReference("../../server"));
        assertFalse(Worlds.isSafeWorldReference("world/subworld"));
        assertFalse(Worlds.isSafeWorldReference(".."));
    }

    @Test
    void recognizesCurrentAndLegacyInstanceWorlds() {
        assertTrue(Worlds.isInstanceWorld("gdinst_0123abcd"));
        assertTrue(Worlds.isInstanceWorld("dungeon_legacy"));
        assertFalse(Worlds.isInstanceWorld("my_dungeon_build"));
    }
}
