package com.panda.themazerunner.game.threat;

import com.panda.themazerunner.TheMazeRunner;
import com.panda.themazerunner.game.arena.ArenaMap;
import com.panda.themazerunner.game.rules.GrieverBrain;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;

import java.util.*;

/** At most eight mobs, one perception pass and one path request per mob per second. */
public final class GrieverManager {
    private record Hunter(Spider mob, Location nest, GrieverBrain brain) {}

    private final List<Hunter> hunters = new ArrayList<>();
    private final NamespacedKey key;
    private final ArenaMap arena;
    private final int maximum;
    private final double detection, damage;
    private final Map<UUID, UUID> targets = new HashMap<>();
    private final Map<UUID, Location> lastSeen = new HashMap<>();
    private boolean applyingDamage;
    private long pathRequests;

    public GrieverManager(TheMazeRunner plugin, ArenaMap arena, int maximum) {
        this.arena = arena;
        this.maximum = maximum;
        key = new NamespacedKey(plugin, "griever");
        detection =
                com.panda.themazerunner.game.rules.GameSettings.integer(
                        plugin.getConfig(), "griever.detection", 18, 4, 32);
        damage =
                com.panda.themazerunner.game.rules.GameSettings.integer(
                        plugin.getConfig(), "griever.damage", 4, 1, 12);
    }

    public boolean owns(Entity entity) {
        return hunters.stream().anyMatch(h -> h.mob.getUniqueId().equals(entity.getUniqueId()));
    }

    public boolean applyingDamage() {
        return applyingDamage;
    }

    public long pathRequests() {
        return pathRequests;
    }

    public int count() {
        return hunters.size();
    }

    public void tick(boolean night, long now, List<Player> alive) {
        hunters.removeIf(h -> !h.mob.isValid());
        Set<UUID> current =
                hunters.stream()
                        .map(h -> h.mob.getUniqueId())
                        .collect(java.util.stream.Collectors.toSet());
        targets.keySet().retainAll(current);
        lastSeen.keySet().retainAll(current);
        if (night && hunters.size() < maximum) {
            Location nest = arena.has("hunter-1") ? arena.point("hunter-1") : arena.point("exit");
            if (arena.has("hunter-2") && hunters.size() % 2 == 1) nest = arena.point("hunter-2");
            Location home = nest;
            if (alive.stream().noneMatch(p -> p.getLocation().distanceSquared(home) < 36)
                    && !arena.glade(nest)) {
                Spider mob =
                        arena.world()
                                .spawn(
                                        home,
                                        Spider.class,
                                        s -> {
                                            s.customName(Component.text("Griever · 实验巡猎体"));
                                            s.setCustomNameVisible(true);
                                            s.setPersistent(false);
                                            s.setRemoveWhenFarAway(false);
                                            s.getPersistentDataContainer()
                                                    .set(
                                                            key,
                                                            org.bukkit.persistence
                                                                    .PersistentDataType.BYTE,
                                                            (byte) 1);
                                            Objects.requireNonNull(
                                                            s.getAttribute(Attribute.MAX_HEALTH))
                                                    .setBaseValue(24);
                                            s.setHealth(24);
                                            Objects.requireNonNull(
                                                            s.getAttribute(
                                                                    Attribute.MOVEMENT_SPEED))
                                                    .setBaseValue(0.23);
                                        });
                hunters.add(new Hunter(mob, home.clone(), new GrieverBrain()));
            }
        }
        for (Hunter h : hunters) {
            Player target =
                    alive.stream()
                            .filter(
                                    p ->
                                            p.getWorld().equals(arena.world())
                                                    && !arena.glade(p.getLocation())
                                                    && p.getLocation()
                                                                    .distanceSquared(
                                                                            h.mob.getLocation())
                                                            <= detection * detection
                                                    && h.mob.hasLineOfSight(p))
                            .min(
                                    Comparator.comparingDouble(
                                            p ->
                                                    p.getLocation()
                                                            .distanceSquared(h.mob.getLocation())))
                            .orElse(null);
            var before = h.brain.state();
            boolean changed =
                    target != null
                            && !target.getUniqueId()
                                    .equals(targets.put(h.mob.getUniqueId(), target.getUniqueId()));
            if (target != null) lastSeen.put(h.mob.getUniqueId(), target.getLocation());
            if (changed) h.brain.retarget(now);
            var state = h.brain.step(night, target != null, now);
            h.mob.setTarget(null);
            if (state == GrieverBrain.State.SENSE
                    && (before != state || changed)
                    && target != null) {
                target.sendMessage(Component.text("[危险] Griever 已察觉你，2秒后追击！退回空地或利用墙壁遮挡视线。"));
                target.playSound(target.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 0.7f, 0.5f);
            }
            if (state == GrieverBrain.State.CHASE && target != null) {
                h.mob.getPathfinder().moveTo(target, 1.0);
                pathRequests++;
                if (h.brain.attack(h.mob.getLocation().distance(target.getLocation()), true, now)) {
                    applyingDamage = true;
                    try {
                        target.damage(damage, h.mob);
                    } finally {
                        applyingDamage = false;
                    }
                }
            } else if (state == GrieverBrain.State.SEARCH
                    && lastSeen.containsKey(h.mob.getUniqueId())) {
                h.mob.getPathfinder().moveTo(lastSeen.get(h.mob.getUniqueId()), 0.8);
                pathRequests++;
            } else if (state == GrieverBrain.State.RETURN || state == GrieverBrain.State.PATROL) {
                Location waypoint = h.nest;
                if (state == GrieverBrain.State.PATROL && arena.has("hunter-2"))
                    waypoint =
                            arena.point(
                                    (now / 10 + hunters.indexOf(h)) % 2 == 0
                                            ? "hunter-1"
                                            : "hunter-2");
                if (h.mob.getLocation().distanceSquared(waypoint) > 4) {
                    h.mob.getPathfinder().moveTo(waypoint, 0.8);
                    pathRequests++;
                } else h.mob.getPathfinder().stopPathfinding();
            } else h.mob.getPathfinder().stopPathfinding();
            if (arena.glade(h.mob.getLocation())) h.mob.teleport(h.nest);
        }
        if (!night) {
            hunters.forEach(h -> h.mob.remove());
            hunters.clear();
            targets.clear();
        }
    }

    public void clear() {
        hunters.forEach(h -> h.mob.remove());
        hunters.clear();
        targets.clear();
        lastSeen.clear();
    }
}
