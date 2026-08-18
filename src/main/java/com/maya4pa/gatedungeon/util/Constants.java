package com.maya4pa.gatedungeon.util;

public final class Constants {

    public static final int TASK_DELAY_SHORT = 1;
    public static final int TASK_DELAY_MEDIUM = 5;
    public static final int TASK_DELAY_LONG = 20;
    public static final int TASK_CHECK_INTERVAL = 20;

    public static final long PLAYER_INTERACTION_COOLDOWN = 2000L;
    public static final long GATE_INTERACTION_COOLDOWN = 1000L;

    public static final double DEFAULT_EXIT_RADIUS = 5.0;
    public static final double IMPROVED_EXIT_RADIUS = 8.0;
    /** How close a player must be to a gate to enter it (blocks). */
    public static final double GATE_ENTER_RADIUS = 2.25;

    public static final int INITIAL_ARRAY_LIST_CAPACITY = 10;
    public static final int DEFAULT_WAVE_COUNT = 3;

    public static final int TITLE_FADE_IN = 0;
    public static final int TITLE_FADE_STAY = 40;
    public static final int TITLE_FADE_OUT = 10;

    public static final String PERMISSION_ADMIN = "gatedungeon.admin";
    public static final String PERMISSION_RELOAD = "gatedungeon.reload";
    public static final String PERMISSION_GATE_SPAWN = "gatedungeon.gate.spawn";
    public static final String PERMISSION_GATE_REMOVE = "gatedungeon.gate.remove";
    public static final String PERMISSION_GATE_LIST = "gatedungeon.gate.list";
    public static final String PERMISSION_DUNGEON_REGISTER = "gatedungeon.dungeon.register";
    public static final String PERMISSION_DUNGEON_UNREGISTER = "gatedungeon.dungeon.unregister";
    public static final String PERMISSION_DUNGEON_ADMIN = "gatedungeon.dungeon.admin";
    public static final String PERMISSION_DUNGEON_CREATE = "gatedungeon.dungeon.create";
    public static final String PERMISSION_DUNGEON_ASSIGN = "gatedungeon.dungeon.assign";

    /** Live instance copies created when a player enters a gate. */
    public static final String INSTANCE_WORLD_PREFIX = "gdinst_";
    /** Pre-1.0.5 instance worlds; still recognized for cleanup. */
    public static final String LEGACY_INSTANCE_WORLD_PREFIX = "dungeon_";

    private Constants() {
        throw new AssertionError("Utility class");
    }
}
