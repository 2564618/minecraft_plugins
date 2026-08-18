package com.maya4pa.gatedungeon.template;

import org.bukkit.Location;

public class Marker {
    private final String type;
    private final Location location;
    private final String metadata;

    public Marker(String type, Location location, String metadata) {
        this.type = type;
        this.location = location.clone();
        this.metadata = metadata;
    }

    public String getType() { return type; }
    public Location getLocation() { return location.clone(); }
    public String getMetadata() { return metadata; }
}