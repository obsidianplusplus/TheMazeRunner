package com.panda.themazerunner.game.arena;

import com.panda.themazerunner.TheMazeRunner;

import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/** One finite, deterministic template. Never overwrites an existing world. */
public final class ArenaTemplate {
    private ArenaTemplate() {}

    public static final String WORLD = "tmr_arena";

    public static boolean walkable(int x, int z) {
        if (Math.abs(x) > 19 || Math.abs(z) > 19) return false;
        return Math.abs(x) <= 4 && Math.abs(z) <= 4
                || Math.abs(x) <= 1
                || Math.abs(z) <= 1
                || Math.abs(Math.abs(x) - 8) <= 1
                || Math.abs(Math.abs(z) - 8) <= 1
                || Math.abs(Math.abs(x) - 16) <= 1
                || Math.abs(Math.abs(z) - 16) <= 1;
    }

    public static void create(TheMazeRunner plugin, Consumer<String> result) {
        if (new File(Bukkit.getWorldContainer(), WORLD).exists() || Bukkit.getWorld(WORLD) != null)
            throw new IllegalArgumentException(
                    "tmr_arena already exists; template never overwrites a world");
        World world =
                new WorldCreator(WORLD)
                        .generator(new ChunkGenerator() {})
                        .generateStructures(false)
                        .createWorld();
        if (world == null) throw new IllegalStateException("Cannot create template world");
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setTime(1000);
        world.setSpawnLocation(0, 65, 0);
        // ChunkGenerator's empty template is built incrementally; no player may queue before
        // configuration commits.
        new BukkitRunnable() {
            int cursor;

            public void run() {
                for (int batch = 0; batch < 400 && cursor < 41 * 41 * 6; batch++, cursor++) {
                    int layer = cursor / (41 * 41),
                            n = cursor % (41 * 41),
                            x = n / 41 - 20,
                            z = n % 41 - 20;
                    Material type =
                            layer == 0
                                    ? Material.STONE_BRICKS
                                    : walkable(x, z) ? Material.AIR : Material.MOSSY_STONE_BRICKS;
                    world.getBlockAt(x, 64 + layer, z).setType(type, false);
                }
                if (cursor < 41 * 41 * 6) return;
                cancel();
                var config = plugin.getConfig();
                config.set("match.world", WORLD);
                Map<String, int[]> points = new LinkedHashMap<>();
                points.put("spawn", new int[] {0, 0});
                points.put("exit", new int[] {0, 17});
                points.put("route-1", new int[] {-16, -16});
                points.put("route-2", new int[] {16, -16});
                points.put("relay", new int[] {16, 8});
                points.put("supply", new int[] {-8, 16});
                points.put("hunter-1", new int[] {0, -16});
                points.put("hunter-2", new int[] {16, 0});
                for (var entry : points.entrySet()) {
                    Location loc =
                            new Location(
                                    world,
                                    entry.getValue()[0] + 0.5,
                                    65,
                                    entry.getValue()[1] + 0.5);
                    config.set(
                            "match.points." + entry.getKey(),
                            com.panda.themazerunner.persistence.Locations.encode(loc));
                    if (!entry.getKey().startsWith("hunter") && !entry.getKey().equals("spawn"))
                        world.getBlockAt(loc.getBlockX(), 64, loc.getBlockZ())
                                .setType(Material.CHISELED_STONE_BRICKS, false);
                }
                config.set("match.bounds.min-x", -19);
                config.set("match.bounds.max-x", 19);
                config.set("match.bounds.min-z", -19);
                config.set("match.bounds.max-z", 19);
                // Optional bridge crossing: all required targets have an alternate route around it.
                config.set("match.gates", List.of("tmr_route"));
                if (plugin.getGateManager().getGate("tmr_route") == null) {
                    for (int z = -1; z <= 1; z++)
                        for (int y = 65; y <= 68; y++)
                            world.getBlockAt(8, y, z).setType(Material.STONE_BRICKS, false);
                    var gate =
                            plugin.getGateManager()
                                    .createGate(
                                            "tmr_route",
                                            new com.panda.themazerunner.utils.Region(
                                                    new Location(world, 8, 65, -1),
                                                    new Location(world, 8, 68, 0)),
                                            new com.panda.themazerunner.utils.Region(
                                                    new Location(world, 8, 65, 1),
                                                    new Location(world, 8, 68, 1)),
                                            3,
                                            2);
                    gate.setAxis(com.panda.themazerunner.gate.Gate.Axis.Z);
                    plugin.getGateManager().saveGates();
                }
                plugin.saveConfig();
                world.save();
                result.accept("模板就绪：/maze validate 检查后可加入；世界 tmr_arena，范围 -19..19，脚部Y=65。");
            }
        }.runTaskTimer(plugin, 1, 1);
    }
}
