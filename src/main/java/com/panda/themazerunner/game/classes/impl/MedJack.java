package com.panda.themazerunner.game.classes.impl;

import com.panda.themazerunner.game.classes.GameClass;
import com.panda.themazerunner.manager.LanguageManager;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/**
 * 医疗兵 (The Med-jack) 职业实现
 * <p>
 * 特点: 自愈能力，提供医疗物资。
 * 职责: 治疗伤员。
 */
public class MedJack extends GameClass {

    public MedJack(LanguageManager languageManager) {
        super(languageManager, "medjack");
    }

    @Override
    public void onEquip(Player player) {
        // 给予生命恢复 I
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, false, false));

        // --- 物品 ---
        // 喷溅型治疗药水 (II)
        ItemStack splashPotion = new ItemStack(Material.SPLASH_POTION, 2);
        PotionMeta potionMeta = (PotionMeta) splashPotion.getItemMeta();
        if (potionMeta != null) {
            potionMeta.setBasePotionType(PotionType.STRONG_HEALING);
            splashPotion.setItemMeta(potionMeta);
        }
        player.getInventory().addItem(splashPotion);

        // 牛奶桶
        player.getInventory().addItem(new ItemStack(Material.MILK_BUCKET));
        // 蜂蜜瓶
        player.getInventory().addItem(new ItemStack(Material.HONEY_BOTTLE));

        // 织网药水 (Potion of Weaving) - 1.21
        ItemStack weavingPotion = new ItemStack(Material.SPLASH_POTION); // 投掷型方便使用
        PotionMeta weavingMeta = (PotionMeta) weavingPotion.getItemMeta();
        if (weavingMeta != null) {
            // 1.21 API: PotionType.WEAVING
            try {
                weavingMeta.setBasePotionType(PotionType.valueOf("WEAVING"));
            } catch (IllegalArgumentException e) {
                // Fallback or log warning if running on older version/API mismatch despite 1.21
                // target
                // 也可以尝试直接给予 potion effect 如果没有对应 PotionType (虽然药水物品通常需要 type)
            }
            weavingPotion.setItemMeta(weavingMeta);
        }
        player.getInventory().addItem(weavingPotion);

        // --- 武器 ---
        // 木剑
        player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));

        // 弩 (装填 治疗之箭)
        ItemStack crossbow = new ItemStack(Material.CROSSBOW);
        // 这里只是给一把弩和治疗箭，玩家需要自己装填，或者通过 NMS/API 预装填 (API 较繁琐，通常给箭即可)
        // 为了简便，给弩和治疗箭矢
        player.getInventory().addItem(crossbow);

        ItemStack tippedArrow = new ItemStack(Material.TIPPED_ARROW, 64);
        PotionMeta arrowMeta = (PotionMeta) tippedArrow.getItemMeta();
        if (arrowMeta != null) {
            arrowMeta.setBasePotionType(PotionType.HEALING);
            tippedArrow.setItemMeta(arrowMeta);
        }
        player.getInventory().addItem(tippedArrow);

        // --- 护甲 ---
        // 金头盔
        ItemStack helmet = new ItemStack(Material.GOLDEN_HELMET);

        // 白色皮革护甲
        ItemStack chestplate = createColoredArmor(Material.LEATHER_CHESTPLATE, Color.WHITE);
        ItemStack leggings = createColoredArmor(Material.LEATHER_LEGGINGS, Color.WHITE);
        ItemStack boots = createColoredArmor(Material.LEATHER_BOOTS, Color.WHITE);

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);

        player.sendMessage(languageManager.getMessage("errors.class_equipped").replace("%class%", getName()));
    }

    @Override
    public void onUnequip(Player player) {
        player.removePotionEffect(PotionEffectType.REGENERATION);
        // 通常不移除物品，除非是临时物品
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
