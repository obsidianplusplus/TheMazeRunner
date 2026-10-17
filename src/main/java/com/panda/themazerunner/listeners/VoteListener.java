package com.panda.themazerunner.listeners;

import com.panda.themazerunner.TheMazeRunner;
import com.panda.themazerunner.manager.VoteManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.SkullMeta;

/** Server-owned inventory identity, never title or client item names. */
public final class VoteListener implements Listener {
    private final TheMazeRunner plugin;
    public VoteListener(TheMazeRunner plugin) { this.plugin=plugin; }
    @EventHandler(ignoreCancelled=true) public void interact(PlayerInteractEvent e) {
        if (e.getHand()==EquipmentSlot.HAND && e.getAction().isRightClick() && plugin.getVoteManager().isTicket(e.getItem())) {
            e.setCancelled(true); plugin.getVoteManager().openVoteGui(e.getPlayer());
        }
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof VoteManager.VoteMenu menu)) return;
        e.setCancelled(true);
        VoteManager vm=plugin.getVoteManager();
        if (!vm.validMenu(menu) || !(e.getWhoClicked() instanceof Player p) || e.getRawSlot()<0
                || e.getRawSlot()>=e.getView().getTopInventory().getSize() || e.getCurrentItem()==null) return;
        var item=e.getCurrentItem();
        if (menu.trial) {
            if (e.getRawSlot()==11) vm.castJudgment(p,false);
            if (e.getRawSlot()==15) vm.castJudgment(p,true);
        } else if (item.getType()==Material.PLAYER_HEAD && item.getItemMeta() instanceof SkullMeta skull && skull.getOwningPlayer()!=null) {
            vm.castVote(p,skull.getOwningPlayer().getUniqueId());
        } else if (e.getRawSlot()==49) vm.castVote(p,null);
    }
    @EventHandler public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof VoteManager.VoteMenu) e.setCancelled(true);
    }
    @EventHandler public void quit(PlayerQuitEvent e) { plugin.getVoteManager().removeParticipant(e.getPlayer()); plugin.getAreaManager().removeParticipant(e.getPlayer()); }
}
