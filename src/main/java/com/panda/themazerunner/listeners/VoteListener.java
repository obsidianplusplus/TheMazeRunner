package com.panda.themazerunner.listeners;

import com.panda.themazerunner.TheMazeRunner;
import com.panda.themazerunner.manager.VoteManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;

/**
 * 投票监听器
 * <p>
 * 负责处理玩家与投票相关的交互，包括使用投票券和在投票界面中的点击操作。
 */
public class VoteListener implements Listener {

    private final TheMazeRunner plugin;

    /**
     * 构造函数
     *
     * @param plugin 插件实例
     */
    public VoteListener(TheMazeRunner plugin) {
        this.plugin = plugin;
    }

    /**
     * 处理玩家交互事件
     * <p>
     * 当玩家右键点击投票券时，打开投票界面。
     *
     * @param event 玩家交互事件
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getItem().getType() != Material.PAPER)
            return;
        if (!event.getItem().hasItemMeta())
            return;

        Component displayName = event.getItem().getItemMeta().displayName();
        if (displayName == null)
            return;

        String plainName = PlainTextComponentSerializer.plainText().serialize(displayName);
        String configName = PlainTextComponentSerializer.plainText()
                .serialize(plugin.getLanguageManager().getComponent("vote.ticket.name"));

        if (plainName.contains(configName)) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                plugin.getVoteManager().openVoteGui(event.getPlayer());
                event.setCancelled(true);
            }
        }
    }

    /**
     * 处理 GUI 点击事件
     * <p>
     * 处理投票界面中的点击操作，包括投票、弃权和审判界面的选择。
     *
     * @param event GUI 点击事件
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Component title = event.getView().title();
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(title);

        String voteTitle = PlainTextComponentSerializer.plainText()
                .serialize(plugin.getLanguageManager().getComponent("vote.gui_title"));
        String judgmentTitle = PlainTextComponentSerializer.plainText()
                .serialize(plugin.getLanguageManager().getComponent("judgment.gui_title"))
                .replace("%player%", "").trim();

        if (plainTitle.contains(voteTitle)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null)
                return;

            Player voter = (Player) event.getWhoClicked();
            VoteManager vm = plugin.getVoteManager();

            if (event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) event.getCurrentItem().getItemMeta();
                if (meta.getOwningPlayer() != null) {
                    vm.castVote(voter, meta.getOwningPlayer().getUniqueId());
                }
            } else if (event.getCurrentItem().getType() == Material.BARRIER) {
                vm.castVote(voter, null);
            }
        } else if (plainTitle.contains(judgmentTitle)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null)
                return;

            Player voter = (Player) event.getWhoClicked();
            VoteManager vm = plugin.getVoteManager();

            boolean isExile = false;
            boolean validClick = false;

            if (event.getCurrentItem().getType() == Material.LIME_WOOL) {
                isExile = false;
                validClick = true;
            } else if (event.getCurrentItem().getType() == Material.RED_WOOL) {
                isExile = true;
                validClick = true;
            } else if (event.getCurrentItem().getType() == Material.OMINOUS_BOTTLE) {
                if (event.getSlot() == 11)
                    isExile = false;
                else if (event.getSlot() == 15)
                    isExile = true;
                else
                    return;
                validClick = true;
            }

            if (validClick) {
                if (vm.wouldCauseTie(isExile) && !vm.hasWarnedTremblingHand(voter)) {
                    vm.setWarnedTremblingHand(voter, isExile);

                    voter.playSound(voter.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f);
                    voter.playSound(voter.getLocation(), org.bukkit.Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 0.5f);
                    voter.showTitle(Title.title(
                            plugin.getLanguageManager().getComponent("vote.trembling.title"),
                            plugin.getLanguageManager().getComponent("vote.trembling.subtitle"),
                            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500),
                                    Duration.ofMillis(1000))));

                    ItemStack ominous = new ItemStack(Material.OMINOUS_BOTTLE);
                    ItemMeta meta = ominous.getItemMeta();
                    meta.displayName(plugin.getLanguageManager().getComponent("vote.trembling.item_name")
                            .decoration(TextDecoration.BOLD, true));

                    Enchantment glowEnchant = Enchantment.UNBREAKING;
                    meta.addEnchant(glowEnchant, 1, true);

                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    ominous.setItemMeta(meta);

                    event.setCurrentItem(ominous);
                    return;
                }

                vm.castJudgment(voter, isExile);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;

        Player player = (Player) event.getPlayer();
        VoteManager vm = plugin.getVoteManager();

        // 只有在审判进行中才需要限制
        if (!vm.isTrialInProgress() || vm.getCurrentAccused() == null) {
            return;
        }

        Component title = event.getView().title();
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(title);
        String judgmentTitlePart = PlainTextComponentSerializer.plainText()
                .serialize(plugin.getLanguageManager().getComponent("judgment.gui_title"))
                .replace("%player%", "").trim();

        // 检查是否是审判界面
        if (plainTitle.contains(judgmentTitlePart)) {
            // 如果玩家还没有投票，强制重新打开
            if (!vm.hasVotedInJudgment(player)) {
                // 需要延迟一点打开，否则可能会被本次关闭事件覆盖
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    // 再次检查是否还在审判中，防止审判刚好结束
                    if (vm.isTrialInProgress() && vm.getCurrentAccused() != null) {
                        vm.openJudgmentGui(player);
                    }
                });
            }
        }
    }
}
