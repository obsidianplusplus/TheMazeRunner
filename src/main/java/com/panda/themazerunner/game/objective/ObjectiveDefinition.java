package com.panda.themazerunner.game.objective;

import java.util.Set;

/** Data only. World interaction belongs to the Paper adapter. */
public record ObjectiveDefinition(
        String id,
        String title,
        Type type,
        String point,
        Set<String> prerequisites,
        int work,
        int supplies,
        boolean required,
        int deadlineSeconds) {
    public enum Type {
        EXPLORE,
        CLUE,
        REPAIR,
        SUPPLY
    }

    public ObjectiveDefinition(
            String id,
            String title,
            Type type,
            String point,
            Set<String> prerequisites,
            int work,
            int supplies,
            boolean required) {
        this(id, title, type, point, prerequisites, work, supplies, required, 0);
    }

    public ObjectiveDefinition {
        if (id == null
                || !id.matches("[a-z0-9_-]{1,48}")
                || title == null
                || title.isBlank()
                || type == null
                || point == null
                || point.isBlank()
                || work < 1
                || work > 100
                || supplies < 0
                || supplies > 100
                || deadlineSeconds < 0
                || deadlineSeconds > 7200)
            throw new IllegalArgumentException("Invalid objective: " + id);
        prerequisites = Set.copyOf(prerequisites);
    }
}
