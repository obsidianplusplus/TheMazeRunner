package com.panda.themazerunner.game.rules;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class MeetingRoundTest {
    private final UUID a = UUID.randomUUID(),
            b = UUID.randomUUID(),
            c = UUID.randomUUID(),
            d = UUID.randomUUID();

    @Test
    void electorateTimingAndOneVoteAreAuthoritative() {
        var m = new MeetingRound(List.of(a, b, c), 0, 30, 15);
        assertFalse(m.vote(a, b, 29));
        assertFalse(m.vote(d, b, 30));
        assertFalse(m.vote(a, d, 30));
        assertTrue(m.vote(a, b, 30));
        assertFalse(m.vote(a, c, 31));
        assertTrue(m.vote(c, b, 44));
        assertEquals(b, m.result());
        assertTrue(m.ended(45));
        assertFalse(m.vote(b, a, 45));
    }

    @Test
    void leavingDoesNotLowerQuorumAndCandidatesCannotRemainOffline() {
        var m = new MeetingRound(List.of(a, b, c, d), 0, 30, 15);
        m.vote(a, b, 30);
        m.vote(c, b, 30);
        m.leave(d);
        assertNull(m.result());
        m.leave(b);
        assertNull(m.result());
    }

    @Test
    void abstentionAndTieAreHarmless() {
        var m = new MeetingRound(List.of(a, b, c), 0, 1, 15);
        m.vote(a, b, 1);
        m.vote(b, a, 1);
        m.vote(c, null, 1);
        assertNull(m.result());
    }
}
