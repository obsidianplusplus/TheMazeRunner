package com.panda.themazerunner.game.classes.impl;

import com.panda.themazerunner.game.classes.GameClass;
import com.panda.themazerunner.manager.LanguageManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 建筑师 (The Builder) 职业实现
 * <p>
 * 特点: 挖掘速度快，携带建筑材料。
 * 职责: 建造防御，改造地形。
 */
public class Builder extends GameClass {

    public Builder(LanguageManager languageManager) {
        super(languageManager, "builder");
    }

    @Override
    public void onEquip(Player player) {
        // 给予急迫 I (挖掘速度提升)
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, Integer.MAX_VALUE, 0, false, false));

        // --- 物品 (方块) ---
        // 做旧铜块 (Weathered Copper)
        player.getInventory().addItem(new ItemStack(Material.WEATHERED_COPPER, 64));
        // 木板 (Oak Planks)
        player.getInventory().addItem(new ItemStack(Material.OAK_PLANKS, 64));
        // 圆石 (Cobblestone)
        player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 64));
        // 凝灰岩砖 (Tuff Bricks) - 1.21
        // 注意检查 Material 是否存在，Paper 1.21 API 应包含 TUFF_BRICKS
        try {
            player.getInventory().addItem(new ItemStack(Material.valueOf("TUFF_BRICKS"), 64));
        } catch (IllegalArgumentException e) {
            // Fallback for older API
            player.getInventory().addItem(new ItemStack(Material.STONE_BRICKS, 64));
        }

        // 铜灯 (Copper Bulb) - 1.21
        try {
            player.getInventory().addItem(new ItemStack(Material.valueOf("COPPER_BULB"), 16));
        } catch (IllegalArgumentException e) {
            // Fallback
            player.getInventory().addItem(new ItemStack(Material.REDSTONE_LAMP, 16));
        }

        // 红石
        player.getInventory().addItem(new ItemStack(Material.REDSTONE, 32));

        // --- 武器 ---
        // 石斧
        player.getInventory().addItem(new ItemStack(Material.STONE_AXE));

        // --- 护甲 ---
        // 金头盔 (工地安全帽)
        ItemStack helmet = new ItemStack(Material.GOLDEN_HELMET);

        // 锁链胸甲
        ItemStack chestplate = new ItemStack(Material.CHAINMAIL_CHESTPLATE);

        // 皮革裤子/靴子 (默认颜色或不需要染色，原著似乎比较随意，这里保持原色或不染色)
        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);

        player.sendMessage(languageManager.getMessage("errors.class_equipped").replace("%class%", getName()));
    }

    @Override
    public void onUnequip(Player player) {
        player.removePotionEffect(PotionEffectType.HASTE);
    }
}
