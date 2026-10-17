package com.panda.themazerunner.game.classes;

import com.panda.themazerunner.manager.LanguageManager;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.*;

import java.util.*;

/** Small native-API kits; supplies and abilities are independently server-budgeted. */
public final class ConfiguredClass extends GameClass {
    private final List<ItemStack> items;
    private final ItemStack[] armor;

    public ConfiguredClass(LanguageManager language, String id, ConfigurationSection section) {
        super(language, id);
        Object raw = section.get("items");
        if (!(raw instanceof List<?> list)
                || list.stream().anyMatch(value -> !(value instanceof String)))
            throw new IllegalArgumentException("classes." + id + ".items must be a string list");
        List<String> itemDefinitions = section.getStringList("items");
        if (itemDefinitions.size() > 20)
            throw new IllegalArgumentException("Too many items for " + id);
        items = itemDefinitions.stream().map(ConfiguredClass::parse).toList();
        var armorDefinitions = section.getStringList("armor");
        if (armorDefinitions.size() != 4)
            throw new IllegalArgumentException(
                    "classes." + id + ".armor must contain boots, leggings, chestplate, helmet");
        armor = armorDefinitions.stream().map(ConfiguredClass::parse).toArray(ItemStack[]::new);
        String[] suffixes = {"_BOOTS", "_LEGGINGS", "_CHESTPLATE", "_HELMET"};
        for (int i = 0; i < armor.length; i++)
            if (!armor[i].getType().name().endsWith(suffixes[i]))
                throw new IllegalArgumentException("Invalid armor slot: " + id + "." + i);
    }

    private static ItemStack parse(String definition) {
        String[] fields = definition.split(":");
        Material type = Material.matchMaterial(fields[0]);
        int count = fields.length == 1 ? 1 : Integer.parseInt(fields[1]);
        if (fields.length > 2
                || type == null
                || type.isAir()
                || !type.isItem()
                || count < 1
                || count > type.getMaxStackSize())
            throw new IllegalArgumentException("Invalid kit item: " + definition);
        return new ItemStack(type, count);
    }

    @Override
    public void onEquip(Player p) {
        for (int i = 0; i < items.size(); i++)
            p.getInventory().setItem(i < 8 ? i : i + 1, items.get(i).clone());
        p.getInventory()
                .setArmorContents(
                        Arrays.stream(armor).map(ItemStack::clone).toArray(ItemStack[]::new));
        if (id.equals("runner"))
            p.addPotionEffect(
                    new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
    }

    @Override
    public void onUnequip(Player p) {
        if (id.equals("runner")) p.removePotionEffect(PotionEffectType.SPEED);
    }
}
