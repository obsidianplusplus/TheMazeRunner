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
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        config = plugin.getConfig();
    }

    public void reload() {
        loadConfig();
    }

    public int getFormalMeetingDuration() {
        return Math.max(15, Math.min(120, plugin.getConfig().getInt("formal_meeting_duration", 60))); // Default 600 seconds
    }
}
