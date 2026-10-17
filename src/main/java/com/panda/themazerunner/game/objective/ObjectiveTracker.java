package com.panda.themazerunner.game.objective;

import java.util.*;

/** Validated DAG. A completed objective cannot award a second reward. */
public final class ObjectiveTracker {
    private final Map<String, ObjectiveDefinition> definitions = new LinkedHashMap<>();
    private final Set<String> failed = new HashSet<>();
    private final Map<String, Integer> openedAt = new HashMap<>();
    private final Map<String, Integer> progress = new HashMap<>();

    public ObjectiveTracker(Collection<ObjectiveDefinition> input) {
        if (input.isEmpty() || input.size() > 64)
            throw new IllegalArgumentException("Need 1..64 objectives");
        for (var d : input)
            if (definitions.putIfAbsent(d.id(), d) != null)
                throw new IllegalArgumentException("Duplicate objective " + d.id());
        for (var d : input)
            for (String parent : d.prerequisites())
                if (!definitions.containsKey(parent))
                    throw new IllegalArgumentException("Missing prerequisite " + parent);
        Set<String> visited = new HashSet<>();
        for (String id : definitions.keySet()) visit(id, new HashSet<>(), visited);
        if (input.stream().noneMatch(ObjectiveDefinition::required))
            throw new IllegalArgumentException("Need a required objective");
    }

    private void visit(String id, Set<String> path, Set<String> done) {
        if (done.contains(id)) return;
        if (!path.add(id)) throw new IllegalArgumentException("Objective cycle at " + id);
        for (String parent : definitions.get(id).prerequisites()) visit(parent, path, done);
        path.remove(id);
        done.add(id);
    }

    public List<ObjectiveDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    public boolean completed(String id) {
        var d = definitions.get(id);
        return d != null && progress.getOrDefault(id, 0) >= d.work();
    }

    public boolean available(String id) {
        var d = definitions.get(id);
        return d != null
                && !completed(id)
                && !failed.contains(id)
                && d.prerequisites().stream().allMatch(this::completed);
    }

    public List<ObjectiveDefinition> available() {
        return definitions.values().stream().filter(d -> available(d.id())).toList();
    }

    public int work(String id) {
        return progress.getOrDefault(id, 0);
    }

    /** Returns true only on the edge to COMPLETE; partial progress is separately inspectable. */
    public boolean advance(String id, int amount) {
        if (amount <= 0 || !available(id)) return false;
        var d = definitions.get(id);
        progress.put(id, Math.min(d.work(), work(id) + amount));
        return completed(id);
    }

    public Set<String> tick(int elapsed) {
        Set<String> added = new HashSet<>();
        boolean changed;
        do {
            changed = false;
            for (var d : definitions.values()) {
                if (completed(d.id()) || failed.contains(d.id())) continue;
                if (d.prerequisites().stream().anyMatch(failed::contains)) {
                    failed.add(d.id());
                    added.add(d.id());
                    changed = true;
                } else if (available(d.id())) {
                    openedAt.putIfAbsent(d.id(), elapsed);
                    if (d.deadlineSeconds() > 0
                            && elapsed - openedAt.get(d.id()) >= d.deadlineSeconds()) {
                        failed.add(d.id());
                        added.add(d.id());
                        changed = true;
                    }
                }
            }
        } while (changed);
        return Set.copyOf(added);
    }

    public boolean failed(String id) {
        return failed.contains(id);
    }

    public boolean requiredFailure() {
        return definitions.values().stream().anyMatch(d -> d.required() && failed(d.id()));
    }

    public int completedCount() {
        return (int) definitions.keySet().stream().filter(this::completed).count();
    }

    public boolean readyToExtract() {
        return definitions.values().stream()
                .filter(ObjectiveDefinition::required)
                .allMatch(d -> completed(d.id()));
    }

    public static List<ObjectiveDefinition> chain(List<String> ids) {
        List<ObjectiveDefinition> result = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++)
            result.add(
                    new ObjectiveDefinition(
                            ids.get(i),
                            ids.get(i),
                            ObjectiveDefinition.Type.CLUE,
                            ids.get(i),
                            i == 0 ? Set.of() : Set.of(ids.get(i - 1)),
                            1,
                            0,
                            true));
        return result;
    }
}
