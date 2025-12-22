package com.panda.themazerunner.game.area;

import com.panda.themazerunner.manager.LanguageManager;
import com.panda.themazerunner.utils.Region;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 区域命令处理器
 * <p>
 * 处理 /area 命令。
 */
public class AreaCommand implements CommandExecutor, TabCompleter {

    private final AreaManager areaManager;
    private final LanguageManager languageManager;

    public AreaCommand(AreaManager areaManager, LanguageManager languageManager) {
        this.areaManager = areaManager;
        this.languageManager = languageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(languageManager.getMessage("area.only_players"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        Player player = (Player) sender;
        String sub = args[0].toLowerCase();

        // 1. 获取工具 wand
        if (sub.equals("wand")) {
            giveWand(player);
            return true;
        }

        // 2. 选点 select (供 wand 使用或手动)
        if (sub.equals("select")) {
            // 这个命令主要由 wand 触发，但也可以手动
            // 用法: /area select <pos1|pos2>
            // 这里为了简化 wand 逻辑，wand 交互事件可以直接调用 setPlayerSelection
            // 但如果通过命令调用：
            sender.sendMessage(languageManager.getMessage("area.wand_select_hint"));
            return true;
        }

        // 3. 设置林间空地 setglade
        if (sub.equals("setglade")) {
            Region region = areaManager.getPlayerSelectionRegion(player.getUniqueId());
            if (region == null) {
                player.sendMessage(languageManager.getMessage("area.no_selection"));
                return true;
            }
            areaManager.setGladeRegion(region);
            player.sendMessage(languageManager.getMessage("area.glade_set"));
            return true;
        }

        // 4. 设置余烬篝火 setbonfire (看过去的方块)
        if (sub.equals("setbonfire")) {
            Block target = player.getTargetBlockExact(10);
            if (target == null) {
                player.sendMessage(languageManager.getMessage("area.target_block_hint"));
                return true;
            }
            areaManager.setBonfireLocation(target.getLocation());
            player.sendMessage(languageManager.getMessage("area.bonfire_set"));
            return true;
        }

        // 5. 设置篝火区域 setbonfirearea
        if (sub.equals("setbonfirearea")) {
            Region region = areaManager.getPlayerSelectionRegion(player.getUniqueId());
            if (region == null) {
                player.sendMessage(languageManager.getMessage("area.no_selection"));
                return true;
            }
            areaManager.setBonfireRegion(region);
            player.sendMessage(languageManager.getMessage("area.bonfire_area_set"));
            return true;
        }

        // 6. 设置集结钟 setbell
        if (sub.equals("setbell")) {
            Block target = player.getTargetBlockExact(10);
            if (target == null) {
                player.sendMessage(languageManager.getMessage("area.target_block_hint"));
                return true;
            }
            areaManager.setGatheringBellLocation(target.getLocation());
            player.sendMessage(languageManager.getMessage("area.bell_set"));
            return true;
        }

        // 7. 开启/结束会议
        if (sub.equals("startmeeting")) {
            areaManager.startMeeting();
            player.sendMessage(languageManager.getMessage("area.meeting_start"));
            return true;
        }

        if (sub.equals("stopmeeting")) {
            areaManager.stopMeeting();
            player.sendMessage(languageManager.getMessage("area.meeting_stop"));
            return true;
        }

        // 8. 切换流浪者进入权限 togglewanderer
        if (sub.equals("togglewanderer")) {
            boolean current = areaManager.isWandererAccessAllowed();
            areaManager.setWandererAccessAllowed(!current);
            boolean nowAllowed = areaManager.isWandererAccessAllowed();

            if (nowAllowed) {
                player.sendMessage(languageManager.getMessage("area.wanderer_access_enabled"));
            } else {
                player.sendMessage(languageManager.getMessage("area.wanderer_access_disabled"));
            }
            return true;
        }

        sendUsage(sender);
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(languageManager.getMessage("area.usage"));
    }

    private void giveWand(Player player) {
        ItemStack wand = new ItemStack(Material.WOODEN_HOE);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(com.panda.themazerunner.TheMazeRunner.getInstance(), "area_wand"),
                    PersistentDataType.BYTE, (byte) 1);
            meta.displayName(languageManager.getComponent("area.wand_name"));
            meta.lore(languageManager.getComponentList("area.wand_lore"));
            wand.setItemMeta(meta);
        }
        player.getInventory().addItem(wand);
        player.sendMessage(languageManager.getMessage("area.wand_given"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(args[0],
                    Arrays.asList("wand", "setglade", "setbonfire", "setbonfirearea", "setbell", "startmeeting",
                            "stopmeeting", "togglewanderer"));
        }
        return Collections.emptyList();
    }

    private List<String> filterStartsWith(String text, List<String> list) {
        return list.stream().filter(s -> s.startsWith(text.toLowerCase())).collect(Collectors.toList());
    }
}
