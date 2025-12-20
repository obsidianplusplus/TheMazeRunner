package com.panda.themazerunner.listeners;

import com.panda.themazerunner.TheMazeRunner;
import com.panda.themazerunner.game.classes.GameClass;
import com.panda.themazerunner.game.classes.impl.Guardian;
import com.panda.themazerunner.manager.ClassManager;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 守护者事件监听器
 * <p>
 * 处理守护者的职业特性，例如使用拴绳牵引怪物和队友。
 */
public class GuardianListener implements Listener {

    private final TheMazeRunner plugin;

    public GuardianListener(TheMazeRunner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerLeashEntity(PlayerInteractEntityEvent event) {
        // 仅处理主手交互
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // 检查物品是否为拴绳
        if (item.getType() != Material.LEAD) {
            return;
        }

        // 检查玩家职业是否为守护者
        ClassManager classManager = plugin.getClassManager();
        GameClass gameClass = classManager.getPlayerClass(player);
        if (!(gameClass instanceof Guardian)) {
            return;
        }

        // 检查目标是否为生物 (怪物或玩家)
        if (event.getRightClicked() instanceof LivingEntity) {
            LivingEntity target = (LivingEntity) event.getRightClicked();

            // 允许拴住怪物或玩家
            if (target instanceof Monster || target instanceof Player) {
                // 如果已经被拴住，则无需再次处理 (原版逻辑会处理解栓，或者我们可以覆盖)
                if (target.isLeashed()) {
                    if (target.getLeashHolder().equals(player)) {
                        // 如果是自己拴住的，允许解栓 (原版行为)
                        return;
                    } else {
                        // 如果被别人拴住了，提示一下? 或者也允许抢过来? 暂时保持原版逻辑
                        return;
                    }
                }

                // 强制拴住目标
                // 注意: 对玩家使用 setLeashHolder 在某些版本可能没有视觉效果或需要客户端支持
                // 在 1.21+ Paper 中，API 支持通常较好
                target.setLeashHolder(player);

                // 扣除一个拴绳 (模拟生存模式消耗)
                if (!org.bukkit.GameMode.CREATIVE.equals(player.getGameMode())) {
                    item.setAmount(item.getAmount() - 1);
                }

                // 阻止原版事件，防止打开GUI等副作用 (例如村民)
                // 但对于拴绳，我们希望视觉效果生效，通常不需要 setCancelled(true) 除非是自定义逻辑覆盖
                // 这里我们手动设置了 LeashHolder，所以可以取消原版交互以防冲突
                event.setCancelled(true);
            }
        }
    }

    // 可以添加 EntityUnleashEvent 处理来防止意外脱落，或者增加脱落提示
}
