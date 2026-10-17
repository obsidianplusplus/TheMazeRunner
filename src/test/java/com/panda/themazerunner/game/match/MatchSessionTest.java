package com.panda.themazerunner.game.match;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class MatchSessionTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    private MatchSession started() {
        MatchSession s = new MatchSession(List.of("a", "b"));
        s.join(a);
        s.join(b);
        s.countdown(2);
        s.prepare();
        s.activate();
        return s;
    }

    @Test
    void validatesGraphAndTransitions() {
        assertThrows(IllegalArgumentException.class, () -> new MatchSession(List.of("a", "a")));
        MatchSession s = new MatchSession(List.of("a"));
        assertThrows(IllegalStateException.class, s::activate);
        assertThrows(IllegalStateException.class, () -> s.countdown(1));
        s.join(a);
        assertFalse(s.join(a));
        s.countdown(1);
        s.cancelCountdown();
        assertEquals(MatchSession.Phase.LOBBY, s.phase());
    }

    @Test
    void objectiveOrderMembershipAndNoRepeatedReward() {
        MatchSession s = started();
        assertFalse(s.discover(UUID.randomUUID(), "a"));
        assertFalse(s.discover(a, "b"));
        assertTrue(s.discover(a, "a"));
        assertFalse(s.discover(b, "a"));
        assertFalse(s.extract(a));
        assertTrue(s.discover(b, "b"));
        assertTrue(s.extract(a));
        assertFalse(s.extract(b));
        assertEquals("ESCAPED", s.outcome());
    }

    @Test
    void eliminatedPlayersCannotContributeAndWipeEndsRound() {
        MatchSession s = started();
        s.eliminate(a);
        assertFalse(s.discover(a, "a"));
        s.leave(b);
        assertEquals("TEAM_LOST", s.outcome());
    }

    @Test
    void consecutiveRoundsHaveNoProgressOrMembershipLeak() {
        for (int i = 0; i < 100; i++) {
            MatchSession s = started();
            s.discover(a, "a");
            s.finish("TIMEOUT");
            s.reset();
            assertTrue(s.players().isEmpty());
            assertFalse(s.alive(a));
            MatchSession next = new MatchSession(List.of("a", "b"));
            assertEquals(0, next.progress());
            assertEquals(MatchSession.Phase.LOBBY, next.phase());
        }
    }

    @Test
    void resultsAreIdempotentAndLateCallbacksCannotAward() {
        MatchSession s = started();
        s.finish("TIMEOUT");
        s.finish("ESCAPED");
        assertEquals("TIMEOUT", s.outcome());
        assertFalse(s.discover(a, "a"));
        assertFalse(s.join(UUID.randomUUID()));
        s.reset();
        assertFalse(s.extract(a));
    }
}
