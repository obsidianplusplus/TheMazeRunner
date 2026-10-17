package com.panda.themazerunner.listeners;

import com.panda.themazerunner.game.area.AreaManager;
import com.panda.themazerunner.manager.LanguageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathListener implements Listener {

    private final AreaManager areaManager;
    private final LanguageManager languageManager;

    public PlayerDeathListener(AreaManager areaManager, LanguageManager languageManager) {
        this.areaManager = areaManager;
        this.languageManager = languageManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!com.panda.themazerunner.TheMazeRunner.getInstance().getConfig().getBoolean("legacy-meetings-enabled", false)) return;
        // 当任意玩家死亡时，全员获得一次敲钟权 (全局共享)
        areaManager.grantGlobalRingRight();
        for (Player onlinePlayer : org.bukkit.Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(languageManager.getMessage("area.bell_right_obtained"));
        }
    }
}
