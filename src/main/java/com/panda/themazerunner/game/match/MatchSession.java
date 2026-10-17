package com.panda.themazerunner.game.match;

import java.util.*;

/** Main-thread owned, Bukkit-free rules. A new instance is required for every round. */
public final class MatchSession {
    public enum Phase {
        LOBBY,
        COUNTDOWN,
        PREPARATION,
        ACTIVE,
        EXTRACTION,
        RESULTS,
        RESET
    }

    private Phase phase = Phase.LOBBY;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> alive = new HashSet<>();
    private final com.panda.themazerunner.game.objective.ObjectiveTracker objectives;
    private final com.panda.themazerunner.game.rules.ModeRules mode;
    private Map<UUID, com.panda.themazerunner.game.rules.ModeRules.Faction> factions = Map.of();
    private String outcome = "";

    public MatchSession(List<String> objectives) {
        this(
                com.panda.themazerunner.game.objective.ObjectiveTracker.chain(objectives),
                com.panda.themazerunner.game.rules.ModeRules.CORE);
    }

    public MatchSession(
            Collection<com.panda.themazerunner.game.objective.ObjectiveDefinition> definitions,
            com.panda.themazerunner.game.rules.ModeRules mode) {
        this.objectives = new com.panda.themazerunner.game.objective.ObjectiveTracker(definitions);
        this.mode = mode;
    }

    public com.panda.themazerunner.game.objective.ObjectiveTracker objectives() {
        return objectives;
    }

    public com.panda.themazerunner.game.rules.ModeRules.Faction faction(UUID id) {
        return factions.get(id);
    }

    public Phase phase() {
        return phase;
    }

    public Set<UUID> players() {
        return Set.copyOf(players);
    }

    public boolean alive(UUID id) {
        return alive.contains(id);
    }

    public int progress() {
        return objectives.completedCount();
    }

    public String currentObjective() {
        return objectives.available().isEmpty() ? "撤离" : objectives.available().getFirst().id();
    }

    public String outcome() {
        return outcome;
    }

    public boolean join(UUID id) {
        if (phase != Phase.LOBBY && phase != Phase.COUNTDOWN) return false;
        return players.add(id);
    }

    public void countdown(int minimum) {
        if (phase != Phase.LOBBY || players.size() < minimum)
            throw new IllegalStateException("Not ready");
        phase = Phase.COUNTDOWN;
    }

    public void cancelCountdown() {
        if (phase != Phase.COUNTDOWN) throw new IllegalStateException("Not counting down");
        phase = Phase.LOBBY;
    }

    public void prepare() {
        if (phase != Phase.COUNTDOWN) throw new IllegalStateException("Not counting down");
        factions = mode.assign(players, new Random());
        transition(Phase.COUNTDOWN, Phase.PREPARATION);
        alive.addAll(players);
    }

    public void activate() {
        transition(Phase.PREPARATION, Phase.ACTIVE);
    }

    public boolean discover(UUID player, String objective) {
        return contribute(player, objective, 1);
    }

    public boolean contribute(UUID player, String objective, int work) {
        if ((phase != Phase.ACTIVE && phase != Phase.EXTRACTION) || !alive(player)) return false;
        boolean complete = objectives.advance(objective, work);
        if (objectives.readyToExtract()) phase = Phase.EXTRACTION;
        return complete;
    }

    public boolean extract(UUID player) {
        if (phase != Phase.EXTRACTION
                || !alive(player)
                || faction(player) != com.panda.themazerunner.game.rules.ModeRules.Faction.GLADERS)
            return false;
        finish("ESCAPED");
        return true;
    }

    public void leave(UUID id) {
        players.remove(id);
        alive.remove(id);
        checkWipe();
    }

    public void eliminate(UUID id) {
        alive.remove(id);
        checkWipe();
    }

    private void checkWipe() {
        if (phase == Phase.ACTIVE || phase == Phase.EXTRACTION) {
            if (alive.stream()
                    .noneMatch(
                            id ->
                                    faction(id)
                                            == com.panda.themazerunner.game.rules.ModeRules.Faction
                                                    .GLADERS))
                finish(
                        mode == com.panda.themazerunner.game.rules.ModeRules.EXPERIMENT
                                ? "WCKD_WIN"
                                : "TEAM_LOST");
        }
    }

    public void finish(String reason) {
        if (phase == Phase.RESULTS || phase == Phase.RESET) return;
        outcome = reason;
        phase = Phase.RESULTS;
    }

    public void reset() {
        transition(Phase.RESULTS, Phase.RESET);
        alive.clear();
        players.clear();
        factions = Map.of();
    }

    private void transition(Phase from, Phase to) {
        if (phase != from)
            throw new IllegalStateException("Illegal transition " + phase + " -> " + to);
        phase = to;
    }
}
