package com.panda.themazerunner.gate;

import com.panda.themazerunner.utils.Region;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 大门管理器
 * <p>
 * 管理所有的大门实例以及玩家的选区操作。
 */
public class GateManager {

    private final JavaPlugin plugin;
    private final Map<String, Gate> gates;

    // 玩家选区缓存: UUID -> [Pos1, Pos2]
    private final Map<UUID, Location[]> playerSelections;

    // 玩家预备大门数据: UUID -> [LeftRegion, RightRegion]
    // 数组索引 0 = Left, 1 = Right
    private final Map<UUID, Region[]> playerGateBuffers;

    public GateManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.gates = new HashMap<>();
        this.playerSelections = new HashMap<>();
        this.playerGateBuffers = new HashMap<>();
        loadGates();
    }

    /**
     * 加载所有大门数据
     */
    public void loadGates() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "gates.yml");
        if (!file.exists())
            return;

        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(file);
        if (!config.contains("gates"))
            return;

        for (String key : config.getConfigurationSection("gates").getKeys(false)) {
            try {
                Map<String, Object> data = config.getConfigurationSection("gates." + key).getValues(false);
                Gate gate = Gate.deserialize(plugin, data);
                if (gate != null) {
                    gates.put(gate.getName().toLowerCase(java.util.Locale.ROOT), gate);
                    plugin.getLogger().info("已加载大门: " + gate.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("加载大门 " + key + " 失败: " + e.getMessage());
            }
        }
    }

    /**
     * 保存所有大门数据
     */
    public void saveGates() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "gates.yml");
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();

        for (Gate gate : gates.values()) {
            config.createSection("gates." + gate.getName(), gate.serialize());
        }

        try {
            config.save(file);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("保存大门数据失败: " + e.getMessage());
        }
    }

    /**
     * 创建大门
     *
     * @param name            大门名称
     * @param left            左侧区域
     * @param right           右侧区域
     * @param slideDistance   滑动距离 (<=0 则自动计算)
     * @param durationSeconds 动画时长 (秒)
     * @return 创建的大门
     */
    public Gate createGate(String name, Region left, Region right, double slideDistance, int durationSeconds) {
        String lowerName = name.toLowerCase(java.util.Locale.ROOT);
        if (gates.containsKey(lowerName)) {
            return null;
        }
        Gate gate = new Gate(plugin, lowerName, left, right, slideDistance, durationSeconds);
        gates.put(lowerName, gate);
        return gate;
    }

    /**
     * 获取大门
     *
     * @param name 大门名称
     * @return 大门实例
     */
    public Gate getGate(String name) {
        return gates.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * 移除大门
     *
     * @param name 大门名称
     */
    public void removeGate(String name) {
        Gate gate = gates.remove(name.toLowerCase(java.util.Locale.ROOT));
        if (gate != null) {
            // 确保清理并恢复方块，防止留下空气洞
            gate.cleanupDisplays();
            gate.restoreBlocks();
            gate.setState(Gate.State.CLOSED);
        }
    }

    /**
     * 关闭所有大门并清理资源 (用于插件卸载)
     */
    public void shutdown() {
        // 保存所有大门数据
        saveGates();

        for (Gate gate : gates.values()) {
            try {
                // 强制清理实体并恢复方块
                gate.cleanupDisplays();
                gate.restoreBlocks();
                gate.setState(Gate.State.CLOSED);
            } catch (Exception e) {
                plugin.getLogger().warning("错误的关闭了大门" + gate.getName() + ": " + e.getMessage());
            }
        }
        gates.clear();
    }

    /**
     * 设置玩家的选点
     *
     * @param uuid  玩家UUID
     * @param index 点的索引 (0或1)
     * @param loc   选点位置
     */
    public void setPlayerSelection(UUID uuid, int index, Location loc) {
        playerSelections.putIfAbsent(uuid, new Location[2]);
        playerSelections.get(uuid)[index] = loc;
    }

    /**
     * 获取玩家的选点
     *
     * @param uuid 玩家UUID
     * @return 选点数组 [Pos1, Pos2]
     */
    public Location[] getPlayerSelection(UUID uuid) {
        return playerSelections.get(uuid);
    }

    /**
     * 保存当前选区为大门的某一部分（左或右）
     *
     * @param uuid   玩家UUID
     * @param isLeft 是否为左侧
     * @return 是否成功
     */
    public boolean saveSelectionToBuffer(UUID uuid, boolean isLeft) {
        Location[] locs = playerSelections.get(uuid);
        if (locs == null || locs[0] == null || locs[1] == null) {
            return false;
        }
        // 检查世界一致性
        if (!locs[0].getWorld().equals(locs[1].getWorld())) {
            return false;
        }

        Region region = new Region(locs[0], locs[1]);

        playerGateBuffers.putIfAbsent(uuid, new Region[2]);
        playerGateBuffers.get(uuid)[isLeft ? 0 : 1] = region;

        return true;
    }

    /**
     * 获取玩家缓冲区的区域 (用于创建大门)
     *
     * @param uuid 玩家UUID
     * @return Region数组 [Left, Right]
     */
    public Region[] getPlayerGateBuffer(UUID uuid) {
        return playerGateBuffers.get(uuid);
    }

    /**
     * 清理玩家数据 (用于退服清理)
     * 
     * @param uuid 玩家UUID
     */
    public void clearPlayerData(UUID uuid) {
        playerSelections.remove(uuid);
        playerGateBuffers.remove(uuid);
    }

    /**
     * 获取所有大门名称
     *
     * @return 大门名称集合
     */
    public java.util.Collection<String> getGateNames() {
        return gates.keySet();
    }

    /**
     * 关闭大门
     * 
     * @param name 大门名称
     * @return 如果成功触发关闭则返回 true
     */
    public boolean closeGate(String name) {
        Gate gate = getGate(name);
        // 只允许在完全开启状态下关闭
        if (gate != null && gate.getState() == Gate.State.OPEN) {
            gate.close();
            return true;
        }
        return false;
    }

    /**
     * 开启大门
     *
     * @param name 大门名称
     * @return 如果成功触发开启则返回 true
     */
    public boolean openGate(String name) {
        Gate gate = getGate(name);
        // 只允许在完全关闭状态下开启
        if (gate != null && gate.getState() == Gate.State.CLOSED) {
            gate.open();
            return true;
        }
        return false;
    }
}
