package com.panda.themazerunner.game.arena;

import java.util.*;

/** Bounded planar walking proof for template arenas, with forbidden cells for route closures. */
public final class WalkGrid {
    public record Cell(int x, int z) {}

    private final Set<Cell> walkable;

    public WalkGrid(Set<Cell> walkable) {
        if (walkable.isEmpty() || walkable.size() > 16384)
            throw new IllegalArgumentException("Invalid grid size");
        this.walkable = Set.copyOf(walkable);
    }

    public Set<Cell> reachable(Cell start, Set<Cell> closed) {
        if (!walkable.contains(start) || closed.contains(start)) return Set.of();
        Set<Cell> found = new HashSet<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        found.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            Cell cell = queue.remove();
            for (Cell next :
                    List.of(
                            new Cell(cell.x + 1, cell.z),
                            new Cell(cell.x - 1, cell.z),
                            new Cell(cell.x, cell.z + 1),
                            new Cell(cell.x, cell.z - 1)))
                if (walkable.contains(next) && !closed.contains(next) && found.add(next))
                    queue.add(next);
        }
        return Set.copyOf(found);
    }

    public boolean reaches(Cell start, Collection<Cell> goals, Set<Cell> closed) {
        return reachable(start, closed).containsAll(goals);
    }
}
