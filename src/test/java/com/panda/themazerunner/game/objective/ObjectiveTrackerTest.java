package com.panda.themazerunner.game.objective;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class ObjectiveTrackerTest {
    private ObjectiveDefinition d(String id, Set<String> parents, int work, boolean required) {
        return new ObjectiveDefinition(
                id, id, ObjectiveDefinition.Type.REPAIR, id, parents, work, 2, required);
    }

    @Test
    void rejectsMissingAndCyclicDefinitions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObjectiveTracker(List.of(d("a", Set.of("missing"), 1, true))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ObjectiveTracker(
                                List.of(
                                        d("a", Set.of("b"), 1, true),
                                        d("b", Set.of("a"), 1, true))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObjectiveTracker(List.of(d("a", Set.of(), 1, false))));
    }

    @Test
    void forkJoinDependenciesAndOptionalSupplyDoNotBlockExtraction() {
        var t =
                new ObjectiveTracker(
                        List.of(
                                d("a", Set.of(), 2, true),
                                d("b", Set.of(), 1, true),
                                d("c", Set.of("a", "b"), 3, true),
                                d("supply", Set.of(), 1, false)));
        assertFalse(t.available("c"));
        assertFalse(t.advance("a", 1));
        assertEquals(1, t.work("a"));
        assertFalse(t.advance("c", 99));
        assertTrue(t.advance("a", 2));
        assertTrue(t.advance("b", 1));
        assertTrue(t.available("c"));
        assertFalse(t.readyToExtract());
        assertTrue(t.advance("c", 3));
        assertTrue(t.readyToExtract());
        assertFalse(t.advance("c", 3));
        assertEquals(3, t.work("c"));
        assertEquals(3, t.completedCount());
    }

    @Test
    void invalidAmountsNeverAdvanceOrReward() {
        var t = new ObjectiveTracker(ObjectiveTracker.chain(List.of("a")));
        assertFalse(t.advance("a", 0));
        assertFalse(t.advance("a", -1));
        assertEquals(0, t.work("a"));
    }

    @Test
    void perObjectiveDeadlinesStartWhenUnlockedAndCascadeFailure() {
        var a =
                new ObjectiveDefinition(
                        "a", "a", ObjectiveDefinition.Type.CLUE, "a", Set.of(), 1, 1, true, 5);
        var b =
                new ObjectiveDefinition(
                        "b", "b", ObjectiveDefinition.Type.REPAIR, "b", Set.of("a"), 1, 1, true, 5);
        var t = new ObjectiveTracker(List.of(a, b));
        assertTrue(t.tick(0).isEmpty());
        assertTrue(t.tick(4).isEmpty());
        assertEquals(Set.of("a", "b"), t.tick(5));
        assertTrue(t.requiredFailure());
        assertFalse(t.advance("a", 1));
        var next = new ObjectiveTracker(List.of(a, b));
        next.tick(0);
        next.advance("a", 1);
        assertTrue(next.tick(4).isEmpty());
        assertTrue(next.tick(8).isEmpty());
        assertEquals(Set.of("b"), next.tick(9));
    }
}
