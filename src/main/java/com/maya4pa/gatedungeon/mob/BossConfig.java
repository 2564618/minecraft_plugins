package com.maya4pa.gatedungeon.mob;

public class BossConfig {
    private String mobType = "zombie";
    private double health = 100.0;
    private double damage = 10.0;
    private String name = "Boss";
    private String spawnMarker = "BOSS_SPAWN";

    public String getMobType() { return mobType; }
    public void setMobType(String mobType) { this.mobType = mobType; }
    public double getHealth() { return health; }
    public void setHealth(double health) { this.health = health; }
    public double getDamage() { return damage; }
    public void setDamage(double damage) { this.damage = damage; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSpawnMarker() { return spawnMarker; }
    public void setSpawnMarker(String spawnMarker) { this.spawnMarker = spawnMarker; }
}