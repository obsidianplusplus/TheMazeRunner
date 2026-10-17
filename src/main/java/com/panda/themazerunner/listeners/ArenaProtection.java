package com.panda.themazerunner.listeners;

import com.panda.themazerunner.game.match.MatchManager;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.*;

/** Central arena boundary; existing class listeners cannot grant world-edit permissions. */
public final class ArenaProtection implements Listener {
    private final MatchManager match;

    public ArenaProtection(MatchManager match) {
        this.match = match;
    }

    private boolean locked(Player p, Location location) {
        return match.contains(p) || match.protectedWorld(location.getWorld()) && !match.canEdit(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (locked(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) {
        if (locked(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void bucket(PlayerBucketEmptyEvent e) {
        if (locked(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void fill(PlayerBucketFillEvent e) {
        if (locked(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void ignite(BlockIgniteEvent e) {
        if (match.protectedWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void burn(BlockBurnEvent e) {
        if (match.protectedWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void spread(BlockSpreadEvent e) {
        if (match.protectedWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void form(BlockFormEvent e) {
        if (match.protectedWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void fade(BlockFadeEvent e) {
        if (match.protectedWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void flow(BlockFromToEvent e) {
        if (match.protectedWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) {
        if (match.protectedWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void retract(BlockPistonRetractEvent e) {
        if (match.protectedWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void explode(EntityExplodeEvent e) {
        if (match.protectedWorld(e.getLocation().getWorld())) {
            e.blockList().clear();
            e.setYield(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void blockExplosion(BlockExplodeEvent e) {
        if (match.protectedWorld(e.getBlock().getWorld())) {
            e.blockList().clear();
            e.setYield(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void mobChange(EntityChangeBlockEvent e) {
        if (match.protectedWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void hanging(HangingBreakEvent e) {
        if (match.protectedWorld(e.getEntity().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void manipulate(PlayerArmorStandManipulateEvent e) {
        if (locked(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void interactEntity(PlayerInteractEntityEvent e) {
        if (locked(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void portal(PlayerPortalEvent e) {
        if (match.contains(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent e) {
        if (match.allowedTeleport() || e.getTo() == null) return;
        if (match.contains(e.getPlayer())
                || match.busy()
                        && match.protectedWorld(e.getTo().getWorld())
                        && !match.contains(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent e) {
        if (!match.contains(e.getPlayer())) return;
        String command = e.getMessage().split(" ")[0].toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("/maze", "/themazerunner:maze", "/class", "/themazerunner:class")
                .contains(command)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("对局内请使用 /maze 菜单或 /maze leave 退出后执行其他命令。");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void move(PlayerMoveEvent e) {
        if (!match.contains(e.getPlayer()) || e.getTo() == null) return;
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        if (!match.insideArena(e.getTo())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void open(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p
                && match.contains(p)
                && !match.ownsMenu(e.getInventory())) e.setCancelled(true);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !match.contains(p)) return;
        if (e.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            if (e.getRawSlot() >= 0 && e.getRawSlot() <= 4
                    || e.getAction() == InventoryAction.COLLECT_TO_CURSOR
                    || e.getClick() == ClickType.DOUBLE_CLICK) e.setCancelled(true);
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && match.contains(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void drop(PlayerDropItemEvent e) {
        if (match.contains(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent e) {
        if (match.protectedWorld(e.getEntity().getWorld())
                || e.getEntity() instanceof Player p && match.contains(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void arrowPickup(PlayerPickupArrowEvent e) {
        if (match.protectedWorld(e.getPlayer().getWorld()) || match.contains(e.getPlayer()))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void decorationDamage(EntityDamageByEntityEvent e) {
        if (match.protectedWorld(e.getEntity().getWorld())
                && !(e.getEntity() instanceof Player)
                && !match.owns(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void itemSpawn(ItemSpawnEvent e) {
        if (match.protectedWorld(e.getLocation().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent e) {
        if (match.contains(e.getPlayer()) && !match.active(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void mount(org.bukkit.event.entity.EntityMountEvent e) {
        if (e.getEntity() instanceof Player p && match.contains(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void splash(PotionSplashEvent e) {
        if (!match.protectedWorld(e.getEntity().getWorld())) return;
        for (LivingEntity target : e.getAffectedEntities())
            if (!(target instanceof Player p) || !match.active(p)) e.setIntensity(target, 0);
    }

    @EventHandler(ignoreCancelled = true)
    public void linger(LingeringPotionSplashEvent e) {
        if (match.protectedWorld(e.getEntity().getWorld())) e.setCancelled(true);
    }
}
