package com.maya4pa.gatedungeon.mob;

public class MobSpawnConfig {
    private String mobType;
    private String markerId;
    private int amount;
    private int wave;

    public MobSpawnConfig(String mobType, String markerId, int amount, int wave) {
        this.mobType = mobType;
        this.markerId = markerId;
        this.amount = amount;
        this.wave = wave;
    }

    public String getMobType() { return mobType; }
    public void setMobType(String mobType) { this.mobType = mobType; }
    public String getMarkerId() { return markerId; }
    public void setMarkerId(String markerId) { this.markerId = markerId; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public int getWave() { return wave; }
    public void setWave(int wave) { this.wave = wave; }
}