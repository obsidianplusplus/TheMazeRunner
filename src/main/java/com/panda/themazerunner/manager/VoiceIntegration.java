package com.panda.themazerunner.manager;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 语音管理器 (Simple Voice Chat 集成)
 * <p>
 * 负责管理 VoiceChat 插件的连接，处理会议语音、静音等逻辑。
 */
public class VoiceIntegration implements VoicechatPlugin {

    private final JavaPlugin plugin;
    private VoicechatApi api;

    public VoiceIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化 VoiceChat 服务
     * 必须在 onEnable 中注册
     */
    public void register() {
        BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            service.registerPlugin(this);
            plugin.getLogger().info("Successfully registered VoiceChat plugin.");
        } else {
            plugin.getLogger().warning("Simple Voice Chat service not found; text fallback is active.");
        }
    }

    @Override
    public String getPluginId() {
        return "themazerunner";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        api = event.getVoicechat();
        plugin.getLogger().info("VoiceChat API initialized.");
    }

}
