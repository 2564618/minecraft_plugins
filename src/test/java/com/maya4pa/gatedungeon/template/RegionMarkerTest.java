package com.maya4pa.gatedungeon.template;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionMarkerTest {

    @Test
    void normalizesBoundsAndType() {
        Location pos1 = new Location(null, 10, 70, 5);
        Location pos2 = new Location(null, 4, 64, 12);
        RegionMarker region = new RegionMarker("abc12345", 1, "templatee1", pos1, pos2, "Mobs");

        assertEquals(4, region.getMinX());
        assertEquals(10, region.getMaxX());
        assertEquals(64, region.getMinY());
        assertEquals(70, region.getMaxY());
        assertEquals("MOB", region.getType());
        assertTrue(region.contains(7, 65, 8));
        assertFalse(region.contains(3, 65, 8));
        assertTrue(region.containsColumn(10, 12));
        assertFalse(region.containsColumn(11, 12));
    }

    @Test
    void reportsZeroDistanceInsideAndPositiveDistanceOutside() {
        RegionMarker region = new RegionMarker("id", 2, "world", 0, 64, 0, 5, 70, 5, "ELITE");
        assertEquals(0.0, region.distanceSquared(2, 66, 2));
        assertEquals(4.0, region.distanceSquared(-2, 66, 2));
        assertTrue(region.isElite());
        assertEquals(6 * 7 * 6, region.volume());
    }

    @Test
    void containsIgnoresWorldNameSoInstanceCopiesStillMatch() {
        RegionMarker region = new RegionMarker("id", 1, "templatee1", 0, 64, 0, 4, 68, 4, "MOB");
        Location instanceLocation = new Location(null, 2, 65, 3);
        assertTrue(region.contains(instanceLocation));
    }
}
