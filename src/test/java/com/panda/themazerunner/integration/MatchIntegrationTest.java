package com.panda.themazerunner.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.panda.themazerunner.TheMazeRunner;
import com.panda.themazerunner.game.match.MatchSession;

import org.bukkit.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** In-process Bukkit simulation. This is not a real Minecraft server or multi-client test. */
class MatchIntegrationTest {
    private static final class SimulationWorld extends WorldMock {
        private final java.util.Map<
                        org.mockbukkit.mockbukkit.world.Coordinate,
                        org.mockbukkit.mockbukkit.block.BlockMock>
                cells = new java.util.HashMap<>();

        @Override
        public org.mockbukkit.mockbukkit.block.BlockMock getBlockAt(
                org.mockbukkit.mockbukkit.world.Coordinate c) {
            return cells.computeIfAbsent(
                    c,
                    at ->
                            new org.mockbukkit.mockbukkit.block.BlockMock(
                                    Material.AIR, new Location(this, at.x, at.y, at.z)) {
                                @Override
                                public boolean isPassable() {
                                    return getType().isAir();
                                }
                            });
        }
    }

    private static final class SimulationPlayer extends PlayerMock {
        SimulationPlayer(ServerMock server, String name) {
            super(server, name);
        }

        @Override
        public void saveData() {
            /* MockBukkit has no player disk format; persistence replay is tested separately. */
        }
    }

    private static final class SimulationServer extends ServerMock {
        @Override
        public boolean dispatchCommand(
                org.bukkit.command.CommandSender sender, String commandLine) {
            if (sender instanceof org.bukkit.entity.Player player) {
                // MockBukkit 4 emits a slashless preprocess event. Real client events start with /.
                var event = new PlayerCommandPreprocessEvent(player, "/" + commandLine);
                getPluginManager().callEvent(event);
                if (event.isCancelled()) return true;
                return getCommandMap().dispatch(sender, event.getMessage().substring(1));
            }
            return super.dispatchCommand(sender, commandLine);
        }
    }

