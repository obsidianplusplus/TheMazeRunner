package com.panda.themazerunner.game.match;

import com.panda.themazerunner.TheMazeRunner;
import com.panda.themazerunner.game.arena.*;
import com.panda.themazerunner.game.classes.ConfiguredClass;
import com.panda.themazerunner.game.objective.*;
import com.panda.themazerunner.game.rules.*;
import com.panda.themazerunner.game.threat.GrieverManager;
import com.panda.themazerunner.gate.Gate;
import com.panda.themazerunner.persistence.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.*;

/**
 * Coordinates one bounded arena. Objective, threat, resource and mode rules have independent
 * ownership.
 */
public final class MatchManager implements Listener, CommandExecutor, TabCompleter {
    private static final List<String> CLASSES =
            List.of("runner", "guardian", "medjack", "builder", "cook");
    private final TheMazeRunner plugin;
    private final PlayerStateStore states;
    private final Telemetry telemetry;
    private final BukkitTask ticker;
    private final NamespacedKey toolKey;
    private final Map<UUID, String> choices = new LinkedHashMap<>();
    private final Map<UUID, Long> disconnected = new HashMap<>();
    private final Set<UUID> eliminated = new HashSet<>();
    private final Set<UUID> restoring = new HashSet<>();
    private final Map<UUID, Integer> actors = new HashMap<>();
    private final Map<UUID, Integer> contributions = new HashMap<>();
    private final Map<UUID, Set<String>> knowledge = new HashMap<>();
    private final Map<UUID, Long> scannedUntil = new HashMap<>();
    private final Set<UUID> projectiles = new HashSet<>();
    private final List<Gate> gates = new ArrayList<>();
    private GameSettings settings;
    private ArenaMap arena;
    private MatchSession session;
    private AbilityBudget budget;
    private EnvironmentCycle environment;
    private EnvironmentCycle.Frame lastFrame;
    private GrieverManager grievers;
    private MeetingRound meeting;
    private volatile Set<UUID> speakingMembers = Set.of();
    private long meetingSequence;
    private int meetingCount;
    private long nextMeeting;
    private String round = "waiting";
    private String configurationError = "";
    private int remaining, elapsed, initialPlayers;
    private long clock, maxTickNanos;
    private boolean started, internalTeleport, broken, building, shuttingDown, recoveryBarrier;
    private final BossBar boss = Bukkit.createBossBar("迷宫准备", BarColor.BLUE, BarStyle.SOLID);

