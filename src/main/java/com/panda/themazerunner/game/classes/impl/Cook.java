package com.panda.themazerunner.game.classes.impl;

import com.panda.themazerunner.game.classes.GameClass;
import com.panda.themazerunner.manager.LanguageManager;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 农夫/厨师 (The Cook) 职业实现
 * <p>
 * 定位: 后勤 / 资源 / 增益
 * 特点: 提供高级食物和增益，控制不祥之瓶。
 */
public class Cook extends GameClass {

    public Cook(LanguageManager languageManager) {
        super(languageManager, "cook");
    }

    @Override
    public void onEquip(Player player) {
        // --- 武器 ---
        // 锄头 (附魔 击退 I)
        ItemStack hoe = new ItemStack(Material.IRON_HOE); // 或木锄，原著里可能有差异，这里给铁锄实用点，或者按要求配
        hoe.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
        player.getInventory().addItem(hoe);

        // --- 物品 (食物) ---
        // 牛排
        player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 16));
        // 金胡萝卜
        player.getInventory().addItem(new ItemStack(Material.GOLDEN_CARROT, 8));
        // 附魔金苹果 (极稀有)
        // Rare resources must be earned, not granted on equip.

        // 迷之炖菜 (生命恢复)
        player.getInventory().addItem(createSuspiciousStew(PotionEffectType.REGENERATION, 100)); // 5秒
        // 迷之炖菜 (力量)
        player.getInventory().addItem(createSuspiciousStew(PotionEffectType.STRENGTH, 100));

        // 不祥之瓶 (Ominous Bottle) - 1.21
        // 注意: 1.21 API 中，OMINOUS_BOTTLE 是一个物品，通常有等级
        try {
            // 尝试获取不祥之瓶，假设最大等级是 V (5)
            // 需要检查具体 API 实现，OMINOUS_BOTTLE 可能需要通过 OminousBottleMeta 设置 amplifier
            // 这里简单给予默认物品，如果 API 支持 ItemType
            ItemStack ominousBottle = new ItemStack(Material.valueOf("OMINOUS_BOTTLE"));
            // 如果需要设置等级，可能需要 OminousBottleMeta (Paper 1.21 API)
            // 为了安全起见，先给默认的
            player.getInventory().addItem(ominousBottle);
        } catch (IllegalArgumentException e) {
            // Fallback for older versions
            player.getInventory().addItem(new ItemStack(Material.EXPERIENCE_BOTTLE)); // 替代品
        }

        // --- 护甲 ---
        // 只有裤子和鞋子 (无头盔和胸甲)
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));

        player.sendMessage(languageManager.getMessage("errors.class_equipped").replace("%class%", getName()));
    }

    @Override
    public void onUnequip(Player player) {
        // 无特殊持续效果需要清除，物品清除逻辑由外部处理或统一处理
    }

    // 辅助方法：创建迷之炖菜
    private ItemStack createSuspiciousStew(PotionEffectType type, int duration) {
        ItemStack stew = new ItemStack(Material.SUSPICIOUS_STEW);
        SuspiciousStewMeta meta = (SuspiciousStewMeta) stew.getItemMeta();
        if (meta != null) {
            // Suppress deprecation for legacy support or until SuspiciousEffectEntry is
            // fully adopted
            @SuppressWarnings("deprecation")
            boolean success = meta.addCustomEffect(new PotionEffect(type, duration, 0), true);
            stew.setItemMeta(meta);
        }
        return stew;
    }
}
