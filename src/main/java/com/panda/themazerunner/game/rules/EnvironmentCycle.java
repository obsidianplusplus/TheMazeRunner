package com.panda.themazerunner.game.rules;

/** Deterministic, bounded world-pressure timeline; all dangerous transitions have a warning. */
public final class EnvironmentCycle {
    public enum Event {
        NONE,
        WARNING,
        CLOSED
    }

    public record Frame(boolean night, boolean duskWarning, Event event, int eventSecondsLeft) {}

    private final int day, night, warning, interval, duration;

    public EnvironmentCycle(int day, int night, int warning, int interval, int duration) {
        if (day <= warning
                || night <= warning
                || warning < 1
                || interval <= duration + warning
                || duration < 1)
            throw new IllegalArgumentException("Event interval must exceed duration + warning");
        this.day = day;
        this.night = night;
        this.warning = warning;
        this.interval = interval;
        this.duration = duration;
    }

    public Frame at(int elapsed) {
        int cycle = Math.floorMod(elapsed, day + night), event = Math.floorMod(elapsed, interval);
        Event state =
                event >= interval - warning
                        ? Event.WARNING
                        : elapsed >= interval && event < duration ? Event.CLOSED : Event.NONE;
        return new Frame(
                cycle >= day,
                cycle >= day - warning && cycle < day,
                state,
                state == Event.WARNING
                        ? interval - event
                        : state == Event.CLOSED ? duration - event : 0);
    }
}
