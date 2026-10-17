package com.panda.themazerunner.game.rules;

import com.panda.themazerunner.game.objective.*;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/** Missing keys use documented defaults; wrong types/ranges fail closed with a key path. */
public record GameSettings(
        ModeRules mode,
        String profile,
        int minimum,
        int maximum,
        int countdown,
        int preparation,
        int duration,
        int results,
        int day,
        int night,
        int warning,
        int eventInterval,
        int eventDuration,
        int initialSupplies,
        int supplyCap,
        int grievers,
        int reconnectSeconds,
        List<ObjectiveDefinition> objectives,
        Map<String, Ability> abilities) {
    public record Ability(int cost, int cooldown, int range) {}

    public GameSettings {
        objectives = List.copyOf(objectives);
        abilities = Map.copyOf(abilities);
    }

    public static GameSettings load(ConfigurationSection root) {
        ModeRules mode;
        try {
            mode = ModeRules.valueOf(text(root, "match.mode", "CORE"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("match.mode must be CORE or EXPERIMENT");
        }
        String profile = text(root, "match.profile", "FILM");
        if (!Set.of("FILM", "NOVEL").contains(profile))
            throw new IllegalArgumentException("match.profile must be FILM or NOVEL");
        int min = integer(root, "match.minimum-players", 2, 1, 32),
                max = integer(root, "match.maximum-players", 16, min, 64);
        if (mode == ModeRules.EXPERIMENT && min < 4)
            throw new IllegalArgumentException("EXPERIMENT minimum-players >= 4");
        List<ObjectiveDefinition> definitions = new ArrayList<>();
        ConfigurationSection section =
                root.getConfigurationSection("profiles." + profile + ".objectives");
        if (section == null) section = root.getConfigurationSection("objectives");
        if (section == null)
            definitions.addAll(ObjectiveTracker.chain(List.of("route-1", "route-2")));
        else
            for (String id : section.getKeys(false)) {
                ConfigurationSection d = section.getConfigurationSection(id);
                if (d == null)
                    throw new IllegalArgumentException("objectives." + id + " must be a section");
                ObjectiveDefinition.Type type;
                try {
                    type = ObjectiveDefinition.Type.valueOf(text(d, "type", "CLUE"));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown objective type: " + id);
                }
                Object parents = d.get("requires");
                if (parents != null
                        && (!(parents instanceof List<?> list)
                                || list.stream().anyMatch(v -> !(v instanceof String))))
                    throw new IllegalArgumentException(
                            "objectives." + id + ".requires must be a string list");
                Object required = d.get("required");
                if (required != null && !(required instanceof Boolean))
                    throw new IllegalArgumentException(
                            "objectives." + id + ".required must be boolean");
                definitions.add(
                        new ObjectiveDefinition(
                                id,
                                text(d, "title", id),
                                type,
                                text(d, "point", id),
                                Set.copyOf(d.getStringList("requires")),
                                integer(d, "work", 1, 1, 100),
                                integer(d, "supplies", 1, 0, 100),
                                d.getBoolean("required", true),
                                integer(d, "deadline-seconds", 0, 0, 7200)));
            }
        new ObjectiveTracker(definitions);
        Map<String, Ability> abilities = new LinkedHashMap<>();
        int[][] defaults = {{1, 30, 24}, {2, 40, 6}, {3, 20, 5}, {1, 15, 4}, {2, 30, 6}};
        String[] ids = {"runner", "guardian", "medjack", "builder", "cook"};
        for (int i = 0; i < ids.length; i++) {
            String prefix = "classes." + ids[i] + ".";
            abilities.put(
                    ids[i],
                    new Ability(
                            integer(root, prefix + "cost", defaults[i][0], 1, 20),
                            integer(root, prefix + "cooldown", defaults[i][1], 2, 300),
                            integer(root, prefix + "range", defaults[i][2], 2, 32)));
        }
        int day = integer(root, "match.day-seconds", 180, 30, 1800),
                night = integer(root, "match.night-seconds", 90, 20, 900);
        int warning = integer(root, "match.warning-seconds", 10, 5, Math.min(day, night) - 1);
        integer(root, "griever.detection", 18, 4, 32);
        integer(root, "griever.damage", 4, 1, 12);
        if (integer(root, "resources.cap", 64, 0, 1000)
                < integer(root, "resources.initial", 12, 0, 100))
            throw new IllegalArgumentException("resources.cap must be >= resources.initial");
        new EnvironmentCycle(
                day,
                night,
                warning,
                integer(root, "events.interval-seconds", 120, 30, 1800),
                integer(root, "events.duration-seconds", 30, 5, 120));
        return new GameSettings(
                mode,
                profile,
                min,
                max,
                integer(root, "match.countdown-seconds", 20, 3, 120),
                integer(root, "match.preparation-seconds", 5, 1, 30),
                integer(root, "match.duration-seconds", 900, 60, 7200),
                integer(root, "match.results-seconds", 8, 2, 60),
                day,
                night,
                warning,
                integer(root, "events.interval-seconds", 120, 30, 1800),
                integer(root, "events.duration-seconds", 30, 5, 120),
                integer(root, "resources.initial", 12, 0, 100),
                integer(
                        root,
                        "resources.cap",
                        64,
                        integer(root, "resources.initial", 12, 0, 100),
                        1000),
                integer(root, "griever.maximum", 4, 0, 8),
                integer(root, "match.reconnect-seconds", 30, 0, 120),
                definitions,
                abilities);
    }

    public static int integer(
            ConfigurationSection section, String key, int fallback, int min, int max) {
        Object value = section.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number number)
                || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.intValue()
                || number.intValue() < min
                || number.intValue() > max)
            throw new IllegalArgumentException(key + " must be an integer in " + min + ".." + max);
        return number.intValue();
    }

    private static String text(ConfigurationSection section, String key, String fallback) {
        Object value = section.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String str) || str.isBlank())
            throw new IllegalArgumentException(key + " must be text");
        return str;
    }
}
