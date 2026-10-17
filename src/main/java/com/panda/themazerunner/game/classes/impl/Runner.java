package com.panda.themazerunner.game.classes.impl;

import com.panda.themazerunner.game.classes.GameClass;
import com.panda.themazerunner.manager.LanguageManager;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 行者 (The Runner) 职业实现
 * <p>
 * 特点: 移动速度快，跳跃能力强。
 * 职责: 探索迷宫，绘制地图。
 */
public class Runner extends GameClass {

    public Runner(LanguageManager languageManager) {
        super(languageManager, "runner");
    }

    @Override
    public void onEquip(Player player) {
        // 给予速度 I 效果，持续时间无限 (Integer.MAX_VALUE)
        // 注意: 放大倍率 0 代表等级 I
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));

        // --- 物品 ---
        // 指南针
        player.getInventory().addItem(new ItemStack(Material.COMPASS));
        // 望远镜
        player.getInventory().addItem(new ItemStack(Material.SPYGLASS));
        // 空地图
        player.getInventory().addItem(new ItemStack(Material.MAP));

        // --- 武器 ---
        // 铁剑
        player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
        // 风弹 (Wind Charge) - 需要 1.21+ API
        // 注意: 如果 API 版本较旧可能没有 WIND_CHARGE，请确保依赖正确
        // No launch shortcut across protected maze walls.

        // --- 护甲 ---
        // 皮革套装 (深绿色)
        ItemStack helmet = createColoredArmor(Material.LEATHER_HELMET, Color.fromRGB(0, 100, 0)); // Dark Green
        ItemStack chestplate = createColoredArmor(Material.LEATHER_CHESTPLATE, Color.fromRGB(0, 100, 0));
        ItemStack leggings = createColoredArmor(Material.LEATHER_LEGGINGS, Color.fromRGB(0, 100, 0));
        ItemStack boots = createColoredArmor(Material.LEATHER_BOOTS, Color.fromRGB(0, 100, 0));

        // 靴子附魔：轻盈 (Feather Falling)
        boots.addUnsafeEnchantment(Enchantment.FEATHER_FALLING, 1);

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);

        player.sendMessage(languageManager.getMessage("errors.class_equipped").replace("%class%", getName()));
    }

    @Override
    public void onUnequip(Player player) {
        player.removePotionEffect(PotionEffectType.SPEED);
        // 清空物品或仅移除特定物品视游戏逻辑而定，这里暂时保留清除效果的逻辑
    }

    // 辅助方法：创建染色皮革护甲
    private ItemStack createColoredArmor(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }
}
