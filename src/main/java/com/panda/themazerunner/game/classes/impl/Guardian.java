package com.panda.themazerunner.game.classes.impl;

import com.panda.themazerunner.game.classes.GameClass;
import com.panda.themazerunner.manager.LanguageManager;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 守护者 (The Guardian) 职业实现
 * <p>
 * 特点: 高防御，生命值加成。
 * 职责: 保护行者，抵御鬼火兽。
 */
public class Guardian extends GameClass {

    public Guardian(LanguageManager languageManager) {
        super(languageManager, "guardian");
    }

    @Override
    public void onEquip(Player player) {
        // 给予抗性提升 I
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));

        // --- 武器 & 工具 ---
        // 重锤 (Mace)
        player.getInventory().addItem(new ItemStack(Material.MACE));
        // 盾牌
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        // 拴绳
        player.getInventory().addItem(new ItemStack(Material.LEAD));

        // --- 护甲 ---
        // 铁套 (附魔 保护 I)
        ItemStack helmet = new ItemStack(Material.IRON_HELMET);
        helmet.addEnchantment(Enchantment.PROTECTION, 1);

        ItemStack chestplate = new ItemStack(Material.IRON_CHESTPLATE);
        chestplate.addEnchantment(Enchantment.PROTECTION, 1);

        ItemStack leggings = new ItemStack(Material.IRON_LEGGINGS);
        leggings.addEnchantment(Enchantment.PROTECTION, 1);

        ItemStack boots = new ItemStack(Material.IRON_BOOTS);
        boots.addEnchantment(Enchantment.PROTECTION, 1);

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);

        player.sendMessage(languageManager.getMessage("errors.class_equipped").replace("%class%", getName()));
    }

    @Override
    public void onUnequip(Player player) {
        player.removePotionEffect(PotionEffectType.RESISTANCE);
    }
}
