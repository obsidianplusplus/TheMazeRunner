package com.panda.themazerunner.game.match;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class VoteRulesTest {
    private final UUID a = UUID.randomUUID(),
            b = UUID.randomUUID(),
            c = UUID.randomUUID(),
            outsider = UUID.randomUUID();

    @Test
    void closedOutsiderAndDuplicateAbstentionCannotVote() {
        Set<UUID> eligible = Set.of(a, b);
        Map<UUID, UUID> votes = new HashMap<>();
        assertTrue(VoteRules.mayCast(true, eligible, votes, a));
        assertFalse(VoteRules.mayCast(false, eligible, votes, a));
        assertFalse(VoteRules.mayCast(true, eligible, votes, outsider));
        votes.put(a, null);
        assertFalse(VoteRules.mayCast(true, eligible, votes, a));
    }

    @Test
    void abstentionMajorityTieAndOfflineCandidatesDoNotAccuse() {
        Map<UUID, UUID> votes = new HashMap<>();
        Set<UUID> eligible = Set.of(a, b, c);
        votes.put(a, b);
        votes.put(b, null);
        votes.put(c, null);
        assertNull(VoteRules.nominee(votes, eligible, id -> true));
        votes.put(c, b);
        assertEquals(b, VoteRules.nominee(votes, eligible, id -> true));
        assertNull(VoteRules.nominee(votes, eligible, id -> false));
        votes.put(b, a);
        votes.remove(c);
        assertNull(VoteRules.nominee(votes, eligible, id -> true));
    }

    @Test
    void forgedVoterOrCandidateNeverCounts() {
        Set<UUID> eligible = Set.of(a, b);
        Map<UUID, UUID> votes = new HashMap<>();
        votes.put(outsider, b);
        votes.put(a, outsider);
        assertNull(VoteRules.nominee(votes, eligible, id -> true));
    }
}
