package com.panda.themazerunner.game.rules;

import com.panda.themazerunner.game.match.VoteRules;

import java.util.*;

/** One immutable electorate, bounded discussion/vote windows; leaving cannot lower quorum. */
public final class MeetingRound {
    private final Set<UUID> electorate;
    private final Set<UUID> departed = new HashSet<>();
    private final Map<UUID, UUID> votes = new HashMap<>();
    private final long voteStarts, ends;

    public MeetingRound(Collection<UUID> members, long now, int discussion, int voting) {
        electorate = Set.copyOf(members);
        if (electorate.size() < 3 || discussion < 1 || voting < 1)
            throw new IllegalArgumentException("Invalid meeting");
        voteStarts = now + discussion;
        ends = voteStarts + voting;
    }

    public boolean voting(long now) {
        return now >= voteStarts && now < ends;
    }

    public boolean ended(long now) {
        return now >= ends;
    }

    public long secondsLeft(long now) {
        return Math.max(0, ends - now);
    }

    public Set<UUID> candidates() {
        Set<UUID> result = new HashSet<>(electorate);
        result.removeAll(departed);
        return Set.copyOf(result);
    }

    public boolean vote(UUID voter, UUID candidate, long now) {
        if (!voting(now)
                || departed.contains(voter)
                || !VoteRules.mayCast(true, electorate, votes, voter)
                || candidate != null
                        && (!electorate.contains(candidate) || departed.contains(candidate)))
            return false;
        votes.put(voter, candidate);
        return true;
    }

    public void leave(UUID id) {
        departed.add(id);
        votes.remove(id);
        votes.entrySet().removeIf(e -> id.equals(e.getValue()));
    }

    public UUID result() {
        UUID candidate = VoteRules.nominee(votes, candidates(), id -> !departed.contains(id));
        if (candidate == null) return null;
        long count = votes.values().stream().filter(candidate::equals).count();
        return count > electorate.size() / 2 ? candidate : null;
    }
}
