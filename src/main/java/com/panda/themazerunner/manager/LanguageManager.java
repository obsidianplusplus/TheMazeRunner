package com.panda.themazerunner.manager;

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
        // 重新加载配置以确保获取最新值
        plugin.reloadConfig();
        String langFileName = plugin.getConfig().getString("language", "zh_CN.yml");

        File messagesDir = new File(plugin.getDataFolder(), "messages");
        if (!messagesDir.exists()) {
            messagesDir.mkdirs();
        }

        configFile = new File(messagesDir, langFileName);

        // 尝试从 jar 包中保存默认资源
        if (!configFile.exists()) {
            try {
                // 检查 jar 包中是否存在该资源
                if (plugin.getResource("messages/" + langFileName) != null) {
                    plugin.saveResource("messages/" + langFileName, false);
                } else {
                    // 如果找不到对应的资源文件，创建一个空文件或者记录警告
                    plugin.getLogger().warning("没有找到语言文件: messages/" + langFileName);
                    configFile.createNewFile();
                }
            } catch (Exception e) {
                plugin.getLogger().severe("无法保存语言文件: " + e.getMessage());
            }
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
        String message = config.getString(path, "&c语言键不存在: " + path);
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
        // 使用 Adventure API 的 LegacyComponentSerializer 处理 & 颜色代码
        // 虽然方法返回 String，但为了兼容旧代码我们仍然转换
        // 实际上如果你全面拥抱 Adventure，应该尽可能返回 Component
        // 这里为了兼容 getStringList 等方法，我们使用 legacyAmpersand 序列化后再反序列化回 legacySection (S 符号)
        // 或者简单地:
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(message));
    }
}
