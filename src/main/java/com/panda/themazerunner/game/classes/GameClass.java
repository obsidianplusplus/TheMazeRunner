package com.panda.themazerunner.game.classes;

import com.panda.themazerunner.manager.LanguageManager;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 游戏职业基类
 * <p>
 * 所有具体的游戏职业都应继承此抽象类。
 * 提供了获取本地化名称、描述以及职业装备和卸载的基本方法。
 */
public abstract class GameClass {

    protected final LanguageManager languageManager;
    protected final String id;

    /**
     * 构造函数
     *
     * @param languageManager 语言管理器实例
     * @param id              职业唯一标识符 (对应语言文件中的 key)
     */
    public GameClass(LanguageManager languageManager, String id) {
        this.languageManager = languageManager;
        this.id = id;
    }

    /**
     * 获取职业ID
     *
     * @return 职业ID
     */
    public String getId() {
        return id;
    }

    /**
     * 获取职业显示名称 (已本地化)
     *
     * @return 职业名称
     */
    public String getName() {
        return languageManager.getString("classes." + id + ".name");
    }

    /**
     * 获取职业描述 (已本地化)
     *
     * @return 职业描述列表
     */
    public List<String> getDescription() {
        return languageManager.getStringList("classes." + id + ".description");
    }

    /**
     * 当玩家装备此职业时调用
     *
     * @param player 装备职业的玩家
     */
    public abstract void onEquip(Player player);

    /**
     * 当玩家卸下此职业时调用
     * <p>
     * 通常用于清除药水效果或移除物品。
     *
     * @param player 卸下职业的玩家
     */
    public abstract void onUnequip(Player player);
}