    public MatchManager(TheMazeRunner plugin) {
        this.plugin = plugin;
        states = new PlayerStateStore(plugin.getDataFolder().toPath().resolve("players"));
        toolKey = new NamespacedKey(plugin, "ability");
        Telemetry writer = null;
        try {
            writer =
                    new Telemetry(
                            plugin.getDataFolder().toPath().resolve("statistics"),
                            plugin.getLogger());
        } catch (IOException e) {
            plugin.getLogger().warning("Statistics unavailable: " + e.getMessage());
        }
        telemetry = writer;
        configure();
        for (Player p : Bukkit.getOnlinePlayers()) restore(p);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::safeTick, 20, 20);
    }

    public boolean contains(Player p) {
        return choices.containsKey(p.getUniqueId()) || restoring.contains(p.getUniqueId());
    }

    public boolean active(Player p) {
        return choices.containsKey(p.getUniqueId())
                && session != null
                && session.alive(p.getUniqueId())
                && !eliminated.contains(p.getUniqueId())
                && running();
    }

    public boolean running() {
        return session != null
                && (session.phase() == MatchSession.Phase.ACTIVE
                        || session.phase() == MatchSession.Phase.EXTRACTION);
    }

    public boolean busy() {
        return !choices.isEmpty() || building || restoring.size() > 0;
    }

    public boolean protectedWorld(World world) {
        return arena != null && arena.world().equals(world);
    }

    public boolean canEdit(Player p) {
        return !busy() && p.hasPermission("tmr.admin");
    }

    public MatchSession.Phase phase() {
        return session == null ? MatchSession.Phase.LOBBY : session.phase();
    }

    public ModeRules.Faction faction(Player player) {
        return session == null ? null : session.faction(player.getUniqueId());
    }

    public int supplies() {
        return budget == null ? 0 : budget.supplies();
    }

    public String result() {
        return session == null ? "" : session.outcome();
    }

    public boolean ownsMenu(Inventory inventory) {
        return inventory.getHolder() instanceof Menu;
    }

    public boolean owns(Entity entity) {
        return grievers != null && grievers.owns(entity);
    }

    public boolean allowedTeleport() {
        return internalTeleport;
    }

    public boolean insideArena(Location location) {
        return arena != null && arena.contains(location);
    }

    private String organization() {
        return plugin.getConfig()
                .getString(
                        "profiles." + settings.profile() + ".organization",
                        settings.profile().equals("NOVEL") ? "WICKED" : "WCKD");
    }

    private String phaseLabel() {
        if (session == null) return "等待地图配置";
        return switch (session.phase()) {
            case LOBBY -> "等待玩家";
            case COUNTDOWN -> "开局倒计时";
            case PREPARATION -> "实验简报";
            case ACTIVE -> "迷宫探索";
            case EXTRACTION -> "撤离";
            case RESULTS -> "实验结算";
            case RESET -> "环境复原";
        };
    }

    private String outcomeLabel(String value) {
        return switch (value) {
            case "ESCAPED" -> "团队逃脱成功";
            case "TEAM_LOST" -> "幸存者全部失去行动能力";
            case "TIMEOUT" -> "实验时间耗尽";
            case "WCKD_WIN" -> "实验控制方达成目标";
            case "OBJECTIVE_FAILED" -> "撤离关键目标失败";
            case "ADMIN_STOP" -> "管理员中止";
            case "SHUTDOWN" -> "服务正在关闭";
            case "INTERNAL_ERROR" -> "环境故障，对局中止并恢复";
            default -> "本轮实验结束";
        };
    }

    private void tell(Player p, String message) {
        p.sendMessage(Component.text("[迷宫] " + message));
    }

    private List<Player> players() {
        return choices.keySet().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }

    private List<Player> living() {
        return players().stream().filter(this::active).toList();
    }

    private void broadcast(String text) {
        players().forEach(p -> tell(p, text));
    }

    private void record(Player p, String event, double value, String detail) {
        if (telemetry != null && started)
            telemetry.record(
                    round,
                    settings.mode().name(),
                    arena.world().getName(),
                    settings.profile(),
                    elapsed,
                    p == null ? 0 : actors.getOrDefault(p.getUniqueId(), 0),
                    p == null ? "" : choices.getOrDefault(p.getUniqueId(), ""),
                    p == null ? "" : String.valueOf(session.faction(p.getUniqueId())),
                    event,
                    value,
                    detail);
    }

    private Set<WalkGrid.Cell> gateCells() {
        Set<WalkGrid.Cell> cells = new HashSet<>();
        gates.forEach(g -> cells.addAll(g.footprint()));
        return Set.copyOf(cells);
    }

    private boolean configure() {
        if (busy()) return false;
        try {
            plugin.reloadConfig();
            settings = GameSettings.load(plugin.getConfig());
            arena = new ArenaMap(plugin.getConfig());
            gates.clear();
            Object gateConfig = plugin.getConfig().get("match.gates");
            if (!(gateConfig instanceof List<?> list)
                    || list.size() > 8
                    || list.stream().anyMatch(id -> !(id instanceof String)))
                throw new IllegalArgumentException(
                        "match.gates must be a list of at most 8 gate IDs");
            for (String id : plugin.getConfig().getStringList("match.gates")) {
                Gate gate = plugin.getGateManager().getGate(id);
                if (gate == null || !gate.world().equals(arena.world()))
                    throw new IllegalArgumentException("Unknown or wrong-world gate: " + id);
                gates.add(gate);
            }
            for (Gate gate : gates)
                if (!gate.forceRestore()) throw new IllegalStateException("大门仍被占用，不能完成地图重置");
            arena.validate(settings.objectives(), gateCells());
            var classes = plugin.getConfig().getConfigurationSection("classes");
            if (classes == null)
                throw new IllegalArgumentException(
                        "Missing classes; migrate config from the bundled config.yml");
            for (String id : CLASSES) {
                var config = classes.getConfigurationSection(id);
                if (config == null) throw new IllegalArgumentException("Missing classes." + id);
                plugin.getClassManager()
                        .registerClass(
                                new ConfiguredClass(plugin.getLanguageManager(), id, config));
            }
            resetSession();
            configurationError = "";
            broken = false;
            return true;
        } catch (RuntimeException e) {
            configurationError =
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            plugin.getLogger().warning("Arena unavailable: " + configurationError);
            broken = true;
            return false;
        }
    }

    private void resetSession() {
        session = new MatchSession(settings.objectives(), settings.mode());
        budget = new AbilityBudget(settings.initialSupplies(), settings.supplyCap());
        environment =
                new EnvironmentCycle(
                        settings.day(),
                        settings.night(),
                        settings.warning(),
                        settings.eventInterval(),
                        settings.eventDuration());
        round = UUID.randomUUID().toString();
        sabotageEnergy = 3;
        nextSabotage = 0;
        sabotageUntil = 0;
        sabotageStarts = 0;
        meetingOpened = false;
        finishing = false;
        lastFrame = null;
        elapsed = 0;
        remaining = 0;
        meeting = null;
        speakingMembers = Set.of();
        meetingCount = 0;
        nextMeeting = 0;
        started = false;
        actors.clear();
        contributions.clear();
        knowledge.clear();
        scannedUntil.clear();
        disconnected.clear();
        eliminated.clear();
    }

    private void restore(Player p) {
        if (!states.has(p.getUniqueId())) {
            restoring.remove(p.getUniqueId());
            return;
        }
        restoring.add(p.getUniqueId());
        internalTeleport = true;
        try {
            if (states.restore(p)) {
                restoring.remove(p.getUniqueId());
                if (recoveryBarrier && restoring.isEmpty()) {
                    recoveryBarrier = false;
                    broken = false;
                    configurationError = "";
                }
                tell(p, "入局前状态已恢复。");
            }
        } catch (IOException | RuntimeException error) {
            tell(p, "恢复暂未完成，请联系管理员；原始快照已保留。");
            plugin.getLogger().severe("Player recovery failed: " + error.getMessage());
            if (plugin.getConfig().getBoolean("diagnostics.stacktraces", false))
                plugin.getLogger().log(java.util.logging.Level.WARNING, "recovery", error);
        } finally {
            internalTeleport = false;
        }
    }

    private boolean teleport(Player p, Location point) {
        internalTeleport = true;
        try {
            return p.teleport(point);
        } finally {
            internalTeleport = false;
        }
    }

    private boolean lobbyPhase() {
        return session != null
                && (session.phase() == MatchSession.Phase.LOBBY
                        || session.phase() == MatchSession.Phase.COUNTDOWN);
    }

    private void join(Player p) {
        if (contains(p)) {
            showClasses(p);
            return;
        }
        if (states.has(p.getUniqueId())) {
            restore(p);
            return;
        }
        if (broken || building || settings == null || arena == null) {
            tell(p, "地图未就绪：" + configurationError);
            return;
        }
        if (!lobbyPhase() || choices.size() >= settings.maximum()) {
            tell(p, "本局已开始或人数已满，请稍候。");
            return;
        }
        if (p.isDead() || p.isInsideVehicle() || p.getGameMode() == GameMode.SPECTATOR) {
            tell(p, "请先复活并离开载具。");
            return;
        }
        try {
            states.capture(p);
        } catch (IOException error) {
            tell(p, "无法保存入局状态，已拒绝加入。");
            return;
        }
        if (!session.join(p.getUniqueId())) {
            restore(p);
            return;
        }
        choices.put(p.getUniqueId(), "runner");
        knowledge.put(p.getUniqueId(), new HashSet<>());
        plugin.getIdentityManager().removeIdentity(p);
        p.getInventory().clear();
        for (var effect : p.getActivePotionEffects()) p.removePotionEffect(effect.getType());
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(false);
        p.setHealth(Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue());
        p.setFoodLevel(20);
        p.setSaturation(5);
        p.setFireTicks(0);
        if (!teleport(p, arena.point("spawn"))) {
            leave(p, "JOIN_TELEPORT_FAILED");
            return;
        }
        boss.addPlayer(p);
        tell(p, "探索路线 → 记录线索 → 修复撤离设施 → 团队撤离。职业可在开始前更换，不会重复发装。");
        tell(p, "潜行右键职责工具释放技能；资源有限。空地可以休整、交流；夜间遇敌请遮挡视线并撤退。");
        showClasses(p);
    }

    private void beginCountdown(boolean force) {
        if (!lobbyPhase() || session.phase() != MatchSession.Phase.LOBBY) return;
        int minimum = force && settings.mode() == ModeRules.CORE ? 1 : settings.minimum();
        if (choices.size() < minimum) return;
        session.countdown(minimum);
        remaining = settings.countdown();
        // A one-player debug start is explicit and retained only for this countdown.
        countdownMinimum = minimum;
        broadcast(remaining + "秒后准备进入迷宫；人数不足将取消。");
    }

    private int countdownMinimum;

    private void prepare() {
        arena.validate(settings.objectives(), gateCells());
        session.prepare();
        round = UUID.randomUUID().toString();
        started = true;
        initialPlayers = choices.size();
        int actor = 1;
        for (UUID id : choices.keySet()) actors.put(id, actor++);
        for (Player p : players()) {
            p.closeInventory();
            if (!teleport(p, arena.point("spawn"))
                    || !plugin.getClassManager().equipClass(p, choices.get(p.getUniqueId())))
                throw new IllegalStateException("Preparation failed");
            p.getInventory().setItem(8, abilityTool(p));
            p.setGameMode(GameMode.ADVENTURE);
            tell(p, "你的职责：" + choices.get(p.getUniqueId()) + "；潜行右键第9格工具使用能力。");
            if (settings.mode() == ModeRules.EXPERIMENT)
                tell(
                        p,
                        session.faction(p.getUniqueId()) == ModeRules.Faction.WCKD
                                ? "秘密阵营 "
                                        + organization()
                                        + "：阻止幸存者逃脱直到超时；/maze sabotage 消耗实验能量，留下可调查的干扰记录。"
                                : "阵营 幸存者：完成路线任务并撤离；仅在空地通过有证据的会议表决实验嫌疑人。");
            record(p, "class_selected", 1, "");
        }
        for (String line :
                plugin.getConfig().getStringList("profiles." + settings.profile() + ".briefing"))
            broadcast(line);
        remaining = settings.preparation();
        broadcast("简报：白天探索，日落前评估撤退。Griever发现你后会先发出预警；线索由团队共享。");
        record(null, "match_started", initialPlayers, "");
        grievers = new GrieverManager(plugin, arena, settings.grievers());
        gates.forEach(Gate::open);
        if (gates.stream().anyMatch(g -> g.getState() == Gate.State.CLOSED))
            throw new IllegalStateException("Gate opening failed; check gate recovery storage");
    }

    private ItemStack abilityTool(Player p) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("职责工具 · 潜行右键"));
        meta.lore(List.of(Component.text("地图、护卫、救治、修理或烹饪；消耗团队补给")));
        meta.getPersistentDataContainer()
                .set(toolKey, PersistentDataType.STRING, round + ":" + p.getUniqueId());
        item.setItemMeta(meta);
        return item;
    }

    private boolean tool(Player p, ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && (round + ":" + p.getUniqueId())
                        .equals(
                                item.getItemMeta()
                                        .getPersistentDataContainer()
                                        .get(toolKey, PersistentDataType.STRING));
    }

    private void safeTick() {
        long start = System.nanoTime();
        clock++;
        try {
            tick();
        } catch (RuntimeException error) {
            configurationError = "运行故障：" + error.getMessage();
            plugin.getLogger().severe("Match fault: " + error.getMessage());
            if (session != null) finish("INTERNAL_ERROR");
            broken = true;
        } finally {
            maxTickNanos = Math.max(maxTickNanos, System.nanoTime() - start);
        }
    }

    private void tick() {
        if (session == null) return;
        if (broken && session.phase() != MatchSession.Phase.RESULTS) return;
        for (var entry : Map.copyOf(disconnected).entrySet())
            if (clock >= entry.getValue()) {
                UUID id = entry.getKey();
                String job = choices.getOrDefault(id, "");
                disconnected.remove(id);
                session.leave(id);
                eliminated.add(id);
                plugin.getClassManager().forget(id);
                choices.remove(id);
                if (meeting != null) {
                    meeting.leave(id);
                    speakingMembers = meeting.candidates();
                }
                if (telemetry != null && started)
                    telemetry.record(
                            round,
                            settings.mode().name(),
                            arena.world().getName(),
                            settings.profile(),
                            elapsed,
                            actors.getOrDefault(id, 0),
                            job,
                            String.valueOf(session.faction(id)),
                            "disconnect_expired",
                            1,
                            "");
            }
        if (session.phase() == MatchSession.Phase.RESULTS && !finishing) {
            finish(session.outcome());
            return;
        }
        switch (session.phase()) {
            case LOBBY -> {
                if (choices.size() >= settings.minimum()) beginCountdown(false);
                hud();
            }
            case COUNTDOWN -> {
                if (choices.size() < countdownMinimum) {
                    session.cancelCountdown();
                    broadcast("人数不足，倒计时取消。");
                    break;
                }
                if (--remaining <= 0) prepare();
                hud();
            }
            case PREPARATION -> {
                if (--remaining <= 0) {
                    session.activate();
                    remaining = settings.duration();
                    broadcast("探索开始。靠近任务点右键调查；修理需多次工作，所有职业均能完成。");
                }
                hud();
            }
            case ACTIVE, EXTRACTION -> {
                elapsed++;
                remaining--;
                if (remaining <= 0) {
                    finish(settings.mode() == ModeRules.EXPERIMENT ? "WCKD_WIN" : "TIMEOUT");
                    break;
                }
                if (living().isEmpty() && disconnected.isEmpty()) {
                    finish("TEAM_LOST");
                    break;
                }
                for (String id : session.objectives().tick(elapsed)) {
                    broadcast("目标超时或前置失败：" + id);
                    record(null, "objective_failed", 1, id);
                }
                if (session.objectives().requiredFailure()) {
                    finish(
                            settings.mode() == ModeRules.EXPERIMENT
                                    ? "WCKD_WIN"
                                    : "OBJECTIVE_FAILED");
                    break;
                }
                var frame = environment.at(elapsed);
                updateEnvironment(frame);
                grievers.tick(frame.night(), elapsed, living());
                for (Player p : living()) {
                    if (!arena.contains(p.getLocation())) {
                        eliminate(p, "OUT_OF_BOUNDS");
                        continue;
                    }
                    if (session.phase() == MatchSession.Phase.EXTRACTION
                            && p.getLocation().distanceSquared(arena.point("exit")) <= 4
                            && session.extract(p.getUniqueId())) {
                        finish("ESCAPED");
                        break;
                    }
                    for (var d : session.objectives().available())
                        if (d.type() == ObjectiveDefinition.Type.EXPLORE
                                && p.getLocation().distanceSquared(arena.point(d.point())) <= 9)
                            contribute(p, d, 1);
                    if (arena.glade(p.getLocation())
                            && elapsed % 10 == 0
                            && p.getHealth()
                                    < Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH))
                                            .getValue()
                            && budget.spend(p.getUniqueId(), "glade-care", 1, 10, clock)) {
                        p.setHealth(
                                Math.min(
                                        p.getHealth() + 2,
                                        Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH))
                                                .getValue()));
                        record(p, "healing", 2, "glade");
                    }
                }
                updateMeeting();
                hud();
            }
            case RESULTS -> {
                if (--remaining <= 0) reset();
            }
            case RESET -> {}
        }
    }

    private void updateEnvironment(EnvironmentCycle.Frame frame) {
        if (lastFrame == null || frame.night() != lastFrame.night()) {
            arena.world().setTime(frame.night() ? 14000 : 1000);
            broadcast(frame.night() ? "夜间：Griever开始活动。可退回空地，也可沿备用路线继续探索。" : "白天：门路恢复，抓紧记录路线。");
            record(null, "day_night", frame.night() ? 1 : 0, "");
        }
        if (frame.duskWarning() && (lastFrame == null || !lastFrame.duskWarning()))
            warn("日落预警：" + settings.warning() + "秒后关门并进入夜间。", Sound.BLOCK_BELL_RESONATE);
        if (lastFrame == null || frame.event() != lastFrame.event()) {
            if (frame.event() == EnvironmentCycle.Event.WARNING)
                warn(
                        "路线预警："
                                + settings.warning()
                                + "秒后实验支路封锁，持续"
                                + settings.eventDuration()
                                + "秒；沿外环绕行可反制。",
                        Sound.BLOCK_NOTE_BLOCK_BASS);
            if (frame.event() == EnvironmentCycle.Event.CLOSED) {
                record(null, "route_event", 1, "closed");
                broadcast("支路封锁已开始，所有必需点保留绕行路线。");
            }
            if (frame.event() == EnvironmentCycle.Event.NONE
                    && lastFrame != null
                    && lastFrame.event() == EnvironmentCycle.Event.CLOSED) broadcast("实验支路恢复。");
        }
        boolean close =
                frame.night()
                        || frame.event() == EnvironmentCycle.Event.CLOSED
                        || clock >= sabotageStarts && clock < sabotageUntil;
        if (close
                && arena.canClose(
                        gateCells(),
                        living().stream().map(Player::getLocation).toList(),
                        settings.objectives())) {
            if (elapsed % 3 == 0)
                for (Gate gate : gates) {
                    var before = gate.getState();
                    gate.close();
                    if (before != gate.getState()) record(null, "gate_close", 1, gate.getName());
                }
        } else if (!close)
            for (Gate gate : gates) {
                var before = gate.getState();
                gate.open();
                if (before != gate.getState()) record(null, "gate_open", 1, gate.getName());
            }
        lastFrame = frame;
    }

    private void warn(String message, Sound sound) {
        broadcast(message);
        players().forEach(p -> p.playSound(p.getLocation(), sound, 0.7f, 0.7f));
    }

    private void hud() {
        if (settings == null) return;
        String title =
                session.phase()
                        + " | "
                        + (running() ? remaining : Math.max(0, remaining))
                        + "s | 补给 "
                        + budget.supplies()
                        + " | 人数 "
                        + choices.size()
                        + "/"
                        + settings.maximum();
        title =
                plugin.getConfig()
                        .getString(
                                "hud.boss",
                                "%phase% | %seconds%s | 补给 %supplies% | 人数 %players%/%maximum%")
                        .replace("%phase%", phaseLabel())
                        .replace("%seconds%", Integer.toString(Math.max(0, remaining)))
                        .replace("%supplies%", Integer.toString(budget.supplies()))
                        .replace("%players%", Integer.toString(choices.size()))
                        .replace("%maximum%", Integer.toString(settings.maximum()));
        boss.setTitle(title);
        boss.setColor(lastFrame != null && lastFrame.night() ? BarColor.RED : BarColor.BLUE);
        boss.setProgress(
                running() ? Math.max(0, Math.min(1, (double) remaining / settings.duration())) : 1);
        for (Player p : players()) {
            String objective =
                    session.phase() == MatchSession.Phase.EXTRACTION
                            ? "前往出口撤离"
                            : session.objectives().available().stream()
                                    .map(ObjectiveDefinition::title)
                                    .findFirst()
                                    .orElse("选择职业并等待开局");
            String area = arena.glade(p.getLocation()) ? "林间空地" : "迷宫";
            if (session.phase() == MatchSession.Phase.EXTRACTION)
                objective =
                        "出口 X="
                                + arena.point("exit").getBlockX()
                                + " Z="
                                + arena.point("exit").getBlockZ();
            String action =
                    plugin.getConfig()
                            .getString(
                                    "hud.actionbar",
                                    "%area% | %objective% | %progress%/%total% | 技能 %cooldown%s")
                            .replace("%area%", area)
                            .replace("%objective%", objective)
                            .replace("%progress%", Integer.toString(session.progress()))
                            .replace("%total%", Integer.toString(settings.objectives().size()))
                            .replace(
                                    "%cooldown%",
                                    Long.toString(
                                            budget.cooldown(
                                                    p.getUniqueId(),
                                                    choices.get(p.getUniqueId()),
                                                    clock)));
            p.sendActionBar(Component.text(action));
            if (session.phase() == MatchSession.Phase.EXTRACTION)
                p.spawnParticle(
                        Particle.END_ROD, arena.point("exit").add(0, 1, 0), 2, 0.15, 0.3, 0.15, 0);
            if (running())
                for (var d : session.objectives().available()) {
                    Location target = arena.point(d.point());
                    double distance = p.getLocation().distanceSquared(target);
                    if (distance <= 36
                            || scannedUntil.getOrDefault(p.getUniqueId(), 0L) > clock
                                    && distance
                                            <= Math.pow(
                                                    settings.abilities().get("runner").range(),
                                                    2)) {
                        p.spawnParticle(
                                Particle.HAPPY_VILLAGER,
                                target.clone().add(0, 0.8, 0),
                                2,
                                0.2,
                                0.2,
                                0.2,
                                0);
                        if (distance <= 36
                                && knowledge
                                        .computeIfAbsent(p.getUniqueId(), id -> new HashSet<>())
                                        .add("hint:" + d.id()))
                            tell(p, "附近目标：" + d.title() + "；右键标记地面调查，修理需重复操作。");
                    }
                }
        }
    }

    private void contribute(Player p, ObjectiveDefinition d, int amount) {
        if (!active(p) || !session.objectives().available(d.id())) return;
        int before = session.objectives().work(d.id());
        boolean complete = session.contribute(p.getUniqueId(), d.id(), amount);
        if (session.objectives().work(d.id()) > before)
            contributions.merge(p.getUniqueId(), Math.min(amount, d.work() - before), Integer::sum);
        if (complete) {
            budget.award(d.supplies());
            record(p, "resource_awarded", d.supplies(), d.id());
            record(p, "objective_completed", d.work(), d.id());
            if (d.type() == ObjectiveDefinition.Type.CLUE) record(p, "clue_found", 1, d.id());
            players()
                    .forEach(
                            player ->
                                    knowledge
                                            .computeIfAbsent(
                                                    player.getUniqueId(), id -> new HashSet<>())
                                            .add(d.id()));
            broadcast("目标完成：" + d.title() + "；团队补给 +" + d.supplies() + "。");
            if (session.phase() == MatchSession.Phase.EXTRACTION)
                broadcast("路线与撤离设施已确认！前往出口；任一幸存者撤离即团队胜利。");
        } else if (session.objectives().work(d.id()) > before)
            tell(p, d.title() + "：" + session.objectives().work(d.id()) + "/" + d.work());
    }

    private void skill(Player p) {
        if (!active(p)) {
            tell(p, "当前阶段不能使用技能。");
            return;
        }
        String job = choices.get(p.getUniqueId());
        var rule = settings.abilities().get(job);
        if (!budget.spend(p.getUniqueId(), "ability-input", 0, 1, clock)) return;
        record(p, "skill_attempt", 1, job);
        List<Player> nearby =
                living().stream()
                        .filter(
                                target ->
                                        target.getLocation().distanceSquared(p.getLocation())
                                                <= rule.range() * rule.range())
                        .toList();
        ObjectiveDefinition repair =
                session.objectives().available().stream()
                        .filter(
                                d ->
                                        d.type() == ObjectiveDefinition.Type.REPAIR
                                                && p.getLocation()
                                                                .distanceSquared(
                                                                        arena.point(d.point()))
                                                        <= rule.range() * rule.range())
                        .findFirst()
                        .orElse(null);
        Player injured =
                nearby.stream()
                        .filter(
                                target ->
                                        target.getHealth()
                                                < Objects.requireNonNull(
                                                                target.getAttribute(
                                                                        Attribute.MAX_HEALTH))
                                                        .getValue())
                        .min(Comparator.comparingDouble(Player::getHealth))
                        .orElse(null);
        if (job.equals("runner")
                && session.objectives().available().stream()
                        .noneMatch(
                                d ->
                                        arena.point(d.point()).distanceSquared(p.getLocation())
                                                <= rule.range() * rule.range())) {
            tell(p, "侦察范围内没有可记录目标，请先深入迷宫；未消耗补给。");
            return;
        }
        if (job.equals("medjack") && injured == null) {
            tell(p, "范围内没有需要救治的队友。");
            return;
        }
        if (job.equals("builder") && repair == null) {
            tell(p, "附近没有已解锁的待修设施。普通右键可帮助团队调查线索。");
            return;
        }
        if (!budget.spend(p.getUniqueId(), job, rule.cost(), rule.cooldown(), clock)) {
            tell(
                    p,
                    "技能需要补给"
                            + rule.cost()
                            + "；当前"
                            + budget.supplies()
                            + "，冷却剩余"
                            + budget.cooldown(p.getUniqueId(), job, clock)
                            + "秒。");
            return;
        }
        record(p, "resource_spent", rule.cost(), job);
        record(p, "skill_success", 1, job);
        switch (job) {
            case "runner" -> {
                scannedUntil.put(p.getUniqueId(), clock + 20);
                var target =
                        session.objectives().available().stream()
                                .filter(
                                        d ->
                                                arena.point(d.point())
                                                                .distanceSquared(p.getLocation())
                                                        <= rule.range() * rule.range())
                                .min(
                                        Comparator.comparingDouble(
                                                d ->
                                                        arena.point(d.point())
                                                                .distanceSquared(p.getLocation())))
                                .orElse(null);
                if (target != null) {
                    Location loc = arena.point(target.point());
                    broadcast(
                            "行者情报："
                                    + target.title()
                                    + " 位于 X="
                                    + loc.getBlockX()
                                    + " Z="
                                    + loc.getBlockZ()
                                    + "；已共享路线标记。");
                }
                living().forEach(player -> scannedUntil.put(player.getUniqueId(), clock + 20));
                record(p, "route_shared", 1, "");
            }
            case "guardian" -> {
                nearby.forEach(
                        player ->
                                player.addPotionEffect(
                                        new PotionEffect(
                                                PotionEffectType.RESISTANCE, 120, 0, false, true)));
                tell(p, "护卫覆盖" + nearby.size() + "位队友，持续6秒；不能免疫全部伤害。");
                record(p, "protected", nearby.size(), "");
            }
            case "medjack" -> {
                double heal =
                        Math.min(
                                8,
                                Objects.requireNonNull(injured.getAttribute(Attribute.MAX_HEALTH))
                                                .getValue()
                                        - injured.getHealth());
                injured.setHealth(injured.getHealth() + heal);
                injured.removePotionEffect(PotionEffectType.POISON);
                tell(injured, "Med-jack完成救治。");
                record(p, "healing", heal, "");
            }
            case "builder" -> contribute(p, repair, 2);
            case "cook" -> {
                for (Player player : nearby) {
                    player.setFoodLevel(Math.min(20, player.getFoodLevel() + 4));
                    player.setSaturation(Math.min(10, player.getSaturation() + 2));
                }
                tell(p, "消耗补给制成热餐，已分配给" + nearby.size() + "位队友。");
                record(p, "food_served", nearby.size(), "");
            }
        }
    }

    private void eliminate(Player p, String reason) {
        if (!choices.containsKey(p.getUniqueId()) || !eliminated.add(p.getUniqueId())) return;
        record(p, "eliminated", elapsed, reason);
        session.eliminate(p.getUniqueId());
        if (meeting != null) {
            meeting.leave(p.getUniqueId());
            speakingMembers = meeting.candidates();
        }
        if (!p.isDead()) {
            teleport(p, arena.point("spawn"));
            p.setGameMode(GameMode.SPECTATOR);
            p.getInventory().clear();
            tell(
                    p,
                    "本局已淘汰："
                            + (reason.startsWith("DEATH")
                                    ? "生命耗尽"
                                    : reason.equals("VOTED_OUT") ? "参会者多数表决" : "离开实验区域")
                            + "；可等待结算或 /maze leave。");
        }
        if (session.phase() == MatchSession.Phase.RESULTS) finish(session.outcome());
    }

    private void finish(String outcome) {
        if (session == null || session.phase() == MatchSession.Phase.RESET) return;
        if (finishing) return;
        finishing = true;
        session.finish(outcome);
        remaining = settings.results();
        meeting = null;
        speakingMembers = Set.of();
        if (grievers != null) grievers.clear();
        for (UUID id : projectiles) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
        projectiles.clear();
        broadcast("结算：" + outcomeLabel(session.outcome()) + "。" + settings.results() + "秒后恢复入局状态。");
        for (Player p : players())
            tell(
                    p,
                    "个人贡献：任务工作 "
                            + contributions.getOrDefault(p.getUniqueId(), 0)
                            + "；已获线索 "
                            + knowledge.getOrDefault(p.getUniqueId(), Set.of()).stream()
                                    .filter(s -> !s.startsWith("hint:"))
                                    .count()
                            + "。");
        record(null, "match_result", initialPlayers, session.outcome());
    }

    private boolean finishing;

    private void leave(Player p, String reason) {
        if (!choices.containsKey(p.getUniqueId())) {
            restore(p);
            return;
        }
        record(p, "left", elapsed, reason);
        boss.removePlayer(p);
        disconnected.remove(p.getUniqueId());
        if (meeting != null) {
            meeting.leave(p.getUniqueId());
            speakingMembers = meeting.candidates();
        }
        plugin.getClassManager().unequipClass(p);
        plugin.getIdentityManager().removeIdentity(p);
        session.leave(p.getUniqueId());
        choices.remove(p.getUniqueId());
        eliminated.remove(p.getUniqueId());
        restore(p);
        if (session.phase() == MatchSession.Phase.RESULTS && !finishing) finish(session.outcome());
    }

    private void reset() {
        if (grievers != null) grievers.clear();
        for (Player p : players()) leave(p, "RESET");
        choices.keySet().forEach(plugin.getClassManager()::forget);
        choices.clear();
        boss.removeAll();
        boolean restored = true;
        for (Gate gate : gates) if (!gate.forceRestore()) restored = false;
        session.reset();
        finishing = false;
        if (!restored) {
            broken = true;
            configurationError = "大门仍被实体占据，地图未重置。清理后 /maze validate。";
            started = false;
            return;
        }
        resetSession();
        if (!restoring.isEmpty()) {
            recoveryBarrier = true;
            broken = true;
            configurationError = "有玩家状态等待恢复；管理员处理后 /maze validate。";
        }
    }

    public void shutdown() {
        shuttingDown = true;
        ticker.cancel();
        try {
            if (session != null) {
                finish("SHUTDOWN");
                reset();
            }
        } finally {
            boss.removeAll();
            if (grievers != null) grievers.clear();
            if (telemetry != null) telemetry.close();
        }
    }

    private long sabotageUntil, nextSabotage;
    private int sabotageEnergy = 3;

    private void sabotage(Player p) {
        if (!active(p)
                || settings.mode() != ModeRules.EXPERIMENT
                || session.faction(p.getUniqueId()) != ModeRules.Faction.WCKD) {
            tell(p, "没有实验干预权限。");
            return;
        }
        if (gates.isEmpty()) {
            tell(p, "本地图没有可干预的可选支路。");
            return;
        }
        if (clock < nextSabotage || sabotageEnergy < 1) {
            tell(p, "干预冷却或能量不足；每局最多3次，冷却60秒。");
            return;
        }
        if (!arena.canClose(
                gateCells(),
                living().stream().map(Player::getLocation).toList(),
                settings.objectives())) {
            tell(p, "干预被安全系统拒绝：会封死必要路线。");
            return;
        }
        nextSabotage = clock + 60;
        sabotageEnergy--;
        sabotageUntil = clock + settings.warning() + 20;
        warn(
                "实验干预信号：" + settings.warning() + "秒后可选支路关闭20秒。可沿外环绕行，并在空地讨论刚刚的异常。",
                Sound.BLOCK_NOTE_BLOCK_BASS);
        broadcast(
                "可调查干扰痕迹：X="
                        + p.getLocation().getBlockX()
                        + " Z="
                        + p.getLocation().getBlockZ()
                        + "。结合现场观察讨论，痕迹本身不证明身份。");
        // Warning interval is respected separately by the gate decision.
        sabotageStarts = clock + settings.warning();
        record(p, "sabotage", 1, "optional_route");
    }

    private long sabotageStarts;

    private void startMeeting(Player p) {
        if (!active(p) || !arena.glade(p.getLocation())) {
            tell(p, "请先回到林间空地。");
            return;
        }
        if (settings.mode() == ModeRules.CORE) {
            if (!budget.spend(p.getUniqueId(), "rally", 1, 60, clock)) {
                tell(p, "集结需要1补给且冷却60秒。");
                return;
            }
            broadcast("空地集结：请共享路线、分配补给；探索与时间继续推进。");
            record(p, "rally", 1, "");
            return;
        }
        if (meeting != null || meetingCount >= 2 || clock < nextMeeting) {
            tell(p, "会议未冷却、正在进行或本局次数已用完。");
            return;
        }
        List<UUID> members =
                living().stream()
                        .filter(player -> arena.glade(player.getLocation()))
                        .map(Player::getUniqueId)
                        .toList();
        if (members.size() < 3) {
            tell(p, "空地至少需要3位存活玩家参会。");
            return;
        }
        if (!budget.spend(p.getUniqueId(), "meeting", 3, 120, clock)) {
            tell(p, "召开会议需要3点团队补给。");
            return;
        }
        meeting = new MeetingRound(members, clock, 30, 15);
        speakingMembers = Set.copyOf(members);
        meetingSequence++;
        meetingCount++;
        nextMeeting = clock + 120;
        broadcast("实验会议：空地参会者讨论30秒，再投票15秒。探索不停；需参会快照绝对多数才淘汰，平票/弃权不惩罚。");
        record(p, "meeting_started", members.size(), "");
    }

    private boolean meetingOpened;

    private void updateMeeting() {
        if (meeting == null) {
            meetingOpened = false;
            return;
        }
        if (meeting.voting(clock) && !meetingOpened) {
            meetingOpened = true;
            for (Player p : living())
                if (meeting.candidates().contains(p.getUniqueId())) showVote(p);
        }
        if (meeting.ended(clock)) {
            UUID candidate = meeting.result();
            record(
                    null,
                    "meeting_result",
                    candidate == null ? 0 : actors.getOrDefault(candidate, 0),
                    "majority");
            meeting = null;
            speakingMembers = Set.of();
            meetingOpened = false;
            broadcast(candidate == null ? "会议结束：未达到绝对多数，无人淘汰。" : "会议结束：多数意见通过，实验嫌疑人退出本轮测试。");
            if (candidate != null) {
                Player target = Bukkit.getPlayer(candidate);
                if (target != null) eliminate(target, "VOTED_OUT");
            }
        }
    }

    private enum MenuKind {
        LOBBY,
        CLASSES,
        VOTE
    }

    private static final class Menu implements InventoryHolder {
        final MenuKind kind;
        final UUID owner;
        final String round;
        final long meeting;
        final Map<Integer, String> actions = new HashMap<>();
        final Map<Integer, UUID> candidates = new HashMap<>();
        final Inventory inventory;

        Menu(MenuKind kind, Player p, String round, long meeting, String title) {
            this.kind = kind;
            owner = p.getUniqueId();
            this.round = round;
            this.meeting = meeting;
            inventory =
                    Bukkit.createInventory(
                            this, kind == MenuKind.VOTE ? 54 : 27, Component.text(title));
        }

        public Inventory getInventory() {
            return inventory;
        }

        void item(int slot, Material type, String title, List<String> lore) {
            ItemStack item = new ItemStack(type);
            var meta = item.getItemMeta();
            meta.displayName(
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacySection()
                            .deserialize(title));
            meta.lore(lore.stream().map(Component::text).toList());
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }
    }

    private void showLobby(Player p) {
        Menu menu = new Menu(MenuKind.LOBBY, p, round, meetingSequence, "TheMazeRunner · 迷宫实验");
        menu.item(
                11,
                Material.COMPASS,
                "快速加入",
                List.of("合作探索、线索与逃脱", "状态：" + (broken ? "配置待完成" : phaseLabel())));
        menu.actions.put(11, "join");
        menu.item(
                15,
                Material.BOOK,
                "新手指引",
                List.of("白天探索，夜间注意危险", "右键目标；潜行右键工具施放技能", "空地可消耗有限补给休整", "完成必需目标后抵达出口"));
        p.openInventory(menu.inventory);
    }

    private void showClasses(Player p) {
        if (!choices.containsKey(p.getUniqueId())) {
            showLobby(p);
            return;
        }
        if (!lobbyPhase()) {
            tell(p, "开局后不能更换职业。");
            return;
        }
        Menu menu = new Menu(MenuKind.CLASSES, p, round, meetingSequence, "选择团队职责");
        Material[] icons = {
            Material.COMPASS, Material.SHIELD, Material.RED_DYE, Material.ANVIL, Material.BREAD
        };
        String[] duties = {"侦察并共享路线标记", "短时间保护附近队友", "救治附近受伤队友", "加速修复撤离设施", "加工补给并分配热餐"};
        for (int i = 0; i < CLASSES.size(); i++) {
            String id = CLASSES.get(i);
            var ability = settings.abilities().get(id);
            menu.item(
                    10 + i,
                    icons[i],
                    plugin.getClassManager().getGameClass(id).getName(),
                    List.of(
                            duties[i],
                            "补给 " + ability.cost() + "，冷却 " + ability.cooldown() + "秒",
                            "所有职业都能调查、修理与撤离"));
            menu.actions.put(10 + i, id);
        }
        p.openInventory(menu.inventory);
    }

    private void showVote(Player p) {
        showVote(p, 0);
    }

    private void showVote(Player p, int page) {
        if (meeting == null
                || !meeting.voting(clock)
                || !meeting.candidates().contains(p.getUniqueId())) return;
        List<Player> candidates =
                meeting.candidates().stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(Player::getName))
                        .toList();
        int pages = Math.max(1, (candidates.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));
        Menu menu =
                new Menu(
                        MenuKind.VOTE,
                        p,
                        round,
                        meetingSequence,
                        "实验会议 · 一人一票 · " + (page + 1) + "/" + pages);
        for (int i = page * 45; i < Math.min(candidates.size(), (page + 1) * 45); i++) {
            Player candidate = candidates.get(i);
            int slot = i - page * 45;
            menu.item(
                    slot,
                    Material.PLAYER_HEAD,
                    candidate.getName(),
                    List.of("依据可验证线索投票", "需参会快照绝对多数"));
            menu.candidates.put(slot, candidate.getUniqueId());
        }
        if (page > 0) {
            menu.item(45, Material.ARROW, "上一页", List.of());
            menu.actions.put(45, Integer.toString(page - 1));
        }
        if (page + 1 < pages) {
            menu.item(53, Material.ARROW, "下一页", List.of());
            menu.actions.put(53, Integer.toString(page + 1));
        }
        menu.item(49, Material.BARRIER, "弃权", List.of("平票和弃权不造成团队伤害"));
        p.openInventory(menu.inventory);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        boolean admin =
                Set.of(
                                "template",
                                "set",
                                "bounds",
                                "validate",
                                "start",
                                "stop",
                                "reload",
                                "debug",
                                "mode")
                        .contains(sub);
        if (admin && !sender.hasPermission("tmr.admin")) {
            sender.sendMessage("需要 tmr.admin 权限。");
            return true;
        }
        try {
            if (sub.equals("debug")) {
                sender.sendMessage(
                        "phase="
                                + (session == null ? "unconfigured" : session.phase())
                                + " config="
                                + configurationError
                                + " players="
                                + choices.size()
                                + " maxTickMs="
                                + (maxTickNanos / 1_000_000.0)
                                + " grievers="
                                + (grievers == null ? 0 : grievers.count())
                                + " paths="
                                + (grievers == null ? 0 : grievers.pathRequests())
                                + " droppedTelemetry="
                                + (telemetry == null ? 0 : telemetry.dropped()));
                return true;
            }
            if (sub.equals("validate") || sub.equals("reload")) {
                sender.sendMessage(
                        configure() ? "配置、目标依赖及开/关门路线检查通过。" : "无法加载/对局忙碌：" + configurationError);
                return true;
            }
            if (sub.equals("stop")) {
                if (session != null) finish("ADMIN_STOP");
                return true;
            }
            if (sub.equals("start")) {
                if (!broken) beginCountdown(true);
                sender.sendMessage(
                        broken
                                ? "地图不可用：" + configurationError
                                : "当前阶段：" + session.phase() + "，人数：" + choices.size());
                return true;
            }
            if (sub.equals("mode") && args.length == 2) {
                if (busy()) throw new IllegalArgumentException("请先结束所有对局与恢复");
                ModeRules mode = ModeRules.valueOf(args[1].toUpperCase(Locale.ROOT));
                plugin.getConfig().set("match.mode", mode.name());
                if (mode == ModeRules.EXPERIMENT)
                    plugin.getConfig().set("match.minimum-players", 4);
                plugin.saveConfig();
                configure();
                return true;
            }
            if (!(sender instanceof Player p)) {
                sender.sendMessage("此操作需要玩家位置。");
                return true;
            }
            switch (sub) {
                case "template" -> {
                    if (busy()) throw new IllegalArgumentException("对局忙碌");
                    building = true;
                    try {
                        ArenaTemplate.create(
                                plugin,
                                message -> {
                                    building = false;
                                    configure();
                                    tell(p, message);
                                });
                    } catch (RuntimeException e) {
                        building = false;
                        throw e;
                    }
                    tell(p, "正在分批构建独立模板世界。");
                }
                case "set" -> {
                    if (busy() || args.length != 2 || !args[1].matches("[a-z0-9_-]{1,48}"))
                        throw new IllegalArgumentException("/maze set <点位ID>，仅空闲时设置");
                    plugin.getConfig().set("match.world", p.getWorld().getName());
                    plugin.getConfig()
                            .set("match.points." + args[1], Locations.encode(p.getLocation()));
                    plugin.saveConfig();
                    tell(p, "点位已保存；完成后 /maze validate。");
                }
                case "bounds" -> {
                    if (busy() || args.length != 5)
                        throw new IllegalArgumentException(
                                "/maze bounds <minX> <maxX> <minZ> <maxZ>");
                    String[] keys = {"min-x", "max-x", "min-z", "max-z"};
                    for (int i = 0; i < 4; i++)
                        plugin.getConfig()
                                .set("match.bounds." + keys[i], Integer.parseInt(args[i + 1]));
                    plugin.saveConfig();
                    configure();
                }
                case "join" -> join(p);
                case "leave" -> leave(p, "PLAYER_LEFT");
                case "class" -> showClasses(p);
                case "skill" -> skill(p);
                case "rally", "meeting" -> startMeeting(p);
                case "journal" -> {
                    if (!contains(p)) tell(p, "请先加入对局。");
                    else
                        for (var d : settings.objectives())
                            tell(
                                    p,
                                    (session.objectives().failed(d.id())
                                                    ? "已失败："
                                                    : session.objectives().completed(d.id())
                                                            ? "已完成："
                                                            : session.objectives().available(d.id())
                                                                    ? "可调查："
                                                                    : "待解锁：")
                                            + d.title());
                }
                case "vote" -> showVote(p);
                case "sabotage" -> sabotage(p);
                case "menu" -> showLobby(p);
                default ->
                        tell(
                                p,
                                "/maze 打开大厅；/maze class 职业；/maze leave 退出；管理员使用"
                                        + " template、validate、debug。");
            }
        } catch (RuntimeException error) {
            sender.sendMessage("操作失败：" + error.getMessage());
            if (plugin.getConfig().getBoolean("diagnostics.stacktraces", false))
                plugin.getLogger().log(java.util.logging.Level.WARNING, "maze " + sub, error);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> options =
                new ArrayList<>(
                        List.of(
                                "join", "leave", "class", "rally", "meeting", "vote", "skill",
                                "journal"));
        if (sender.hasPermission("tmr.admin"))
            options.addAll(
                    List.of(
                            "template",
                            "set",
                            "bounds",
                            "validate",
                            "start",
                            "stop",
                            "reload",
                            "debug",
                            "mode"));
        return options.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!contains(p)) return;
        if (e.getAction() == Action.PHYSICAL) {
            e.setCancelled(true);
            return;
        }
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction().isRightClick() && tool(p, e.getItem())) {
            e.setCancelled(true);
            if (p.isSneaking()) skill(p);
            else tell(p, "潜行右键释放职责能力。任务标记附近直接右键地面可调查。");
            return;
        }
        e.setUseInteractedBlock(Event.Result.DENY);
        if (!active(p) || !e.getAction().isRightClick() || e.getClickedBlock() == null) return;
        for (var d : session.objectives().available()) {
            Location point = arena.point(d.point());
            if (p.getLocation().distanceSquared(point) <= 16
                    && e.getClickedBlock().getLocation().distanceSquared(point) <= 4) {
                if (budget.spend(
                        p.getUniqueId(),
                        "objective",
                        0,
                        d.type() == ObjectiveDefinition.Type.REPAIR ? 3 : 1,
                        clock)) contribute(p, d, 1);
                else
                    interactionHint(
                            p,
                            "工作冷却剩余 "
                                    + budget.cooldown(p.getUniqueId(), "objective", clock)
                                    + "秒。");
                return;
            }
        }
        for (var d : settings.objectives()) {
            Location point = arena.point(d.point());
            if (p.getLocation().distanceSquared(point) <= 16
                    && e.getClickedBlock().getLocation().distanceSquared(point) <= 4) {
                interactionHint(
                        p,
                        session.objectives().completed(d.id())
                                ? "目标已经完成，不会重复发放奖励。"
                                : "目标尚未解锁，请先完成日志中的前置调查。");
                return;
            }
        }
    }

    private void interactionHint(Player player, String message) {
        if (budget.spend(player.getUniqueId(), "interaction-hint", 0, 2, clock))
            tell(player, message);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)
                || !(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!menu.owner.equals(p.getUniqueId())
                || !menu.round.equals(round)
                || e.getRawSlot() < 0
                || e.getRawSlot() >= menu.inventory.getSize()) return;
        int slot = e.getRawSlot();
        switch (menu.kind) {
            case LOBBY -> {
                if ("join".equals(menu.actions.get(slot))) join(p);
            }
            case CLASSES -> {
                if (lobbyPhase()
                        && choices.containsKey(p.getUniqueId())
                        && menu.actions.containsKey(slot)) {
                    choices.put(p.getUniqueId(), menu.actions.get(slot));
                    tell(p, "已选择 " + menu.actions.get(slot));
                    p.closeInventory();
                }
            }
            case VOTE -> {
                if (meeting != null
                        && menu.meeting == meetingSequence
                        && menu.actions.containsKey(slot)) {
                    showVote(p, Integer.parseInt(menu.actions.get(slot)));
                    return;
                }
                if (meeting != null
                        && menu.meeting == meetingSequence
                        && (slot == 49 || menu.candidates.containsKey(slot))
                        && meeting.vote(p.getUniqueId(), menu.candidates.get(slot), clock)) {
                    record(p, "vote", 1, slot == 49 ? "abstain" : "cast");
                    tell(p, "投票已记录。");
                    p.closeInventory();
                }
            }
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @EventHandler
    public void meetingChat(io.papermc.paper.event.player.AsyncChatEvent event) {
        Set<UUID> members = speakingMembers;
        if (members.isEmpty()) return;
        boolean sender = members.contains(event.getPlayer().getUniqueId());
        event.viewers()
                .removeIf(
                        viewer ->
                                viewer instanceof Player recipient
                                        && sender != members.contains(recipient.getUniqueId()));
    }

    @EventHandler
    public void login(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (disconnected.containsKey(id)
                && clock < disconnected.get(id)
                && running()
                && session.alive(id)) {
            disconnected.remove(id);
            boss.addPlayer(p);
            tell(p, "已在重连宽限内返回；沿用原职业与状态，没有重新发装。");
            record(p, "reconnected", 1, "");
        } else {
            if (choices.containsKey(id)) leave(p, "LATE_RECONNECT");
            else restore(p);
            p.sendMessage(
                    Component.text("[迷宫] 点击打开游戏大厅").clickEvent(ClickEvent.runCommand("/maze")));
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!choices.containsKey(p.getUniqueId())) return;
        if (running() && active(p) && settings.reconnectSeconds() > 0) {
            disconnected.put(p.getUniqueId(), clock + settings.reconnectSeconds());
            boss.removePlayer(p);
            if (meeting != null) {
                meeting.leave(p.getUniqueId());
                speakingMembers = meeting.candidates();
            }
            record(p, "disconnected", 1, "");
        } else leave(p, "DISCONNECTED");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(PlayerDeathEvent e) {
        if (!choices.containsKey(e.getEntity().getUniqueId())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.setKeepInventory(true);
        eliminate(
                e.getEntity(),
                "DEATH:"
                        + String.valueOf(
                                e.getEntity().getLastDamageCause() == null
                                        ? "unknown"
                                        : e.getEntity().getLastDamageCause().getCause()));
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent e) {
        if (!choices.containsKey(e.getPlayer().getUniqueId())
                && !states.has(e.getPlayer().getUniqueId())) return;
        if (arena != null) e.setRespawnLocation(arena.point("spawn"));
        if (!shuttingDown)
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                Player p = e.getPlayer();
                                if (choices.containsKey(p.getUniqueId())) {
                                    p.setGameMode(GameMode.SPECTATOR);
                                    p.getInventory().clear();
                                } else restore(p);
                            });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent e) {
        if (owns(e.getDamager()) && grievers != null && !grievers.applyingDamage()) {
            e.setCancelled(true);
            return;
        }
        Player source =
                e.getDamager() instanceof Player p
                        ? p
                        : e.getDamager() instanceof Projectile pr
                                        && pr.getShooter() instanceof Player p
                                ? p
                                : null;
        if (source != null && owns(e.getEntity()) && !active(source)) {
            e.setCancelled(true);
            return;
        }
        if (source != null
                && e.getEntity() instanceof Player target
                && (contains(source) || contains(target))) e.setCancelled(true);
        if (source != null && contains(source) && !active(source)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damageStatistic(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && active(p))
            record(
                    p,
                    "damage_taken",
                    e.getFinalDamage(),
                    e instanceof EntityDamageByEntityEvent by && owns(by.getDamager())
                            ? "GRIEVER"
                            : e.getCause().name());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && contains(p)) {
            if (!active(p)) {
                e.setCancelled(true);
                return;
            }
            if (e.getCause() == EntityDamageEvent.DamageCause.FALL && arena.glade(p.getLocation()))
                e.setCancelled(true);
        }
    }

    @EventHandler
    public void target(EntityTargetLivingEntityEvent e) {
        if (owns(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void monsterDeath(EntityDeathEvent e) {
        if (owns(e.getEntity())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            record(null, "griever_defeated", 1, "");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void launch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player p && contains(p)) {
            if (!active(p)) {
                e.setCancelled(true);
                return;
            }
            projectiles.add(e.getEntity().getUniqueId());
        }
    }
}
