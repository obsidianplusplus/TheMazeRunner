package com.panda.themazerunner.game.rules;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.*;

class GameRulesTest {
    @Test
    void resourcesCooldownAndCapsPreventInfiniteSkills() {
        UUID id = UUID.randomUUID();
        var b = new AbilityBudget(5, 10);
        assertTrue(b.spend(id, "heal", 3, 20, 0));
        assertFalse(b.spend(id, "heal", 3, 20, 19));
        b.award(100);
        assertEquals(10, b.supplies());
        assertFalse(b.spend(id, "heal", 3, 20, 19));
        assertTrue(b.spend(id, "heal", 3, 20, 20));
        assertEquals(7, b.supplies());
        assertThrows(IllegalArgumentException.class, () -> b.spend(id, "heal", -1, 1, 20));
    }

    @Test
    void everyScheduledClosureHasAFullWarningWindow() {
        var e = new EnvironmentCycle(180, 90, 10, 120, 30);
        for (int n = 1; n < 100; n++)
            for (int t = n * 120 - 10; t < n * 120; t++)
                assertEquals(EnvironmentCycle.Event.WARNING, e.at(t).event());
        assertEquals(EnvironmentCycle.Event.CLOSED, e.at(120).event());
        assertEquals(EnvironmentCycle.Event.NONE, e.at(150).event());
        assertFalse(e.at(179).night());
        assertTrue(e.at(170).duskWarning());
        assertTrue(e.at(180).night());
        assertFalse(e.at(270).night());
        assertThrows(
                IllegalArgumentException.class, () -> new EnvironmentCycle(180, 90, 10, 30, 30));
    }

    @Test
    void grieverSignalsBeforeAttackingLosesTargetAndReturns() {
        var brain = new GrieverBrain();
        assertFalse(brain.attack(1, true, 0));
        assertEquals(GrieverBrain.State.SENSE, brain.step(true, true, 0));
        assertFalse(brain.attack(1, true, 1));
        assertEquals(GrieverBrain.State.CHASE, brain.step(true, true, 2));
        assertTrue(brain.attack(1, true, 2));
        assertFalse(brain.attack(1, true, 3));
        assertFalse(brain.attack(1, false, 4));
        assertEquals(GrieverBrain.State.SEARCH, brain.step(true, false, 4));
        assertEquals(GrieverBrain.State.PATROL, brain.step(true, false, 10));
        assertEquals(GrieverBrain.State.RETURN, brain.step(false, true, 11));
        assertFalse(brain.attack(1, true, 11));
    }

    @Test
    void newTargetReceivesItsOwnTelegraph() {
        var brain = new GrieverBrain();
        brain.step(true, true, 0);
        brain.step(true, true, 2);
        brain.retarget(3);
        assertFalse(brain.attack(1, true, 3));
        assertEquals(GrieverBrain.State.SENSE, brain.step(true, true, 4));
        assertEquals(GrieverBrain.State.CHASE, brain.step(true, true, 5));
    }

    @Test
    void profileAndConfigurationFailClosed() {
        var config = new YamlConfiguration();
        assertEquals(ModeRules.CORE, GameSettings.load(config).mode());
        config.set("match.minimum-players", "2");
        assertThrows(IllegalArgumentException.class, () -> GameSettings.load(config));
        config.set("match.minimum-players", 2);
        config.set("match.mode", "EXPERIMENT");
        assertThrows(IllegalArgumentException.class, () -> GameSettings.load(config));
        config.set("match.minimum-players", 4);
        assertEquals(ModeRules.EXPERIMENT, GameSettings.load(config).mode());
        config.set("match.profile", "HYBRID");
        assertThrows(IllegalArgumentException.class, () -> GameSettings.load(config));
    }

    @Test
    void factionsArePerRoundAndRequireAdequatePlayers() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) ids.add(UUID.randomUUID());
        assertEquals(
                0,
                ModeRules.CORE.assign(ids, new Random(1)).values().stream()
                        .filter(f -> f == ModeRules.Faction.WCKD)
                        .count());
        assertEquals(
                1,
                ModeRules.EXPERIMENT.assign(ids, new Random(1)).values().stream()
                        .filter(f -> f == ModeRules.Faction.WCKD)
                        .count());
        assertThrows(
                IllegalStateException.class,
                () -> ModeRules.EXPERIMENT.assign(ids.subList(0, 3), new Random()));
    }

    @Test
    void bundledProfilesHaveDifferentExplicitPuzzleDependencies() throws Exception {
        var yaml =
                YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(
                                java.util.Objects.requireNonNull(
                                        getClass()
                                                .getClassLoader()
                                                .getResourceAsStream("config.yml")),
                                java.nio.charset.StandardCharsets.UTF_8));
        var film = GameSettings.load(yaml);
        assertTrue(
                film.objectives().stream()
                        .filter(d -> d.id().equals("route-2"))
                        .findFirst()
                        .orElseThrow()
                        .prerequisites()
                        .contains("route-1"));
        yaml.set("match.profile", "NOVEL");
        var novel = GameSettings.load(yaml);
        assertEquals(
                Set.of("map-west", "map-east"),
                novel.objectives().stream()
                        .filter(d -> d.id().equals("overlay"))
                        .findFirst()
                        .orElseThrow()
                        .prerequisites());
    }

    @Test
    void boundedLongSessionDoesNotCreateResourcesFromRepeatedInput() {
        var budget = new AbilityBudget(12, 64);
        var brains = new GrieverBrain[8];
        for (int i = 0; i < 8; i++) brains[i] = new GrieverBrain();
        UUID[] players = new UUID[64];
        for (int i = 0; i < players.length; i++) players[i] = UUID.randomUUID();
        int successful = 0;
        long start = System.nanoTime();
        for (int second = 0; second < 900; second++) {
            for (UUID player : players)
                if (budget.spend(player, "ability", 2, 30, second)) successful++;
            for (var brain : brains) {
                brain.step(second % 270 >= 180, second % 7 != 0, second);
                brain.attack(2, true, second);
            }
        }
        assertEquals(6, successful);
        assertEquals(0, budget.supplies());
        System.out.println(
                "Bounded rules workload: 64 actors, 8 brains, 900 seconds in "
                        + ((System.nanoTime() - start) / 1_000_000.0)
                        + "ms (not a server TPS benchmark)");
    }
}
