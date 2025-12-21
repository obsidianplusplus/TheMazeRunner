package com.panda.themazerunner.game.identity;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 身份管理器
 * <p>
 * 负责管理玩家的身份状态。
 */
public class IdentityManager {

    private final Map<UUID, Identity> playerIdentities = new HashMap<>();

    public IdentityManager() {
    }

    /**
     * 设置玩家身份
     *
     * @param player   玩家
     * @param identity 身份
     */
    public void setIdentity(Player player, Identity identity) {
        playerIdentities.put(player.getUniqueId(), identity);
    }

    /**
     * 获取玩家身份
     *
     * @param player 玩家
     * @return 玩家身份，如果没有则返回 null
     */
    public Identity getIdentity(Player player) {
        return playerIdentities.get(player.getUniqueId());
    }

    /**
     * 移除玩家身份
     *
     * @param player 玩家
     */
    public void removeIdentity(Player player) {
        playerIdentities.remove(player.getUniqueId());
    }

    /**
     * 检查玩家是否有特定身份
     * 
     * @param player   玩家
     * @param identity 身份
     * @return 如果玩家拥有该身份返回 true
     */
    public boolean hasIdentity(Player player, Identity identity) {
        return getIdentity(player) == identity;
    }
}
