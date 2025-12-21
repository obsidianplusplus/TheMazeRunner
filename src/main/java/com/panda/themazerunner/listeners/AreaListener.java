package com.panda.themazerunner.listeners;

import com.panda.themazerunner.game.area.AreaManager;
import com.panda.themazerunner.game.identity.Identity;
import com.panda.themazerunner.game.identity.IdentityManager;
import com.panda.themazerunner.manager.LanguageManager;
import com.panda.themazerunner.utils.Region;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import io.papermc.paper.event.player.AsyncChatEvent;

public class AreaListener implements Listener {

    private final AreaManager areaManager;
    private final IdentityManager identityManager;
    private final LanguageManager languageManager;

    public AreaListener(AreaManager areaManager, IdentityManager identityManager, LanguageManager languageManager) {
        this.areaManager = areaManager;
        this.identityManager = identityManager;
        this.languageManager = languageManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // 1. 区域选点工具逻辑
        ItemStack item = event.getItem();
        boolean isWand = false;
        if (item != null && item.getType() == Material.WOODEN_HOE) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(
                    new org.bukkit.NamespacedKey(com.panda.themazerunner.TheMazeRunner.getInstance(), "area_wand"),
                    PersistentDataType.BYTE)) {
                isWand = true;
            }
        }

        if (isWand) {
            event.setCancelled(true);
            Location loc = event.getClickedBlock().getLocation();
            int index = (event.getAction() == Action.LEFT_CLICK_BLOCK) ? 0 : 1;

            areaManager.setPlayerSelection(event.getPlayer().getUniqueId(), index, loc);

            String msgKey = (index == 0) ? "area.pos1_set" : "area.pos2_set";
            event.getPlayer().sendMessage(languageManager.getMessage(msgKey)
                    .replace("%location%", String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ())));
            return;
        }

        // 2. 集结钟限制逻辑
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Location bellLoc = areaManager.getGatheringBellLocation();
            if (bellLoc != null && clickedBlock.getLocation().equals(bellLoc)) {
                // 如果是 WCKD 身份，阻止敲钟
                if (identityManager.hasIdentity(event.getPlayer(), Identity.WCKD)) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(languageManager.getMessage("area.wckd_cannot_ring_bell"));
                    return;
                }

                // 检查是否有敲钟权 (全局)
                if (!areaManager.hasRingRight()) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(languageManager.getMessage("area.no_bell_right"));
                    return;
                }

                // 消耗敲钟权并触发会议 (尝试消耗全局权限)
                if (areaManager.consumeRingRight()) {
                    areaManager.startMeeting();
                    event.getPlayer().sendMessage(languageManager.getMessage("area.bell_ring_success"));
                } else {
                    // 没抢到权限 (并发情况或双重检查失败)
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(languageManager.getMessage("area.no_bell_right"));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Region glade = areaManager.getGladeRegion();

        // --- 1. 流浪者禁入林间空地逻辑 ---
        if (glade != null && !areaManager.isWandererAccessAllowed()) {
            Player p = event.getPlayer();
            if (identityManager.hasIdentity(p, Identity.WANDERER)) {
                // 如果试图进入林间空地
                if (!glade.contains(event.getFrom()) && glade.contains(event.getTo())) {
                    event.setCancelled(true);
                    p.sendMessage(languageManager.getMessage("area.wanderer_restricted"));

                    // 可选：给一个反向力把它弹出去一点，防止卡边缘
                    Vector direction = event.getFrom().toVector().subtract(event.getTo().toVector()).normalize()
                            .multiply(0.5);
                    p.setVelocity(direction.setY(0.2)); // 稍微跳一下
                    return;
                }
            }
        }

        // --- 2. 会议期间逻辑 ---
        if (areaManager.isMeetingInProgress()) {

            // 归还阶段逻辑 (Return Phase Logic)
            if (areaManager.isReturnToGladePhase() && glade != null) {
                // 如果之前不在林间空地，现在进入了林间空地
                if (!glade.contains(event.getFrom()) && glade.contains(event.getTo())) {
                    Player p = event.getPlayer();
                    // 流浪者不受强制归还影响
                    if (!identityManager.hasIdentity(p, Identity.WANDERER)) {
                        areaManager.teleportAndJoinMeeting(p);
                        return; // 传送后不需要额外处理移动
                    }
                }
            }

            // 现有逻辑: 限制已经在会议中的玩家离开
            if (areaManager.isPlayerInMeeting(event.getPlayer())) {
                if (event.getFrom().getX() != event.getTo().getX() ||
                        event.getFrom().getY() != event.getTo().getY() ||
                        event.getFrom().getZ() != event.getTo().getZ()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (!areaManager.isMeetingInProgress())
            return;

        boolean senderInMeeting = areaManager.isPlayerInMeeting(event.getPlayer());

        event.viewers().removeIf(audience -> {
            if (audience instanceof Player) {
                Player recipient = (Player) audience;
                boolean recipientInMeeting = areaManager.isPlayerInMeeting(recipient);
                return senderInMeeting != recipientInMeeting;
            }
            return false; // 禁止其他或空闲玩家听到会议
        });
    }
}
