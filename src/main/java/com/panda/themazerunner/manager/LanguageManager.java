package com.panda.themazerunner.manager;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 语言管理器
 * <p>
 * 负责加载和获取本地化语言文件中的信息。
 * 支持颜色代码转换和占位符替换。
 */
public class LanguageManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    /**
     * 构造函数
     *
     * @param plugin 插件实例
     */
    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadLanguage();
    }

    /**
     * 加载语言文件
     * <p>
     * 如果文件不存在，则保存默认资源文件。
     */
    public void loadLanguage() {
        configFile = new File(plugin.getDataFolder(), "messages_zh_CN.yml");
        if (!configFile.exists()) {
            plugin.saveResource("messages_zh_CN.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * 获取字符串消息
     *
     * @param path 语言文件路径
     * @return 格式化后的字符串
     */
    public String getString(String path) {
        String message = config.getString(path, "&cMissing language key: " + path);
        return color(message);
    }

    /**
     * 获取带有前缀的字符串消息
     *
     * @param path 语言文件路径
     * @return 带有前缀的格式化字符串
     */
    public String getMessage(String path) {
        String prefix = getString("prefix");
        return prefix + getString(path);
    }

    /**
     * 获取字符串列表
     *
     * @param path 语言文件路径
     * @return 格式化后的字符串列表
     */
    public List<String> getStringList(String path) {
        List<String> list = config.getStringList(path);
        return list.stream().map(this::color).collect(Collectors.toList());
    }

    /**
     * 获取 Component 消息 (Adventure API)
     *
     * @param path 语言文件路径
     * @return 格式化后的 Component
     */
    public net.kyori.adventure.text.Component getComponent(String path) {
        String message = getString(path);
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize(message);
    }

    /**
     * 获取 Component 列表 (Adventure API)
     *
     * @param path 语言文件路径
     * @return 格式化后的 Component 列表
     */
    public List<net.kyori.adventure.text.Component> getComponentList(String path) {
        return getStringList(path).stream()
                .map(s -> net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .deserialize(s))
                .collect(Collectors.toList());
    }

    /**
     * 转换颜色代码
     *
     * @param message 原始消息
     * @return 带有颜色的消息
     */
    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
