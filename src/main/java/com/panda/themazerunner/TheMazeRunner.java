package com.panda.themazerunner;

import com.panda.themazerunner.manager.ClassManager;
import com.panda.themazerunner.manager.LanguageManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 迷宫行者 (The Maze Runner) 插件主类
 * <p>
 * 负责插件的初始化、各个管理器的加载以及资源的释放。
 */
public class TheMazeRunner extends JavaPlugin {

    private static TheMazeRunner instance;
    private LanguageManager languageManager;
    private ClassManager classManager;
    private com.panda.themazerunner.gate.GateManager gateManager;

    @Override
    public void onEnable() {
        instance = this;

        // 初始化语言管理器
        this.languageManager = new LanguageManager(this);

        // 初始化职业管理器
        this.classManager = new ClassManager(languageManager);

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new com.panda.themazerunner.listeners.GuardianListener(this),
                this);
        getServer().getPluginManager().registerEvents(new com.panda.themazerunner.listeners.BuilderListener(this),
                this);

        // 初始化大门管理器
        this.gateManager = new com.panda.themazerunner.gate.GateManager(this);

        // 注册命令
        com.panda.themazerunner.gate.GateCommand gateCmd = new com.panda.themazerunner.gate.GateCommand(gateManager,
                languageManager);
        getCommand("gate").setExecutor(gateCmd);
        getCommand("gate").setTabCompleter(gateCmd);

        // 注册大门监听器
        getServer().getPluginManager()
                .registerEvents(new com.panda.themazerunner.listeners.GateListener(gateManager, languageManager), this);

        getLogger().info("The Maze Runner 插件已启动!");
        getLogger().info("当前版本: " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        if (gateManager != null) {
            gateManager.shutdown();
        }
        getLogger().info("The Maze Runner 插件已卸载!");
    }

    /**
     * 获取插件实例
     *
     * @return 插件实例
     */
    public static TheMazeRunner getInstance() {
        return instance;
    }

    /**
     * 获取语言管理器
     *
     * @return 语言管理器实例
     */
    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    /**
     * 获取职业管理器
     *
     * @return 职业管理器实例
     */
    public ClassManager getClassManager() {
        return classManager;
    }

    /**
     * 获取大门管理器
     *
     * @return 大门管理器实例
     */
    public com.panda.themazerunner.gate.GateManager getGateManager() {
        return gateManager;
    }
}
