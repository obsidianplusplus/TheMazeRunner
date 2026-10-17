package com.panda.themazerunner.game.area;

import com.panda.themazerunner.TheMazeRunner;
import com.panda.themazerunner.game.identity.Identity;
import com.panda.themazerunner.manager.LanguageManager;
import com.panda.themazerunner.manager.VoteManager;
import com.panda.themazerunner.utils.Region;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.data.Lightable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

/**
 * 区域管理器
 * <p>
 * 负责管理游戏中的特殊区域和位置，如林间空地、余烬篝火、集结钟等。
 */
public class AreaManager {

    private final JavaPlugin plugin;
    private final File dataFile;

    // 区域与位置数据
    private Region gladeRegion;
    private Location bonfireLocation;
    private Region bonfireRegion;
    private Location gatheringBellLocation;

    // 游戏设置
    private boolean isWandererAccessAllowed = false; // 流浪者是否允许进入林间空地 (默认false)

    // 玩家选区缓存: UUID -> [Pos1, Pos2]
    private final Map<UUID, Location[]> playerSelections = new HashMap<>();

    public AreaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "areas.yml");
        loadAreas();
    }

    /**
     * 加载区域数据
     */
    public void loadAreas() {
        if (!dataFile.exists())
            return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        if (config.contains("glade")) {
            gladeRegion = readRegion(config,"glade");
        }
        if (config.contains("bonfire_loc")) {
            bonfireLocation = config.getLocation("bonfire_loc");
        }
        if (config.contains("bonfire_area")) {
            bonfireRegion = readRegion(config,"bonfire_area");
        }
        if (config.contains("bell_loc")) {
            gatheringBellLocation = config.getLocation("bell_loc");
        }
        if (config.contains("wanderer_access")) {
            isWandererAccessAllowed = config.getBoolean("wanderer_access");
        }

        plugin.getLogger().info("区域数据加载完成。");
    }

    private Region readRegion(YamlConfiguration config,String key) {
        try {var section=config.getConfigurationSection(key);if(section==null)throw new IllegalArgumentException("Expected region section");return Region.deserialize(section.getValues(false));}
        catch(RuntimeException error){plugin.getLogger().warning("Invalid area "+key+": "+error.getMessage());return null;}
    }

    /**
     * 保存区域数据
     */
    public void saveAreas() {
        YamlConfiguration config = new YamlConfiguration();

        if (gladeRegion != null) {
            config.createSection("glade", gladeRegion.serialize());
        }
        if (bonfireLocation != null) {
            config.set("bonfire_loc", bonfireLocation);
        }
        if (bonfireRegion != null) {
            config.createSection("bonfire_area", bonfireRegion.serialize());
        }
        if (gatheringBellLocation != null) {
            config.set("bell_loc", gatheringBellLocation);
        }
        config.set("wanderer_access", isWandererAccessAllowed);

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("保存区域数据失败: " + e.getMessage());
        }
    }

    public void setWandererAccessAllowed(boolean allowed) {
        this.isWandererAccessAllowed = allowed;
        saveAreas();
    }

    public boolean isWandererAccessAllowed() {
        return isWandererAccessAllowed;
    }

    public void setGladeRegion(Region region) {
        this.gladeRegion = region;
        saveAreas();
    }

    public Region getGladeRegion() {
        return gladeRegion;
    }

    public void setBonfireLocation(Location location) {
        this.bonfireLocation = location;
        saveAreas();
    }

    public Location getBonfireLocation() {
        return bonfireLocation;
    }

    public void setBonfireRegion(Region region) {
        this.bonfireRegion = region;
        saveAreas();
    }

    public Region getBonfireRegion() {
        return bonfireRegion;
    }

    public void setGatheringBellLocation(Location location) {
        this.gatheringBellLocation = location;
        saveAreas();
    }

    public Location getGatheringBellLocation() {
        return gatheringBellLocation;
    }

    /**
     * 设置玩家的选点
     */
    public void setPlayerSelection(UUID uuid, int index, Location loc) {
        playerSelections.putIfAbsent(uuid, new Location[2]);
        playerSelections.get(uuid)[index] = loc;
    }

    /**
     * 获取玩家当前的选区 (如果有效)
     *
     * @param uuid 玩家UUID
     * @return Region 实例，如果选区无效则返回 null
     */
    public Region getPlayerSelectionRegion(UUID uuid) {
        Location[] locs = playerSelections.get(uuid);
        if (locs == null || locs[0] == null || locs[1] == null) {
            return null;
        }
        if (!locs[0].getWorld().equals(locs[1].getWorld())) {
            return null;
        }
        try{return new Region(locs[0],locs[1]);}
        catch(IllegalArgumentException error){Player player=Bukkit.getPlayer(uuid);if(player!=null)player.sendMessage("选区无效："+error.getMessage());return null;}
    }

    // Meeting Logic
    private volatile boolean isMeetingInProgress = false;
    private volatile Set<UUID> meetingSnapshot = Set.of();
    public Set<UUID> getMeetingSnapshot() { return meetingSnapshot; }
    private void updateMeetingSnapshot() {
        meetingSnapshot = meetingParticipants.stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public void removeParticipant(Player player) {
        playerSelections.remove(player.getUniqueId());
        meetingParticipants.remove(player); speakerQueue.remove(player); updateMeetingSnapshot();
        player.removePotionEffect(PotionEffectType.DARKNESS); player.removePotionEffect(PotionEffectType.GLOWING);
    }
    private long meetingEpoch;
    private org.bukkit.scheduler.BukkitTask later(Runnable action, long delay) {
        long epoch = meetingEpoch;
        return Bukkit.getScheduler().runTaskLater(plugin, () -> { if (epoch == meetingEpoch && isMeetingInProgress) action.run(); }, delay);
    }

    public boolean isMeetingInProgress() {
        return isMeetingInProgress;
    }

    private boolean isTeaTimeInProgress = false;
    // Formal Meeting Logic
    private boolean isFormalMeetingInProgress = false;
    private boolean isTrialInProgress = false; // New Trial State
    private LinkedList<Player> speakerQueue = new LinkedList<>();
    private List<Player> meetingParticipants = new ArrayList<>();
    private int speakerTask = -1;
    private int secondsPerPlayer = 60; // Default fallback

    // Return Phase Logic
    private boolean isReturnToGladePhase = false;

    public boolean isReturnToGladePhase() {
        return isReturnToGladePhase;
    }

    public List<Player> getMeetingParticipants() {
        return new ArrayList<>(meetingParticipants);
    }

    public boolean isTeaTimeInProgress() {
        return isTeaTimeInProgress;
    }

    /**
     * 开启篝火会议: 将区域内所有铜灯设为最高亮度 (COPPER_BULB, Lit=true)
     * 播放音效，给予失明，并将林间空地的玩家传送至篝火周围
     */
    public void startMeeting() {
        // Legacy social-deduction mechanics are opt-in, never the cooperative default.
        if (!plugin.getConfig().getBoolean("legacy-meetings-enabled", false) || isMeetingInProgress
                || TheMazeRunner.getInstance().getMatchManager()!=null && TheMazeRunner.getInstance().getMatchManager().busy()) return;
        if (bonfireRegion == null || bonfireLocation == null || gladeRegion == null)
            return;

        isMeetingInProgress = true;
        isReturnToGladePhase = true; // 开启归还阶段

        // 1. 灯光逻辑
        bonfireRegion.forEachBlock(block -> {
            if (isCopperBulbVariant(block.getType())) {
                block.setType(Material.COPPER_BULB);
                if (block.getBlockData() instanceof Lightable) {
                    Lightable lightable = (Lightable) block.getBlockData();
                    lightable.setLit(true);
                    block.setBlockData(lightable);
                }
            }
        });

        // 2. 玩家处理逻辑
        List<Player> playersInGlade = new ArrayList<>();
        List<Player> playersOutside = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (gladeRegion.contains(player.getLocation())) {
                playersInGlade.add(player);
                meetingParticipants.add(player);
            updateMeetingSnapshot(); // Add initial participants
            } else {
                playersOutside.add(player);
            }
        }

        updateMeetingSnapshot();
        // 处理已经在林间空地的玩家
        if (!playersInGlade.isEmpty()) {
            double radius = 5.0; // 圆形半径
            double angleIncrement = 360.0 / playersInGlade.size();
            double currentAngle = 0;

            for (Player player : playersInGlade) {
                // 音效 & 失明
                player.playSound(player.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE, 1.0f, 0.5f);
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));

                // 计算传送位置
                double radians = Math.toRadians(currentAngle);
                double x = bonfireLocation.getX() + radius * Math.cos(radians);
                double z = bonfireLocation.getZ() + radius * Math.sin(radians);
                Location teleportLoc = new Location(bonfireLocation.getWorld(), x, bonfireLocation.getY(), z);

                // 设置朝向看向篝火
                teleportLoc.setDirection(bonfireLocation.toVector().subtract(teleportLoc.toVector()));

                player.teleport(teleportLoc);

                currentAngle += angleIncrement;
            }
        }

        // 3. 处理外部玩家通知 & 倒计时
        notifyOutsiders(playersOutside);

        // 4. 开启茶水时间 (1分钟倒计时)
        startTeaTime(playersInGlade);

        // 5. 调度关闭大门 (与茶水时间一致, 60秒)
        later(this::closeAllGatesAndEndReturnPhase, 1200L);
    }

    private void notifyOutsiders(List<Player> outsiders) {
        LanguageManager lm = TheMazeRunner.getInstance().getLanguageManager();
        com.panda.themazerunner.game.identity.IdentityManager im = TheMazeRunner.getInstance().getIdentityManager();

        Component warning = lm.getComponent("area.return_countdown")
                .replaceText(b -> b.match("%seconds%").replacement("60"));

        Title returnTitle = Title.title(Component.text(""), warning,
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(5000), Duration.ofMillis(1000)));

        for (Player p : outsiders) {
            // 流浪者不受限制
            if (im.hasIdentity(p, Identity.WANDERER))
                continue;

            p.showTitle(returnTitle);
            p.sendMessage(warning);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.0f, 1.0f);
        }
    }

    private void closeAllGatesAndEndReturnPhase() {
        if (!isReturnToGladePhase)
            return;

        isReturnToGladePhase = false;

        // 关闭所有大门
        com.panda.themazerunner.gate.GateManager gm = TheMazeRunner.getInstance().getGateManager();
        for (String gateName : gm.getGateNames()) {
            gm.closeGate(gateName);
        }

        // 广播大门关闭
        Bukkit.broadcast(TheMazeRunner.getInstance().getLanguageManager().getComponent("area.gates_closed"));
        Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.5f));
    }

    /**
     * 将玩家传送至篝火处并加入会议
     * 
     * @param player 玩家
     */
    public void teleportAndJoinMeeting(Player player) {
        if (bonfireLocation == null)
            return;

        player.teleport(bonfireLocation);
        player.sendMessage(TheMazeRunner.getInstance().getLanguageManager().getComponent("area.late_arrival"));

        if (!meetingParticipants.contains(player)) {
            meetingParticipants.add(player);
            updateMeetingSnapshot();
        }
    }

    private void startTeaTime(List<Player> players) {
        isTeaTimeInProgress = true;
        LanguageManager lm = TheMazeRunner.getInstance().getLanguageManager();

        // 广播消息
        for (Player p : players) {
            p.sendMessage(lm.getComponent("area.tea_time.start_header"));
            p.sendMessage(lm.getComponent("area.tea_time.start_body"));
        }

        // 开启语音 (通过 VoiceManager)
        TheMazeRunner.getInstance().getVoiceManager().startMeetingChat(players);

        // 1分钟 (1200 ticks) 后结束
        later(this::stopTeaTime, 1200L);
    }

    public void stopTeaTime() {
        if (!isTeaTimeInProgress)
            return;

        isTeaTimeInProgress = false;

        // 广播结束消息
        Bukkit.broadcast(TheMazeRunner.getInstance().getLanguageManager().getComponent("area.tea_time.end"));

        // 进入正式会议
        startFormalMeeting();
    }

    private void startFormalMeeting() {
        isFormalMeetingInProgress = true;
        LanguageManager lm = TheMazeRunner.getInstance().getLanguageManager();

        // 不要清除 meetingParticipants，因为可能有新人加入
        speakerQueue.clear();

        // 同步 speakerQueue
        speakerQueue.addAll(meetingParticipants);

        if (meetingParticipants.isEmpty()) {
            stopMeeting();
            return;
        }

        // 开启投票 (给票)
        TheMazeRunner.getInstance().getVoteManager().startVoting(meetingParticipants);

        // Calculate Time Per Player
        int totalDuration = TheMazeRunner.getInstance().getConfigManager().getFormalMeetingDuration();
        int participantCount = meetingParticipants.size();
        // Ensure at least 5 seconds per player for safety, avoiding division by zero
        this.secondsPerPlayer = Math.max(5, totalDuration / Math.max(1, participantCount));

        Bukkit.broadcast(lm.getComponent("area.meeting.start_header"));
        Bukkit.broadcast(lm.getComponent("area.meeting.start_body_1"));
        Bukkit.broadcast(lm.getComponent("area.meeting.start_body_2")
                .replaceText(b -> b.match("%minutes%").replacement(String.valueOf(totalDuration / 60)))
                .replaceText(b -> b.match("%seconds%").replacement(String.valueOf(secondsPerPlayer))));
        Bukkit.broadcast(lm.getComponent("area.meeting.start_body_3"));

        nextSpeaker();
    }

    public boolean isPlayerInMeeting(Player player) {
        // Meeting is technically in progress during Trial too if we keep
        // isMeetingInProgress=true
        // My implementation keeps isMeetingInProgress=true until stopMeeting() at the
        // very end.
        // So this check remains valid.
        return isMeetingInProgress && (meetingParticipants.contains(player)
                || (gladeRegion != null && gladeRegion.contains(player.getLocation())));
    }

    private void nextSpeaker() {
        if (speakerQueue.isEmpty()) {
            // 所有人都发言完毕 -> 进入审判阶段 或者 结束
            transitionToTrial();
            return;
        }

        Player currentSpeaker = speakerQueue.poll();
        if (currentSpeaker == null || !currentSpeaker.isOnline()) {
            nextSpeaker(); // 跳过离线玩家
            return;
        }

        // 设置语音权限
        TheMazeRunner.getInstance().getVoiceManager().setSoleSpeaker(currentSpeaker, meetingParticipants);

        // 设置视觉效果: 发言者发光 (Glowing), 其他人失明/变暗 (Darkness)
        for (Player p : meetingParticipants) {
            if (p.equals(currentSpeaker)) {
                p.removePotionEffect(PotionEffectType.DARKNESS);
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
            } else {
                p.removePotionEffect(PotionEffectType.GLOWING);
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, false, false));
            }
        }

        // 使用计算好的时长
        int durationSeconds = this.secondsPerPlayer;
        LanguageManager lm = TheMazeRunner.getInstance().getLanguageManager();

        Bukkit.broadcast(lm.getComponent("area.meeting.speaker")
                .replaceText(b -> b.match("%player%").replacement(currentSpeaker.getName())));
        Bukkit.broadcast(lm.getComponent("area.meeting.duration")
                .replaceText(b -> b.match("%seconds%").replacement(String.valueOf(durationSeconds))));

        // 调度下一位
        speakerTask = later(this::nextSpeaker, durationSeconds * 20L).getTaskId();
    }

    private void transitionToTrial() {
        isFormalMeetingInProgress = false; // Meeting speaking phase over

        // Check Voting Results
        VoteManager vm = TheMazeRunner.getInstance().getVoteManager();
        vm.endVoting(); // Stop accepting initial votes

        Player accused = vm.calculateExileCandidate();

        if (accused == null) {
            Bukkit.broadcast(TheMazeRunner.getInstance().getLanguageManager().getComponent("area.meeting.result_tie"));
            stopMeeting();
        } else {
            startTrial(accused);
        }
    }

    private void startTrial(Player accused) {
        isTrialInProgress = true;
        VoteManager vm = TheMazeRunner.getInstance().getVoteManager();
        vm.startJudgment(accused); // Opens GUIs

        // Schedule End of Trial (e.g., 30 seconds for judgment)
        // Or wait for strict "everyone voted"?
        // Let's use a fixed time for tension.
        Bukkit.broadcast(TheMazeRunner.getInstance().getLanguageManager().getComponent("area.meeting.trial_time"));

        later(() -> {
            endTrial(vm);
        }, 30 * 20L);
    }

    private void endTrial(VoteManager vm) {
        if (!isTrialInProgress)
            return; // Already stopped?

        VoteManager.JudgmentResult result = vm.evaluateJudgment();
        Player accused = vm.getCurrentAccused();
        LanguageManager lm = TheMazeRunner.getInstance().getLanguageManager();

        vm.endTrial();
        isTrialInProgress = false;

        if (accused == null || !accused.isOnline()) { stopMeeting(); return; }
        switch (result) {
            case FORGIVE:
                Bukkit.broadcast(lm.getComponent("area.punishment.forgiven_broadcast")
                        .replaceText(b -> b.match("%player%").replacement(accused.getName())));
                accused.sendMessage(lm.getComponent("area.punishment.forgiven_target"));
                break;
            case EXILE:
                Bukkit.broadcast(lm.getComponent("area.punishment.exiled_broadcast")
                        .replaceText(b -> b.match("%player%").replacement(accused.getName())));
                accused.sendMessage(lm.getComponent("area.punishment.exiled_target"));
                // Execute Punishment (Kill or Teleport?)
                // User request: "Exile". Usually implies death or kick out.
                // Let's strike lightning and kill for effect.
                accused.getWorld().strikeLightningEffect(accused.getLocation());
                accused.setHealth(0);
                break;
            case PUNISHMENT:
                Bukkit.broadcast(lm.getComponent("area.punishment.ominous"));
                // Ominous Punishment: Damage everyone? Lightning relative?
                // Let's strike lightning on everyone.
                for (Player p : meetingParticipants) {
                    p.getWorld().strikeLightning(p.getLocation());
                    p.damage(4.0);
                }
                break;
        }

        stopMeeting();
    }

    public void stopMeeting() {
        if (!isMeetingInProgress) return;
        meetingEpoch++;
        TheMazeRunner.getInstance().getVoteManager().reset();
        globalRingRight = false;
        // 清理状态
        isMeetingInProgress = false;
        LanguageManager lm = TheMazeRunner.getInstance().getLanguageManager();

        // 清理所有参与者的效果
        for (Player p : meetingParticipants) {
            p.removePotionEffect(PotionEffectType.GLOWING);
            p.removePotionEffect(PotionEffectType.DARKNESS);

            // 确保失明也移除 (茶水时间的残留?) - 茶水时间其实没有持续的失明，只有开始时的2秒
            p.removePotionEffect(PotionEffectType.BLINDNESS);
        }

        // Clear lists only AFTER loop
        // meetingParticipants.clear(); // Moved down to avoid ConcurrentModification if
        // loop uses it directly or logic issues.
        // Actually we iterate a clean list above? No, we iterate meetingParticipants.
        // It's safe to clear after.

        isTeaTimeInProgress = false;
        isFormalMeetingInProgress = false;
        isTrialInProgress = false;
        isReturnToGladePhase = false;

        if (speakerTask != -1) {
            Bukkit.getScheduler().cancelTask(speakerTask);
            speakerTask = -1;
        }

        // 恢复语音 (全部解除静音/清理群组)
        TheMazeRunner.getInstance().getVoiceManager().stopMeetingChat();

        Bukkit.broadcast(lm.getComponent("area.meeting.end"));

        if (bonfireRegion != null) bonfireRegion.forEachBlock(block -> {
            if (isCopperBulbVariant(block.getType())) {
                block.setType(Material.OXIDIZED_COPPER_BULB);
                if (block.getBlockData() instanceof Lightable) {
                    Lightable lightable = (Lightable) block.getBlockData();
                    lightable.setLit(true);
                    block.setBlockData(lightable);
                }
            }
        });

        // Finally clear participants
        meetingParticipants.clear();
        updateMeetingSnapshot();
        speakerQueue.clear();
    }

    private boolean isCopperBulbVariant(Material type) {
        return type == Material.COPPER_BULB ||
                type == Material.EXPOSED_COPPER_BULB ||
                type == Material.WEATHERED_COPPER_BULB ||
                type == Material.OXIDIZED_COPPER_BULB ||
                type == Material.WAXED_COPPER_BULB ||
                type == Material.WAXED_EXPOSED_COPPER_BULB ||
                type == Material.WAXED_WEATHERED_COPPER_BULB ||
                type == Material.WAXED_OXIDIZED_COPPER_BULB;
    }

    // Ring Rights Logic
    private boolean globalRingRight = false; // 是否有全局敲钟权

    /**
     * 给予全局敲钟权 (当有人员死亡时调用)
     */
    public void grantGlobalRingRight() {
        this.globalRingRight = true;
    }

    /**
     * 检查是否有敲钟权 (全局)
     *
     * @return 是否有权
     */
    public boolean hasRingRight() {
        return globalRingRight;
    }

    /**
     * @deprecated 使用无参 hasRingRight()
     */
    @Deprecated
    public boolean hasRingRight(Player player) {
        return hasRingRight();
    }

    /**
     * 消耗全局敲钟权
     *
     * @return 如果消耗成功返回 true，如果没有权限则返回 false
     */
    public boolean consumeRingRight() {
        if (globalRingRight) {
            globalRingRight = false;
            return true;
        }
        return false;
    }

    /**
     * @deprecated 使用无参 consumeRingRight()
     */
    @Deprecated
    public boolean consumeRingRight(Player player) {
        return consumeRingRight();
    }
}
