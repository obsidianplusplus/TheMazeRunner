package com.panda.themazerunner.game.rules;

import java.util.*;

/** Explicit adaptation boundary; controller mode is an original multiplayer experiment. */
public enum ModeRules {
    CORE,
    EXPERIMENT;

    public enum Faction {
        GLADERS,
        WCKD
    }

    public Map<UUID, Faction> assign(Collection<UUID> players, Random random) {
        if (this == EXPERIMENT && players.size() < 4)
            throw new IllegalStateException("Experiment requires at least 4 players");
        Map<UUID, Faction> result = new HashMap<>();
        players.forEach(id -> result.put(id, Faction.GLADERS));
        if (this == EXPERIMENT) {
            List<UUID> candidates = new ArrayList<>(players);
            Collections.shuffle(candidates, random);
            result.put(candidates.getFirst(), Faction.WCKD);
        }
        return Map.copyOf(result);
    }
}
