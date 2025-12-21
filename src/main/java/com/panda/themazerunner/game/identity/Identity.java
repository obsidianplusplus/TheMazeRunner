package com.panda.themazerunner.game.identity;

import com.panda.themazerunner.manager.LanguageManager;

/**
 * 玩家身份枚举
 * <p>
 * 定义了游戏中的特殊身份，区别于职业 (GameClass)。
 */
public enum Identity {
    /**
     * 救世主
     */
    SAVIOR("savior"),
    /**
     * W.C.K.D (实验员)
     */
    WCKD("wckd"),
    /**
     * 流浪者
     */
    WANDERER("wanderer");

    private final String id;

    Identity(String id) {
        this.id = id;
    }

    /**
     * 获取身份ID
     *
     * @return 身份ID
     */
    public String getId() {
        return id;
    }

    /**
     * 获取身份显示名称
     * 
     * @param languageManager 语言管理器
     * @return 身份名称
     */
    public String getName(LanguageManager languageManager) {
        return languageManager.getString("identities." + id + ".name");
    }
}
