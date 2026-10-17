package com.panda.themazerunner.game.arena;

import com.panda.themazerunner.game.objective.ObjectiveDefinition;
import com.panda.themazerunner.game.rules.GameSettings;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/** A finite, level map; validates real foot/head/floor cells once, not in movement callbacks. */
public final class ArenaMap {
    private final Map<String, Location> points = new LinkedHashMap<>();
    private final World world;
    private final int minX, maxX, minZ, maxZ, y;
    private WalkGrid grid;

    public ArenaMap(ConfigurationSection config) {
        ConfigurationSection p = config.getConfigurationSection("match.points");
        if (p == null)
            throw new IllegalArgumentException(
                    "Missing match.points; use /maze template or configure a map");
        for (String id : p.getKeys(false)) {
            Location location = com.panda.themazerunner.persistence.Locations.read(p, id);
            if (location == null || location.getWorld() == null)
                throw new IllegalArgumentException("Invalid point " + id);
            points.put(id, location.clone());
        }
        if (!points.containsKey("spawn") || !points.containsKey("exit"))
            throw new IllegalArgumentException("Missing spawn or exit");
        world = points.get("spawn").getWorld();
        y = points.get("spawn").getBlockY();
        if (points.values().stream()
                .anyMatch(l -> !world.equals(l.getWorld()) || l.getBlockY() != y))
            throw new IllegalArgumentException(
                    "All points must be on the same world and foot Y (planar template)");
        ConfigurationSection b = config.getConfigurationSection("match.bounds");
        if (b == null)
            throw new IllegalArgumentException(
                    "Missing match.bounds (bounded physical path validation is required)");
        minX = GameSettings.integer(b, "min-x", 0, -30000000, 30000000);
        maxX = GameSettings.integer(b, "max-x", 0, minX, minX + 127);
        minZ = GameSettings.integer(b, "min-z", 0, -30000000, 30000000);
        maxZ = GameSettings.integer(b, "max-z", 0, minZ, minZ + 127);
        if (points.values().stream().anyMatch(l -> !contains(l)))
            throw new IllegalArgumentException("Point outside map bounds");
    }

    public World world() {
        return world;
    }

    public Location point(String id) {
        Location point = points.get(id);
        if (point == null) throw new IllegalArgumentException("Missing point: " + id);
        return point.clone();
    }

    public boolean has(String id) {
        return points.containsKey(id);
    }

    public boolean contains(Location loc) {
        return world.equals(loc.getWorld())
                && loc.getBlockX() >= minX
                && loc.getBlockX() <= maxX
                && loc.getBlockZ() >= minZ
                && loc.getBlockZ() <= maxZ
                && loc.getY() >= y - 2
                && loc.getY() <= y + 8;
    }

    public boolean glade(Location loc) {
        Location home = point("spawn");
        return world.equals(loc.getWorld()) && home.distanceSquared(loc) <= 36;
    }

    private boolean safeCell(int x, int z) {
        var feet = world.getBlockAt(x, y, z);
        var head = world.getBlockAt(x, y + 1, z);
        var floor = world.getBlockAt(x, y - 1, z);
        return feet.isPassable()
                && head.isPassable()
                && floor.getType().isSolid()
                && !Set.of(
                                Material.LAVA,
                                Material.FIRE,
                                Material.CACTUS,
                                Material.MAGMA_BLOCK,
                                Material.CAMPFIRE,
                                Material.SOUL_CAMPFIRE,
                                Material.WATER,
                                Material.POWDER_SNOW)
                        .contains(floor.getType())
                && !feet.isLiquid()
                && feet.getType() != Material.FIRE
                && feet.getType() != Material.POWDER_SNOW;
    }

    public void validate(List<ObjectiveDefinition> definitions, Collection<WalkGrid.Cell> blocked) {
        Set<WalkGrid.Cell> cells = new HashSet<>();
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                if (safeCell(x, z)) cells.add(new WalkGrid.Cell(x, z));
        for (WalkGrid.Cell gate : blocked) {
            if (gate.x() < minX
                    || gate.x() > maxX
                    || gate.z() < minZ
                    || gate.z() > maxZ
                    || !world.getBlockAt(gate.x(), y - 1, gate.z()).getType().isSolid())
                throw new IllegalArgumentException("Gate footprint outside bounded walk grid");
            cells.add(gate);
        }
        grid = new WalkGrid(cells);
        List<WalkGrid.Cell> goals = new ArrayList<>();
        goals.add(cell(point("exit")));
        for (var d : definitions) goals.add(cell(point(d.point())));
        if (!grid.reaches(cell(point("spawn")), goals, Set.of()))
            throw new IllegalArgumentException("Map has unreachable objectives or exit");
        if (!grid.reaches(cell(point("spawn")), goals, Set.copyOf(blocked)))
            throw new IllegalArgumentException("Gate closure would strand a required point");
    }

    public boolean canClose(
            Collection<WalkGrid.Cell> cells,
            Collection<Location> alive,
            List<ObjectiveDefinition> definitions) {
        if (grid == null) return false;
        List<WalkGrid.Cell> goals = new ArrayList<>();
        goals.add(cell(point("exit")));
        goals.add(cell(point("spawn")));
        for (var d : definitions) goals.add(cell(point(d.point())));
        for (Location player : alive)
            if (!grid.reaches(cell(player), goals, Set.copyOf(cells))) return false;
        return true;
    }

    public static WalkGrid.Cell cell(Location l) {
        return new WalkGrid.Cell(l.getBlockX(), l.getBlockZ());
    }
}
