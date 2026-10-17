package com.panda.themazerunner.game.match;

import java.util.*;
import java.util.function.Predicate;

/** Rules shared by the legacy adapter and tests; null candidate is an explicit abstention. */
public final class VoteRules {
    private VoteRules() {}

    public static boolean mayCast(
            boolean open, Set<UUID> eligible, Map<UUID, ?> received, UUID voter) {
        return open && eligible.contains(voter) && !received.containsKey(voter);
    }

    public static UUID nominee(Map<UUID, UUID> votes, Set<UUID> eligible, Predicate<UUID> online) {
        Map<UUID, Integer> counts = new HashMap<>();
        int abstentions = 0;
        for (var vote : votes.entrySet()) {
            if (!eligible.contains(vote.getKey())) continue;
            UUID target = vote.getValue();
            if (target == null) {
                abstentions++;
                continue;
            }
            if (eligible.contains(target) && online.test(target))
                counts.merge(target, 1, Integer::sum);
        }
        int max = abstentions;
        UUID selected = null;
        for (var count : counts.entrySet()) {
            if (count.getValue() > max) {
                max = count.getValue();
                selected = count.getKey();
            } else if (count.getValue() == max) selected = null;
        }
        return selected;
    }
}
