package com.panda.themazerunner.game.rules;

import java.util.*;

/** Monotonic match seconds, server-owned balances, commit only after target validation. */
public final class AbilityBudget {
    private int supplies;
    private final int cap;
    private final Map<String, Long> readyAt = new HashMap<>();

    public AbilityBudget(int initial, int cap) {
        if (initial < 0 || cap < initial) throw new IllegalArgumentException("Invalid supplies");
        this.supplies = initial;
        this.cap = cap;
    }

    public int supplies() {
        return supplies;
    }

    public void award(int value) {
        if (value < 0) throw new IllegalArgumentException();
        supplies = Math.min(cap, supplies + value);
    }

    private String key(UUID player, String action) {
        return player + ":" + action;
    }

    public long cooldown(UUID player, String action, long now) {
        return Math.max(0, readyAt.getOrDefault(key(player, action), 0L) - now);
    }

    public boolean spend(UUID player, String action, int cost, int cooldown, long now) {
        if (cost < 0 || cooldown < 0) throw new IllegalArgumentException();
        if (supplies < cost || cooldown(player, action, now) > 0) return false;
        supplies -= cost;
        readyAt.put(key(player, action), now + cooldown);
        return true;
    }
}
