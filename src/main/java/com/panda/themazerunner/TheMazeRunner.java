package com.panda.themazerunner;

import com.panda.themazerunner.manager.ClassManager;
import com.panda.themazerunner.manager.LanguageManager;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * 迷宫行者 (The Maze Runner) 插件主类
 *
 * <p>负责插件的初始化、各个管理器的加载以及资源的释放。
 */
public class TheMazeRunner extends JavaPlugin {

    private static TheMazeRunner instance;
    private com.panda.themazerunner.game.match.MatchManager matchManager;

    public com.panda.themazerunner.game.match.MatchManager getMatchManager() {
        return matchManager;
    }

    private LanguageManager languageManager;
    private ClassManager classManager;
    private com.panda.themazerunner.gate.GateManager gateManager;
    private com.panda.themazerunner.game.identity.IdentityManager identityManager;
    private com.panda.themazerunner.game.area.AreaManager areaManager;
    private com.panda.themazerunner.manager.VoiceManager voiceManager;
    private com.panda.themazerunner.manager.ConfigManager configManager;
    private com.panda.themazerunner.manager.VoteManager voteManager;

    @Override
    public void onEnable() {
        instance = this;

        // 初始化配置管理器
        this.configManager = new com.panda.themazerunner.manager.ConfigManager(this);

        // 初始化语言管理器
        this.languageManager = new LanguageManager(this);

        // 初始化职业管理器
        this.classManager = new ClassManager(languageManager);

        // ArenaProtection and the match ability registry own class permissions during a match.
        String arenaWorld = getConfig().getString("match.world", "");
        if (arenaWorld.matches("[A-Za-z0-9_-]{1,48}")
                && org.bukkit.Bukkit.getWorld(arenaWorld) == null
                && new java.io.File(getServer().getWorldContainer(), arenaWorld).isDirectory()) {
            new org.bukkit.WorldCreator(arenaWorld)
                    .generator(new org.bukkit.generator.ChunkGenerator() {})
                    .createWorld();
        }
        // 初始化大门管理器
        this.gateManager = new com.panda.themazerunner.gate.GateManager(this);

        // 初始化身份管理器
        this.identityManager = new com.panda.themazerunner.game.identity.IdentityManager();

        // 初始化区域管理器
        this.areaManager = new com.panda.themazerunner.game.area.AreaManager(this);

        // 初始化语音管理器
        this.voiceManager = new com.panda.themazerunner.manager.VoiceManager(this);
        this.voiceManager.register();

        // 初始化投票管理器
        this.voteManager = new com.panda.themazerunner.manager.VoteManager(this);

        // 注册命令
        com.panda.themazerunner.gate.GateCommand gateCmd =
                new com.panda.themazerunner.gate.GateCommand(gateManager, languageManager);
        getCommand("gate").setExecutor(gateCmd);
        getCommand("gate").setTabCompleter(gateCmd);

        com.panda.themazerunner.game.area.AreaCommand areaCmd =
                new com.panda.themazerunner.game.area.AreaCommand(areaManager, languageManager);
        getCommand("area").setExecutor(areaCmd);
        getCommand("area").setTabCompleter(areaCmd);

        // 注册大门监听器
        getServer()
                .getPluginManager()
                .registerEvents(
                        new com.panda.themazerunner.listeners.GateListener(
                                gateManager, languageManager),
                        this);

        // 注册区域监听器
        getServer()
                .getPluginManager()
                .registerEvents(
                        new com.panda.themazerunner.listeners.AreaListener(
                                areaManager, identityManager, languageManager),
                        this);

        // 注册玩家死亡监听器
        getServer()
                .getPluginManager()
                .registerEvents(
                        new com.panda.themazerunner.listeners.PlayerDeathListener(
                                areaManager, languageManager),
                        this);

        // 注册投票监听器
        getServer()
                .getPluginManager()
                .registerEvents(new com.panda.themazerunner.listeners.VoteListener(this), this);

        matchManager = new com.panda.themazerunner.game.match.MatchManager(this);
        getCommand("maze").setExecutor(matchManager);
        getCommand("maze").setTabCompleter(matchManager);
        getServer()
                .getPluginManager()
                .registerEvents(
                        new com.panda.themazerunner.listeners.ArenaProtection(matchManager), this);
        getCommand("class")
                .setExecutor(
                        (sender, command, label, args) ->
                                matchManager.onCommand(
                                        sender, command, label, new String[] {"class"}));
        getServer().getPluginManager().registerEvents(matchManager, this);
        getLogger().info("The Maze Runner 插件已启动!");
        getLogger().info("当前版本: " + getPluginMeta().getVersion());
    }

    @Override
    public void onDisable() {
        cleanup(
                "match",
                () -> {
                    if (matchManager != null) matchManager.shutdown();
                });
        cleanup(
                "meeting",
                () -> {
                    if (areaManager != null) areaManager.stopMeeting();
                });
        getServer().getScheduler().cancelTasks(this);
        cleanup(
                "gates",
                () -> {
                    if (gateManager != null) gateManager.shutdown();
                });
        instance = null;
        getLogger().info("The Maze Runner 插件已卸载!");
    }

    private void cleanup(String system, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            getLogger()
                    .severe(
                            "Cleanup failed for "
                                    + system
                                    + "; recovery files retained: "
                                    + error.getMessage());
        }
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

    /**
     * 获取身份管理器
     *
     * @return 身份管理器实例
     */
    public com.panda.themazerunner.game.identity.IdentityManager getIdentityManager() {
        return identityManager;
    }

    /**
     * 获取区域管理器
     *
     * @return 区域管理器实例
     */
    public com.panda.themazerunner.game.area.AreaManager getAreaManager() {
        return areaManager;
    }

    /**
     * 获取语音管理器
     *
     * @return 语音管理器实例
     */
    public com.panda.themazerunner.manager.VoiceManager getVoiceManager() {
        return voiceManager;
    }

    public com.panda.themazerunner.manager.ConfigManager getConfigManager() {
        return configManager;
    }

    public com.panda.themazerunner.manager.VoteManager getVoteManager() {
        return voteManager;
    }
}