    private ServerMock server;
    private TheMazeRunner plugin;
    private WorldMock world;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock(new SimulationServer());
        org.bukkit.configuration.serialization.ConfigurationSerialization.registerClass(
                org.mockbukkit.mockbukkit.inventory.ItemStackMock.class);
        world = new SimulationWorld();
        world.setName("arena");
        server.addWorld(world);
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) {
                world.getBlockAt(x, 64, z).setType(Material.STONE_BRICKS);
                world.getBlockAt(x, 65, z).setType(Material.AIR);
                world.getBlockAt(x, 66, z).setType(Material.AIR);
            }
        plugin = MockBukkit.load(TheMazeRunner.class);
        var config = plugin.getConfig();
        config.set("diagnostics.stacktraces", true);
        config.set("match.minimum-players", 2);
        config.set("match.countdown-seconds", 3);
        config.set("match.preparation-seconds", 1);
        config.set("match.duration-seconds", 60);
        config.set("match.results-seconds", 2);
        config.set("griever.maximum", 0);
        config.set("match.points.spawn", new Location(world, 0.5, 65, 0.5));
        config.set("match.points.exit", new Location(world, 5.5, 65, 5.5));
        config.set("match.points.route-1", new Location(world, -5.5, 65, -5.5));
        config.set("match.points.route-2", new Location(world, 5.5, 65, -5.5));
        config.set("match.points.relay", new Location(world, 5.5, 65, 0.5));
        config.set("match.points.supply", new Location(world, -5.5, 65, 5.5));
        config.set("match.bounds.min-x", -6);
        config.set("match.bounds.max-x", 6);
        config.set("match.bounds.min-z", -6);
        config.set("match.bounds.max-z", 6);
        plugin.saveConfig();
        server.dispatchCommand(server.getConsoleSender(), "maze validate");
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    private PlayerMock player(String name) {
        PlayerMock p = new SimulationPlayer(server, name);
        server.addPlayer(p);
        p.teleport(new Location(world, 0.5, 65, 0.5));
        return p;
    }

    private void queue(PlayerMock p) {
        p.performCommand("maze");
        p.performCommand("maze join");
    }

    private String messages(PlayerMock p) {
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = p.nextMessage()) != null) out.append(line).append("\n");
        return out.toString();
    }

    private void advance(int seconds) {
        server.getScheduler().performTicks(seconds * 20L);
    }

    @Test
    void startupWithoutVoiceAndAutomaticCountdownCancellation() {
        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getCommand("class"));
        PlayerMock a = player("a"), b = player("b");
        queue(a);
        queue(b);
        assertTrue(plugin.getMatchManager().contains(a), () -> messages(a));
        advance(1);
        assertEquals(MatchSession.Phase.COUNTDOWN, plugin.getMatchManager().phase());
        b.performCommand("maze leave");
        advance(1);
        assertEquals(MatchSession.Phase.LOBBY, plugin.getMatchManager().phase());
        a.performCommand("maze leave");
        assertFalse(plugin.getMatchManager().contains(a));
    }

    @Test
    void roundTimeoutRestoresInventoryAndCanStartAgain() {
        PlayerMock a = player("a"), b = player("b");
        var originalItem = new ItemStack(Material.DIAMOND, 3);
        var meta = originalItem.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Recovery token"));
        var tokenKey = new NamespacedKey(plugin, "recovery-test");
        meta.getPersistentDataContainer()
                .set(tokenKey, org.bukkit.persistence.PersistentDataType.STRING, "before-upgrade");
        originalItem.setItemMeta(meta);
        a.getInventory().setItem(2, originalItem);
        a.setExhaustion(2.5f);
        a.setFlySpeed(0.2f);
        a.setLevel(7);
        a.setFoodLevel(15);
        queue(a);
        queue(b);
        advance(5);
        assertEquals(
                MatchSession.Phase.ACTIVE, plugin.getMatchManager().phase(), () -> messages(a));
        assertFalse(
                java.util.Arrays.stream(a.getInventory().getContents())
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(i -> i.getType() == Material.DIAMOND));
        advance(60);
        assertEquals(MatchSession.Phase.RESULTS, plugin.getMatchManager().phase());
        advance(2);
        assertEquals(MatchSession.Phase.LOBBY, plugin.getMatchManager().phase());
        assertEquals(Material.DIAMOND, a.getInventory().getItem(2).getType());
        assertEquals(3, a.getInventory().getItem(2).getAmount());
        assertEquals(
                originalItem.getItemMeta().displayName(),
                a.getInventory().getItem(2).getItemMeta().displayName());
        assertEquals(
                "before-upgrade",
                a.getInventory()
                        .getItem(2)
                        .getItemMeta()
                        .getPersistentDataContainer()
                        .get(tokenKey, org.bukkit.persistence.PersistentDataType.STRING));
        assertEquals(2.5f, a.getExhaustion());
        assertEquals(0.2f, a.getFlySpeed());
        assertEquals(7, a.getLevel());
        assertEquals(15, a.getFoodLevel());
        queue(a);
        queue(b);
        advance(5);
        assertEquals(MatchSession.Phase.ACTIVE, plugin.getMatchManager().phase());
    }

    private void select(PlayerMock p, int slot) {
        var event =
                new InventoryClickEvent(
                        p.getOpenInventory(),
                        org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                        slot,
                        ClickType.LEFT,
                        InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    private void investigate(PlayerMock p, String id) {
        Location target = plugin.getConfig().getLocation("match.points." + id);
        p.simulatePlayerMove(target);
        var event =
                new PlayerInteractEvent(
                        p,
                        org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                        null,
                        world.getBlockAt(target.getBlockX(), 64, target.getBlockZ()),
                        org.bukkit.block.BlockFace.UP,
                        org.bukkit.inventory.EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
    }

    @Test
    void fullObjectiveChainExtractionAndReset() {
        PlayerMock a = player("a"), b = player("b");
        queue(a);
        queue(b);
        advance(5);
        investigate(a, "route-2");
        assertEquals(MatchSession.Phase.ACTIVE, plugin.getMatchManager().phase());
        investigate(a, "route-1");
        advance(1);
        investigate(b, "route-2");
        advance(1);
        for (int i = 0; i < 4; i++) {
            investigate(a, "relay");
            advance(3);
        }
        assertEquals(MatchSession.Phase.EXTRACTION, plugin.getMatchManager().phase());
        new org.mockbukkit.mockbukkit.simulate.entity.PlayerSimulation(a)
                .simulatePlayerMove(plugin.getConfig().getLocation("match.points.exit"));
        advance(1);
        assertEquals("ESCAPED", plugin.getMatchManager().result());
        advance(2);
        assertEquals(MatchSession.Phase.LOBBY, plugin.getMatchManager().phase());
        assertFalse(plugin.getMatchManager().contains(a));
    }

    @Test
    void medjackSpendsSuppliesOnceAndInvalidBuilderTargetCostsNothing() {
        PlayerMock a = player("a"), b = player("b");
        queue(a);
        select(a, 12);
        queue(b);
        select(b, 13);
        advance(5);
        assertEquals("medjack", plugin.getClassManager().getPlayerClass(a).getId());
        b.setHealth(10);
        int before = plugin.getMatchManager().supplies();
        a.performCommand("maze skill");
        assertEquals(18, b.getHealth(), () -> messages(a));
        assertEquals(before - 3, plugin.getMatchManager().supplies());
        a.performCommand("maze skill");
        assertEquals(before - 3, plugin.getMatchManager().supplies());
        b.performCommand("maze skill");
        assertEquals(before - 3, plugin.getMatchManager().supplies());
    }

    @Test
    void guiShiftClickAndCraftingAreNeverAnEquipmentSource() {
        PlayerMock a = player("a");
        queue(a);
        int before = a.getInventory().getContents().length;
        var event =
                new InventoryClickEvent(
                        a.getOpenInventory(),
                        org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                        10,
                        ClickType.SHIFT_LEFT,
                        InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertTrue(a.getInventory().isEmpty());
        assertEquals(before, a.getInventory().getContents().length);
    }

    @Test
    void invalidConfigurationBlocksAdmission() {
        plugin.getConfig().set("match.duration-seconds", -1);
        plugin.saveConfig();
        server.dispatchCommand(server.getConsoleSender(), "maze validate");
        PlayerMock a = player("a");
        queue(a);
        assertFalse(plugin.getMatchManager().contains(a));
    }

    @Test
    void originalStateJournalRejectsCorruptionWithoutDeletingSnapshot() throws Exception {
        PlayerMock a = player("a");
        queue(a);
        var file =
                plugin.getDataFolder()
                        .toPath()
                        .resolve("players")
                        .resolve(a.getUniqueId() + ".yml");
        String original = java.nio.file.Files.readString(file);
        java.nio.file.Files.writeString(
                file, original.replace("inventory-digest:", "invalid-digest:"));
        a.performCommand("maze leave");
        assertTrue(java.nio.file.Files.exists(file));
        assertTrue(plugin.getMatchManager().contains(a));
        java.nio.file.Files.writeString(file, original);
        a.performCommand("maze leave");
        assertFalse(java.nio.file.Files.exists(file));
        assertFalse(plugin.getMatchManager().contains(a));
    }

    @Test
    void reconnectWithinGraceDoesNotReissueKit() {
        PlayerMock a = player("a"), b = player("b");
        queue(a);
        queue(b);
        advance(5);
        int bread =
                java.util.Arrays.stream(a.getInventory().getContents())
                        .filter(java.util.Objects::nonNull)
                        .filter(i -> i.getType() == Material.BREAD)
                        .mapToInt(ItemStack::getAmount)
                        .sum();
        assertTrue(a.disconnect());
        advance(2);
        assertTrue(a.reconnect());
        int after =
                java.util.Arrays.stream(a.getInventory().getContents())
                        .filter(java.util.Objects::nonNull)
                        .filter(i -> i.getType() == Material.BREAD)
                        .mapToInt(ItemStack::getAmount)
                        .sum();
        assertEquals(bread, after);
        assertTrue(plugin.getMatchManager().active(a));
    }

    @Test
    void reconnectAfterGraceRestoresOriginalInventoryAndCannotRejoinActiveMatch() {
        PlayerMock a = player("a"), b = player("b");
        a.getInventory().setItem(0, new ItemStack(Material.EMERALD, 2));
        queue(a);
        queue(b);
        advance(5);
        a.disconnect();
        advance(31);
        a.reconnect();
        assertFalse(plugin.getMatchManager().contains(a));
        assertEquals(Material.EMERALD, a.getInventory().getItem(0).getType());
        queue(a);
        assertFalse(plugin.getMatchManager().contains(a));
    }

    @Test
    void allDisconnectedPlayersExpireIntoResultsInsteadOfStalling() {
        PlayerMock a = player("a"), b = player("b");
        queue(a);
        queue(b);
        advance(5);
        a.disconnect();
        b.disconnect();
        advance(31);
        assertEquals(MatchSession.Phase.RESULTS, plugin.getMatchManager().phase());
        advance(2);
        assertEquals(MatchSession.Phase.LOBBY, plugin.getMatchManager().phase());
    }

    @Test
    void pluginDisableRestoresOnlineState() {
        PlayerMock a = player("a"), b = player("b");
        a.getInventory().setItem(0, new ItemStack(Material.EMERALD, 4));
        queue(a);
        queue(b);
        advance(5);
        server.getPluginManager().disablePlugin(plugin);
        assertEquals(Material.EMERALD, a.getInventory().getItem(0).getType());
        assertEquals(4, a.getInventory().getItem(0).getAmount());
    }

    @Test
    void guardianAndCookHaveFiniteTeamUtility() {
        PlayerMock a = player("a"), b = player("b");
        queue(a);
        select(a, 11);
        queue(b);
        select(b, 14);
        advance(5);
        a.setFoodLevel(10);
        int supplies = plugin.getMatchManager().supplies();
        a.performCommand("maze skill");
        assertNotNull(b.getPotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE));
        assertEquals(supplies - 2, plugin.getMatchManager().supplies());
        b.performCommand("maze skill");
        assertEquals(14, a.getFoodLevel());
        assertEquals(supplies - 4, plugin.getMatchManager().supplies());
    }

    @Test
    void experimentKeepsFactionPrivateAndMeetingRequiresSnapshotMajority() {
        plugin.getConfig().set("match.mode", "EXPERIMENT");
        plugin.getConfig().set("match.minimum-players", 4);
        plugin.saveConfig();
        server.dispatchCommand(server.getConsoleSender(), "maze validate");
        var party = java.util.List.of(player("a"), player("b"), player("c"), player("d"));
        party.forEach(this::queue);
        advance(5);
        var controller =
                party.stream()
                        .filter(
                                p ->
                                        plugin.getMatchManager().faction(p)
                                                == com.panda.themazerunner.game.rules.ModeRules
                                                        .Faction.WCKD)
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                1,
                party.stream()
                        .filter(
                                p ->
                                        plugin.getMatchManager().faction(p)
                                                == com.panda.themazerunner.game.rules.ModeRules
                                                        .Faction.WCKD)
                        .count());
        party.getFirst().performCommand("maze meeting");
        advance(30);
        for (PlayerMock voter : party)
            if (voter != controller) {
                var inventory = voter.getOpenInventory().getTopInventory();
                int target = -1;
                for (int i = 0; i < 24; i++)
                    if (inventory.getItem(i) != null
                            && net.kyori.adventure.text.serializer.plain
                                    .PlainTextComponentSerializer.plainText()
                                    .serialize(inventory.getItem(i).getItemMeta().displayName())
                                    .equals(controller.getName())) target = i;
                assertTrue(target >= 0);
                select(voter, target);
            }
        advance(15);
        assertFalse(plugin.getMatchManager().active(controller));
        assertEquals(
                MatchSession.Phase.ACTIVE,
                plugin.getMatchManager().phase()); // Meetings do not replace exploration victory.
    }

    @Test
    void physicallyUnreachableRequiredPointPreventsAdmission() {
        for (int x = -6; x <= 6; x++) world.getBlockAt(x, 64, -3).setType(Material.AIR);
        server.dispatchCommand(server.getConsoleSender(), "maze validate");
        PlayerMock a = player("a");
        queue(a);
        assertFalse(plugin.getMatchManager().contains(a));
    }

    @Test
    void deathRespawnAndDelayedRecoveryDoNotLeaveNextRoundLocked() {
        PlayerMock a = player("a"), b = player("b");
        queue(a);
        queue(b);
        advance(5);
        a.setHealth(0);
        a.respawn();
        advance(1);
        assertFalse(plugin.getMatchManager().active(a));
        assertEquals(GameMode.SPECTATOR, a.getGameMode());
        b.setHealth(0);
        advance(2);
        assertEquals(MatchSession.Phase.LOBBY, plugin.getMatchManager().phase());
        b.respawn();
        advance(1);
        assertFalse(plugin.getMatchManager().contains(b));
        queue(a);
        queue(b);
        advance(1);
        assertEquals(MatchSession.Phase.COUNTDOWN, plugin.getMatchManager().phase());
    }

    @Test
    void regularPlayersCannotModifyMapOrLeaveThroughCommands() {
        PlayerMock a = player("a");
        assertFalse(a.hasPermission("tmr.admin"));
        queue(a);
        assertTrue(plugin.getMatchManager().contains(a), () -> messages(a));
        BlockBreakEvent broken = new BlockBreakEvent(world.getBlockAt(0, 64, 0), a);
        server.getPluginManager().callEvent(broken);
        assertTrue(broken.isCancelled());
        PlayerCommandPreprocessEvent command =
                new PlayerCommandPreprocessEvent(a, "/minecraft:give a diamond 64");
        server.getPluginManager().callEvent(command);
        assertTrue(command.isCancelled());
    }
}
