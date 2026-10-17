package com.panda.themazerunner.listeners;

import com.panda.themazerunner.gate.GateManager;
import com.panda.themazerunner.manager.LanguageManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 大门监听器
 * <p>
 * 监听玩家使用选区工具的交互事件。
 */
public class GateListener implements Listener {

    private final GateManager gateManager;
    private final LanguageManager languageManager;

    /**
     * 构造函数
     *
     * @param gateManager     大门管理器
     * @param languageManager 语言管理器
     */
    public GateListener(GateManager gateManager, LanguageManager languageManager) {
        this.gateManager = gateManager;
        this.languageManager = languageManager;
    }

    /**
     * 处理玩家交互事件
     *
     * @param event 交互事件
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getPlayer().hasPermission("tmr.admin")) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.WOODEN_AXE) {
            return;
        }

        // 过滤副手交互
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }

        // 检查 PDC 标记
        if (item.hasItemMeta()) {
            org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(
                    com.panda.themazerunner.TheMazeRunner.getInstance(), "gate_wand");
            if (!pdc.has(key, org.bukkit.persistence.PersistentDataType.BYTE)) {
                return;
            }
        } else {
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            gateManager.setPlayerSelection(event.getPlayer().getUniqueId(), 0, event.getClickedBlock().getLocation());
            event.getPlayer().sendMessage(languageManager.getMessage("gate.select_pos1").replace("%location%",
                    formatLoc(event.getClickedBlock().getLocation())));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            gateManager.setPlayerSelection(event.getPlayer().getUniqueId(), 1, event.getClickedBlock().getLocation());
            event.getPlayer().sendMessage(languageManager.getMessage("gate.select_pos2").replace("%location%",
                    formatLoc(event.getClickedBlock().getLocation())));
        }
    }

    private String formatLoc(org.bukkit.Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    /**
     * 处理玩家退出事件，清理缓存数据
     *
     * @param event 退出事件
     */
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        gateManager.clearPlayerData(event.getPlayer().getUniqueId());
    }
}
