package com.panda.themazerunner.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 配置管理器
 * <p>
 * 负责插件配置文件的加载、重载以及读取。
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public void reload() {
        loadConfig();
    }

    public int getFormalMeetingDuration() {
        return plugin.getConfig().getInt("formal_meeting_duration", 600); // Default 600 seconds
    }
}
