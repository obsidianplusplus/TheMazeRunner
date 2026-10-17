package com.panda.themazerunner.game.rules;

/** Pure AI state machine; perception/pathfinding remain limited by the adapter. */
public final class GrieverBrain {
    public enum State {
        PATROL,
        SENSE,
        CHASE,
        SEARCH,
        RETURN
    }

    private State state = State.PATROL;
    private long changedAt;
    private long nextAttack;

    public void retarget(long now) {
        state = State.SENSE;
        changedAt = now;
    }

    public State state() {
        return state;
    }

    public State step(boolean night, boolean seesTarget, long now) {
        if (!night) {
            change(State.RETURN, now);
            return state;
        }
        switch (state) {
            case RETURN -> change(State.PATROL, now);
            case PATROL -> {
                if (seesTarget) change(State.SENSE, now);
            }
            case SENSE -> {
                if (!seesTarget) change(State.SEARCH, now);
                else if (now - changedAt >= 2) change(State.CHASE, now);
            }
            case CHASE -> {
                if (!seesTarget) change(State.SEARCH, now);
            }
            case SEARCH -> {
                if (seesTarget) change(State.SENSE, now);
                else if (now - changedAt >= 6) change(State.PATROL, now);
            }
        }
        return state;
    }

    private void change(State next, long now) {
        if (state != next) {
            state = next;
            changedAt = now;
        }
    }

    public boolean attack(double distance, boolean lineOfSight, long now) {
        if (state != State.CHASE || distance > 2.5 || !lineOfSight || now < nextAttack)
            return false;
        nextAttack = now + 2;
        return true;
    }
}
