package com.panda.themazerunner.manager;

import com.panda.themazerunner.game.classes.GameClass;
import com.panda.themazerunner.game.classes.impl.Builder;
import com.panda.themazerunner.game.classes.impl.Guardian;
import com.panda.themazerunner.game.classes.impl.MedJack;
import com.panda.themazerunner.game.classes.impl.Runner;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 职业管理器
 * <p>
 * 负责管理所有的游戏职业，以及玩家的职业状态。
 */
public class ClassManager {

    private final LanguageManager languageManager;
    private final Map<String, GameClass> classes = new HashMap<>();
    private final Map<UUID, GameClass> playerClasses = new HashMap<>();

    /**
     * 构造函数
     *
     * @param languageManager 语言管理器
     */
    public ClassManager(LanguageManager languageManager) {
        this.languageManager = languageManager;
        registerDefaults();
    }

    /**
     * 注册默认职业
     */
    private void registerDefaults() {
        registerClass(new Runner(languageManager));
        registerClass(new Guardian(languageManager));
        registerClass(new MedJack(languageManager));
        registerClass(new Builder(languageManager));
        registerClass(new com.panda.themazerunner.game.classes.impl.Cook(languageManager));
    }

    /**
     * 注册职业
     *
     * @param gameClass 职业实例
     */
    public void registerClass(GameClass gameClass) {
        classes.put(gameClass.getId(), gameClass);
    }

    /**
     * 获取职业
     *
     * @param id 职业ID
     * @return 职业实例，如果不存在则返回 null
     */
    public GameClass getGameClass(String id) {
        return classes.get(id);
    }

    /**
     * 装备职业
     *
     * @param player 玩家
     * @param id     职业ID
     * @return 是否成功装备
     */
    public boolean equipClass(Player player, String id) {
        GameClass gameClass = getGameClass(id);
        if (gameClass == null) {
            player.sendMessage(languageManager.getMessage("errors.class_not_found"));
            return false;
        }

        // 如果玩家已经有职业，先卸下
        unequipClass(player);

        // 装备新职业
        gameClass.onEquip(player);
        playerClasses.put(player.getUniqueId(), gameClass);
        return true;
    }

    /**
     * 卸下职业
     *
     * @param player 玩家
     */
    public void unequipClass(Player player) {
        if (playerClasses.containsKey(player.getUniqueId())) {
            GameClass gameClass = playerClasses.remove(player.getUniqueId());
            gameClass.onUnequip(player);
        }
    }

    /**
     * 获取玩家当前的职业
     *
     * @param player 玩家
     * @return 职业实例，如果未选择职业则返回 null
     */
    public GameClass getPlayerClass(Player player) {
        return playerClasses.get(player.getUniqueId());
    }
}
