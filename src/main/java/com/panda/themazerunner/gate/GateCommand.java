package com.panda.themazerunner.gate;

import com.panda.themazerunner.manager.LanguageManager;
import com.panda.themazerunner.utils.Region;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 大门指令执行器
 * <p>
 * 处理 /gate 主命令及其所有子命令。
 * 负责解析通过命令行传递的参数，进行基础校验，并调度 GateManager 执行具体逻辑。
 * </p>
 */
public class GateCommand implements CommandExecutor, TabCompleter {

    private final GateManager gateManager;
    private final LanguageManager languageManager;

    /**
     * 构造函数
     *
     * @param gateManager     大门管理器实例
     * @param languageManager 语言管理器实例
     */
    public GateCommand(GateManager gateManager, LanguageManager languageManager) {
        this.gateManager = gateManager;
        this.languageManager = languageManager;
    }

    /**
     * 执行命令逻辑
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        // -----------------------
        // 子命令: wand
        // 获取选区工具
        // -----------------------
        if (sub.equals("wand")) {
            if (!(sender instanceof Player))
                return true;
            Player player = (Player) sender;
            ItemStack wand = new ItemStack(Material.WOODEN_AXE);
            ItemMeta meta = wand.getItemMeta();
            if (meta != null) {
                // 使用 PDC 标记防止伪造
                org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
                org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(
                        com.panda.themazerunner.TheMazeRunner.getInstance(), "gate_wand");
                pdc.set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);

                meta.displayName(languageManager.getComponent("gate.wand_name"));
                meta.lore(languageManager.getComponentList("gate.wand_lore"));
                wand.setItemMeta(meta);
            }
            player.getInventory().addItem(wand);
            player.sendMessage(languageManager.getMessage("gate.wand_given"));
            return true;
        }

        // -----------------------
        // 子命令: select
        // 确认左右门扇选区
        // -----------------------
        if (sub.equals("select")) {
            if (!(sender instanceof Player))
                return true;
            Player player = (Player) sender;
            if (args.length < 2) {
                sender.sendMessage(languageManager.getMessage("gate.usage_select"));
                return true;
            }

            String side = args[1].toLowerCase();
            boolean isLeft = side.equals("left");
            boolean isRight = side.equals("right");

            if (!isLeft && !isRight) {
                sender.sendMessage(languageManager.getMessage("gate.usage_select"));
                return true;
            }

            boolean success = gateManager.saveSelectionToBuffer(player.getUniqueId(), isLeft);
            if (success) {
                player.sendMessage(
                        languageManager.getMessage(isLeft ? "gate.select_left_saved" : "gate.select_right_saved"));
            } else {
                player.sendMessage(languageManager.getMessage("gate.create_fail_no_selection"));
            }
            return true;
        }

        // -----------------------
        // 子命令: create
        // 创建新大门
        // 用法: /gate create <名称> [滑动距离] [动画时长]
        // -----------------------
        if (sub.equals("create")) {
            if (!(sender instanceof Player))
                return true;
            Player player = (Player) sender;
            if (args.length < 2) {
                sender.sendMessage(languageManager.getMessage("gate.usage_create"));
                return true;
            }
            String name = args[1];

            // 解析可选参数
            // slideDistance: 滑动距离。默认为 -1 (自动计算)。
            double distance = -1;
            // duration: 动画及开门时长 (秒)。默认为 5 秒。
            int duration = 5;

            if (args.length >= 3) {
                try {
                    distance = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c距离参数格式错误，已使用默认值 (自动)。");
                }
            }

            if (args.length >= 4) {
                try {
                    duration = Integer.parseInt(args[3]);
                    if (duration < 1)
                        duration = 1;
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c时长参数格式错误，已使用默认值 (5秒)。");
                }
            }

            Region[] buffer = gateManager.getPlayerGateBuffer(player.getUniqueId());
            if (buffer == null || buffer[0] == null || buffer[1] == null) {
                player.sendMessage(languageManager.getMessage("gate.create_fail_no_selection"));
                return true;
            }

            Gate gate = gateManager.createGate(name, buffer[0], buffer[1], distance, duration);
            if (gate != null) {
                player.sendMessage(languageManager.getMessage("gate.create_success").replace("%name%", name));
            } else {
                player.sendMessage(languageManager.getMessage("gate.create_fail_exists").replace("%name%", name));
            }
            return true;
        }

        // -----------------------
        // 子命令: open / close
        // 控制大门开关
        // -----------------------
        if (sub.equals("open") || sub.equals("close")) {
            if (args.length < 2) {
                sender.sendMessage(languageManager.getMessage("gate.usage_control"));
                return true;
            }
            String name = args[1];
            Gate gate = gateManager.getGate(name);

            if (gate == null) {
                sender.sendMessage(languageManager.getMessage("gate.not_found").replace("%name%", name));
                return true;
            }

            if (sub.equals("open")) {
                if (gateManager.openGate(name)) {
                    sender.sendMessage(languageManager.getMessage("gate.open_success").replace("%name%", name));
                } else {
                    sender.sendMessage(languageManager.getMessage("gate.open_fail"));
                }
            } else {
                if (gateManager.closeGate(name)) {
                    sender.sendMessage(languageManager.getMessage("gate.close_success").replace("%name%", name));
                } else {
                    sender.sendMessage(languageManager.getMessage("gate.close_fail"));
                }
            }
            return true;
        }

        // -----------------------
        // 子命令: edit
        // 编辑大门属性
        // 用法: /gate edit <name> <property> <value...>
        // -----------------------
        if (sub.equals("edit")) {
            handleEdit(sender, args);
            return true;
        }

        // 默认显示帮助
        sendUsage(sender);
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(languageManager.getMessage("gate.cmd_usage"));
        sender.sendMessage("§7/gate edit <name> <property> <value> - 编辑大门属性");
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法: /gate edit <大门名> <属性> <值...>");
            sender.sendMessage("§7属性: axis, safemode, movemode, viewrange, cull, keepopen, shellonly");
            return;
        }

        String name = args[1];
        String prop = args[2].toLowerCase();
        Gate gate = gateManager.getGate(name);

        if (gate == null) {
            sender.sendMessage(languageManager.getMessage("gate.not_found").replace("%name%", name));
            return;
        }

        try {
            switch (prop) {
                case "axis" -> {
                    Gate.Axis axis = Gate.Axis.valueOf(args[3].toUpperCase());
                    gate.setAxis(axis);
                    sender.sendMessage("§a大门轴向已设置为: " + axis);
                }
                case "safemode" -> {
                    Gate.SafeMode mode = Gate.SafeMode.valueOf(args[3].toUpperCase());
                    gate.setSafeMode(mode);
                    sender.sendMessage("§a安全模式已设置为: " + mode);
                }
                case "movemode" -> {
                    Gate.MoveMode mode = Gate.MoveMode.valueOf(args[3].toUpperCase());
                    gate.setMoveMode(mode);
                    sender.sendMessage("§a移动模式已设置为: " + mode);

                    if (mode == Gate.MoveMode.TELEPORT && gate.getDurationTicks() > 59) {
                        sender.sendMessage(
                                "§6[警告] 当前动画时长 (" + gate.getDurationTicks() + " ticks) 超过 TELEPORT 模式限制 (59 ticks)。");
                        sender.sendMessage("§6实际动画将被限制为 59 ticks，但逻辑时长保持不变。");
                    }
                }
                case "viewrange" -> {
                    float val = Float.parseFloat(args[3]);
                    gate.setViewRange(val);
                    sender.sendMessage("§a视距已设置为: " + val);
                }
                case "cull" -> {
                    if (args.length < 5) {
                        sender.sendMessage("§c需要提供宽高: /gate edit <name> cull <width> <height>");
                        return;
                    }
                    float w = Float.parseFloat(args[3]);
                    float h = Float.parseFloat(args[4]);
                    gate.setCulling(w, h);
                    sender.sendMessage("§a裁剪框已设置为: " + w + "x" + h);
                }
                case "keepopen" -> {
                    boolean val = Boolean.parseBoolean(args[3]);
                    gate.setKeepOpenDisplays(val);
                    sender.sendMessage("§a保持常驻已设置为: " + val);
                }
                case "shellonly" -> {
                    boolean val = Boolean.parseBoolean(args[3]);
                    gate.setShellOnly(val);
                    sender.sendMessage("§a仅渲染外壳已设置为: " + val);
                }
                case "delay" -> {
                    int val = Integer.parseInt(args[3]);
                    if (val < 0)
                        val = 0;
                    gate.setAnimationDelay(val);
                    sender.sendMessage("§a动画预热延迟已设置为: " + val + " ticks");
                }
                default -> sender.sendMessage("§c未知属性: " + prop);
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c参数无效: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(args[0], Arrays.asList("wand", "select", "create", "open", "close", "edit"));
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("create")) {
            // /gate create <name> [dist] [time]
            if (args.length == 3) {
                return filterStartsWith(args[2], Arrays.asList("-1", "5", "10", "15")); // 推荐距离
            }
            if (args.length == 4) {
                return filterStartsWith(args[3], Arrays.asList("3", "5", "10")); // 推荐时长
            }
        } else if (sub.equals("select")) {
            if (args.length == 2) {
                return filterStartsWith(args[1], Arrays.asList("left", "right"));
            }
        } else if (sub.equals("open") || sub.equals("close")) {
            if (args.length == 2) {
                // 返回大门名称
                return filterStartsWith(args[1], getGateNames());
            }
        } else if (sub.equals("edit")) {
            if (args.length == 2) {
                return filterStartsWith(args[1], getGateNames());
            }
            if (args.length == 3) {
                return filterStartsWith(args[2],
                        Arrays.asList("axis", "safemode", "movemode", "viewrange", "cull", "keepopen", "shellonly",
                                "delay"));
            }
            if (args.length == 4) {
                String prop = args[2].toLowerCase();
                return switch (prop) {
                    case "axis" -> filterStartsWith(args[3],
                            Arrays.stream(Gate.Axis.values()).map(Enum::name).collect(Collectors.toList()));
                    case "safemode" -> filterStartsWith(args[3],
                            Arrays.stream(Gate.SafeMode.values()).map(Enum::name).collect(Collectors.toList()));
                    case "movemode" -> filterStartsWith(args[3],
                            Arrays.stream(Gate.MoveMode.values()).map(Enum::name).collect(Collectors.toList()));
                    case "keepopen", "shellonly" -> filterStartsWith(args[3], Arrays.asList("true", "false"));
                    case "delay" -> filterStartsWith(args[3], Arrays.asList("3", "5", "10"));
                    default -> Collections.emptyList();
                };
            }
        }

        return Collections.emptyList();
    }

    private List<String> getGateNames() {
        return new ArrayList<>(gateManager.getGateNames());
    }

    private List<String> filterStartsWith(String text, List<String> list) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(text.toLowerCase()))
                .collect(Collectors.toList());
    }
}
