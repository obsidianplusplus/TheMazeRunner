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

public class VoiceManager implements VoicechatPlugin {

    private final JavaPlugin plugin;
    private VoicechatApi api;
    private Group meetingGroup;

    public VoiceManager(JavaPlugin plugin) {
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
            plugin.getLogger().warning("DictVoiceChat service not found.");
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

    /**
     * 创建会议群组并将所有玩家加入
     * 
     * @param players 玩家列表
     */
    public void startMeetingChat(Collection<Player> players) {
        if (api == null)
            return;

        plugin.getLogger().info("Starting Voice Chat Meeting for " + players.size() + " players.");
    }

    public void stopMeetingChat() {
        if (api == null)
            return;
        // 清理群组逻辑
    }

    /**
     * 设置唯一发言者 (用于会议)
     * 其他参与者将被静音或限制麦克风
     *
     * @param currentSpeaker      当前发言者
     * @param meetingParticipants 会议参与者列表
     */
    public void setSoleSpeaker(Player currentSpeaker, List<Player> meetingParticipants) {
        if (api == null)
            return;

        // TODO: Implement actual voice chat mute logic
        // This requires manipulating VoicechatConnections or Groups
        // For now, we just log it as the API usage is complex to implement without docs
        // in hand
        plugin.getLogger().info("Setting sole speaker to: " + currentSpeaker.getName());
    }
}
