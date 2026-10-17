package com.panda.themazerunner.manager;

import static org.junit.jupiter.api.Assertions.*;

import com.panda.themazerunner.game.classes.GameClass;

import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

/** Narrow interface fakes exercise inventory ownership without pretending to run Paper. */
class ClassManagerTest {
    private static final class SentinelItem extends ItemStack {
        SentinelItem() {
            super();
        }
    }

    @Test
    void switchingAndRepeatedRemovalRestoreAllInventorySlots() {
        ItemStack[][] contents = {new ItemStack[41]};
        ItemStack original = new SentinelItem(),
                offhand = new SentinelItem(),
                kit = new SentinelItem();
        contents[0][0] = original;
        contents[0][40] = offhand;
        PlayerInventory inventory =
                (PlayerInventory)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class[] {PlayerInventory.class},
                                (p, m, a) -> {
                                    switch (m.getName()) {
                                        case "getContents":
                                            return contents[0].clone();
                                        case "clear":
                                            Arrays.fill(contents[0], null);
                                            return null;
                                        case "setContents":
                                            contents[0] = ((ItemStack[]) a[0]).clone();
                                            return null;
                                        case "setItem":
                                            contents[0][(int) a[0]] = (ItemStack) a[1];
                                            return null;
                                        default:
                                            return null;
                                    }
                                });
        UUID id = UUID.randomUUID();
        Player player =
                (Player)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class[] {Player.class},
                                (p, m, a) ->
                                        switch (m.getName()) {
                                            case "getUniqueId" -> id;
                                            case "getInventory" -> inventory;
                                            default -> null;
                                        });
        ClassManager manager = new ClassManager(null);
        int[] equipped = {0}, unequipped = {0};
        manager.registerClass(
                new GameClass(null, "test") {
                    public void onEquip(Player p) {
                        equipped[0]++;
                        p.getInventory().setItem(1, kit);
                    }

                    public void onUnequip(Player p) {
                        unequipped[0]++;
                    }
                });
        assertTrue(manager.equipClass(player, "test"));
        assertNull(contents[0][0]);
        assertTrue(manager.equipClass(player, "test"));
        manager.unequipClass(player);
        manager.unequipClass(player);
        assertSame(original, contents[0][0]);
        assertNull(contents[0][1]);
        assertSame(offhand, contents[0][40]);
        assertEquals(2, equipped[0]);
        assertEquals(2, unequipped[0]);
        assertNull(manager.getPlayerClass(player));
        manager.registerClass(
                new GameClass(null, "broken") {
                    public void onEquip(Player p) {
                        p.getInventory().setItem(1, kit);
                        throw new IllegalStateException("partial equip");
                    }

                    public void onUnequip(Player p) {}
                });
        assertFalse(manager.equipClass(player, "broken"));
        assertSame(original, contents[0][0]);
        assertSame(offhand, contents[0][40]);
        assertNull(contents[0][1]);
        assertNull(manager.getPlayerClass(player));
    }
}
