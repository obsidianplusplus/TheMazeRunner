package com.panda.themazerunner.persistence;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Durable replacement snapshots, never merge inventories or drop overflow. Main thread only. */
public final class PlayerStateStore {
    private final Path directory;

    public PlayerStateStore(Path directory) {
        this.directory = directory;
    }

    private Path path(UUID id) {
        return directory.resolve(id + ".yml");
    }

    public boolean has(UUID id) {
        return Files.exists(path(id));
    }

    public void capture(Player p) throws IOException {
        if (has(p.getUniqueId())) throw new IOException("Unrestored player state exists");
        if (p.getItemOnCursor().getType() != Material.AIR)
            throw new IOException("Clear cursor before joining");
        var top = p.getOpenInventory().getTopInventory();
        if (top.getType() == org.bukkit.event.inventory.InventoryType.CRAFTING)
            for (int slot = 0; slot < Math.min(5, top.getSize()); slot++)
                if (top.getItem(slot) != null && !top.getItem(slot).getType().isAir())
                    throw new IOException("Clear crafting grid before joining");
        p.closeInventory();
        var data = new YamlConfiguration();
        data.set("schema", 2);
        data.set("location", p.getLocation());
        List<String> inventory =
                Arrays.stream(p.getInventory().getContents())
                        .map(
                                item ->
                                        item == null || item.getType().isAir()
                                                ? ""
                                                : Base64.getEncoder()
                                                        .encodeToString(item.serializeAsBytes()))
                        .toList();
        data.set("inventory", inventory);
        data.set("inventory-digest", digest(inventory));
        data.set("effects", new ArrayList<>(p.getActivePotionEffects()));
        data.set("health", p.getHealth());
        data.set("food", p.getFoodLevel());
        data.set("saturation", p.getSaturation());
        data.set("exhaustion", p.getExhaustion());
        data.set("level", p.getLevel());
        data.set("exp", p.getExp());
        data.set("total-exp", p.getTotalExperience());
        data.set("fire", p.getFireTicks());
        data.set("fall", p.getFallDistance());
        data.set("mode", p.getGameMode().name());
        data.set("allow-flight", p.getAllowFlight());
        data.set("flying", p.isFlying());
        data.set("walk", p.getWalkSpeed());
        data.set("fly", p.getFlySpeed());
        data.set("compass", p.getCompassTarget());
        data.set("air", p.getRemainingAir());
        validateState(data);
        AtomicFiles.write(path(p.getUniqueId()), data.saveToString());
    }

    private static void validateState(YamlConfiguration data) throws IOException {
        String[] keys = {
            "health",
            "food",
            "saturation",
            "exhaustion",
            "level",
            "exp",
            "total-exp",
            "walk",
            "fly",
            "air",
            "fire",
            "fall"
        };
        double[] minimum = {0.001, 0, 0, 0, 0, 0, 0, -1, -1, -1000, -100000, 0};
        double[] maximum = {
            100000,
            20,
            20,
            40,
            Integer.MAX_VALUE,
            1,
            Integer.MAX_VALUE,
            1,
            1,
            100000,
            10000000,
            100000
        };
        for (int i = 0; i < keys.length; i++) {
            Object value = data.get(keys[i]);
            if (!(value instanceof Number number)
                    || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() < minimum[i]
                    || number.doubleValue() > maximum[i])
                throw new IOException("Invalid recovery field: " + keys[i]);
        }
        try {
            GameMode.valueOf(data.getString("mode", ""));
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid recovery game mode", error);
        }
        if (!(data.get("allow-flight") instanceof Boolean)
                || !(data.get("flying") instanceof Boolean))
            throw new IOException("Invalid flight state");
    }

    private static String digest(List<String> inventory) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            String.join("|", inventory)
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public boolean restore(Player p) throws IOException {
        if (!has(p.getUniqueId())) return true;
        if (p.isDead()) return false;
        var data = new YamlConfiguration();
        try {
            data.load(path(p.getUniqueId()).toFile());
        } catch (org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IOException("Invalid player recovery YAML", error);
        }
        if (data.getInt("schema") != 2 || !data.isList("inventory"))
            throw new IOException("Invalid player recovery schema");
        Location location;
        try {
            location = data.getLocation("location");
        } catch (RuntimeException error) {
            throw new IOException("Recovery world missing", error);
        }
        if (location == null || location.getWorld() == null)
            throw new IOException("Recovery world missing");
        List<?> raw = data.getList("inventory");
        if (raw.size() != p.getInventory().getContents().length
                || raw.stream().anyMatch(i -> !(i instanceof String)))
            throw new IOException("Invalid recovery inventory");
        List<String> encoded = data.getStringList("inventory");
        if (!digest(encoded).equals(data.getString("inventory-digest")))
            throw new IOException("Recovery inventory checksum mismatch");
        ItemStack[] inventory = new ItemStack[encoded.size()];
        try {
            for (int i = 0; i < inventory.length; i++)
                if (!encoded.get(i).isEmpty()) {
                    inventory[i] =
                            ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded.get(i)));
                    if (inventory[i] == null || inventory[i].getType().isAir())
                        throw new IllegalArgumentException("Missing recovered item");
                }
        } catch (RuntimeException error) {
            throw new IOException("Cannot decode recovery item; original journal retained", error);
        }
        List<?> effects = data.getList("effects", List.of());
        if (effects.stream().anyMatch(e -> !(e instanceof PotionEffect)))
            throw new IOException("Invalid recovery effects");
        validateState(data);
        p.closeInventory();
        p.setItemOnCursor(null);
        if (!p.teleport(location))
            throw new IOException("Return teleport cancelled; snapshot retained");
        p.getInventory().setContents(inventory);
        for (var effect : p.getActivePotionEffects()) p.removePotionEffect(effect.getType());
        effects.forEach(effect -> p.addPotionEffect((PotionEffect) effect));
        p.setGameMode(GameMode.valueOf(data.getString("mode")));
        p.setHealth(
                Math.min(
                        data.getDouble("health"),
                        Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue()));
        p.setFoodLevel(data.getInt("food"));
        p.setSaturation((float) data.getDouble("saturation"));
        p.setExhaustion((float) data.getDouble("exhaustion"));
        p.setTotalExperience(data.getInt("total-exp"));
        p.setLevel(data.getInt("level"));
        p.setExp((float) data.getDouble("exp"));
        p.setFireTicks(data.getInt("fire"));
        p.setFallDistance((float) data.getDouble("fall"));
        p.setRemainingAir(data.getInt("air"));
        p.setAllowFlight(data.getBoolean("allow-flight"));
        p.setFlying(data.getBoolean("flying") && p.getAllowFlight());
        p.setWalkSpeed((float) data.getDouble("walk"));
        p.setFlySpeed((float) data.getDouble("fly"));
        Location compass = data.getLocation("compass");
        if (compass != null) p.setCompassTarget(compass);
        p.saveData(); // Persist replacement before deleting journal. Replays are idempotent
        // replacements.
        Files.delete(path(p.getUniqueId()));
        return true;
    }
}
