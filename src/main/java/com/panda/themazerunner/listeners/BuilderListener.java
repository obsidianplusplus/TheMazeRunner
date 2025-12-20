package com.panda.themazerunner.listeners;

import com.panda.themazerunner.TheMazeRunner;
import com.panda.themazerunner.game.classes.GameClass;
import com.panda.themazerunner.game.classes.impl.Builder;
import com.panda.themazerunner.manager.ClassManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * 建筑师事件监听器
 * <p>
 * 处理建筑师的特殊权限，例如限制方块放置。
 */
public class BuilderListener implements Listener {

    private final TheMazeRunner plugin;

    public BuilderListener(TheMazeRunner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        // 创造模式无视限制
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        ClassManager classManager = plugin.getClassManager();
        GameClass gameClass = classManager.getPlayerClass(player);

        // 如果玩家不是建筑师，取消放置
        if (!(gameClass instanceof Builder)) {
            // 可以发送提示消息，但为了避免刷屏通常保持静默或仅提示一次
            // player.sendMessage("§c只有建筑师可以放置方块!");
            event.setCancelled(true);
        }
    }
}
