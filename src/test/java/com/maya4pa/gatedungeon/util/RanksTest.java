package com.maya4pa.gatedungeon.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RanksTest {

    @Test
    void parsesCommaSeparatedRanks() {
        assertEquals(List.of("E", "D", "C"), Ranks.parse("E,D,C"));
        assertEquals(List.of("E", "D", "C"), Ranks.parse("e, d, c"));
    }

    @Test
    void parsesMixedSeparatorsAndDeduplicates() {
        assertEquals(List.of("E", "D", "C"), Ranks.parse("E D,C"));
        assertEquals(List.of("E", "D"), Ranks.parse("E,E,D"));
        assertEquals(List.of("S"), Ranks.parse("s"));
    }

    @Test
    void emptyInputIsUnassigned() {
        assertTrue(Ranks.parse("").isEmpty());
        assertTrue(Ranks.parse("   ").isEmpty());
        assertTrue(Ranks.parse((String) null).isEmpty());
        assertEquals("", Ranks.format(List.of()));
    }

    @Test
    void formatsUniqueUppercaseRanks() {
        assertEquals("E,D,C", Ranks.format(List.of("e", "D", "C", "e")));
    }
}
