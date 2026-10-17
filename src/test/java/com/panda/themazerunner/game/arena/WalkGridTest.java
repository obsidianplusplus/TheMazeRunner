package com.panda.themazerunner.game.arena;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class WalkGridTest {
    @Test
    void templateObjectivesRemainReachableWhenOptionalBridgeCloses() {
        Set<WalkGrid.Cell> cells = new HashSet<>();
        for (int x = -19; x <= 19; x++)
            for (int z = -19; z <= 19; z++)
                if (ArenaTemplate.walkable(x, z)) cells.add(new WalkGrid.Cell(x, z));
        var grid = new WalkGrid(cells);
        var start = new WalkGrid.Cell(0, 0);
        var goals =
                List.of(
                        new WalkGrid.Cell(-16, -16),
                        new WalkGrid.Cell(16, -16),
                        new WalkGrid.Cell(16, 8),
                        new WalkGrid.Cell(-8, 16),
                        new WalkGrid.Cell(0, 17));
        Set<WalkGrid.Cell> gates =
                Set.of(new WalkGrid.Cell(8, -1), new WalkGrid.Cell(8, 0), new WalkGrid.Cell(8, 1));
        assertTrue(grid.reaches(start, goals, Set.of()));
        assertTrue(grid.reaches(start, goals, gates));
        for (var cell : grid.reachable(start, gates)) assertTrue(grid.reaches(cell, goals, gates));
    }

    @Test
    void deadEndClosureAndClosedStartFailSafely() {
        var a = new WalkGrid.Cell(0, 0);
        var b = new WalkGrid.Cell(1, 0);
        var c = new WalkGrid.Cell(2, 0);
        var grid = new WalkGrid(Set.of(a, b, c));
        assertTrue(grid.reaches(a, List.of(c), Set.of()));
        assertFalse(grid.reaches(a, List.of(c), Set.of(b)));
        assertTrue(grid.reachable(a, Set.of(a)).isEmpty());
    }
}
