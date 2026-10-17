package com.panda.themazerunner.manager;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Collection;
import java.util.List;

/** No API references: absence of the provided voicechat API must not stop enable. */
public final class VoiceManager {
    private final JavaPlugin plugin;
    public VoiceManager(JavaPlugin plugin) { this.plugin = plugin; }
    public void register() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("voicechat")) {
            plugin.getLogger().info("Simple Voice Chat absent: text-based play is available.");
            return;
        }
        try {
            Class<?> bridge = Class.forName("com.panda.themazerunner.manager.VoiceIntegration");
            Object instance = bridge.getConstructor(JavaPlugin.class).newInstance(plugin);
            bridge.getMethod("register").invoke(instance);
        } catch (ReflectiveOperationException | LinkageError error) {
            plugin.getLogger().warning("Voice integration unavailable; text fallback: " + error.getClass().getSimpleName());
        }
    }
    // API 2.5.0 does not establish arbitrary microphone control. Do not claim muting.
    public void startMeetingChat(Collection<Player> players) { }
    public void stopMeetingChat() { }
    public void setSoleSpeaker(Player speaker, List<Player> participants) {
        for (Player p : participants) p.sendMessage("当前建议发言者：" + speaker.getName() + "（语音自愿轮流发言）");
    }
}
