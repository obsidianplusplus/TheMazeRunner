package com.panda.themazerunner.persistence;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/** Plain maps defer world resolution until after the configured arena is loaded. */
public final class Locations {
    private Locations() {}

    public static Map<String, Object> encode(Location l) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("world", l.getWorld().getName());
        result.put("x", l.getX());
        result.put("y", l.getY());
        result.put("z", l.getZ());
        result.put("yaw", l.getYaw());
        result.put("pitch", l.getPitch());
        return result;
    }

    public static Location read(ConfigurationSection config, String path) {
        Object value = config.get(path);
        if (value instanceof Location l) return l.clone();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) throw new IllegalArgumentException("World unavailable: " + path);
        for (String key : List.of("x", "y", "z"))
            if (!(section.get(key) instanceof Number) || !Double.isFinite(section.getDouble(key)))
                throw new IllegalArgumentException("Invalid coordinate: " + path + "." + key);
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }
}
