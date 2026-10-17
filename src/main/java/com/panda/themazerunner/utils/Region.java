package com.panda.themazerunner.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 区域工具类
 * <p>
 * 表示一个长方体区域，用于定义大门的形状。
 */
public class Region implements Iterable<Block> {
    private final World world;
    private final Vector min;
    private final Vector max;

    /**
     * 构造一个新的区域
     *
     * @param loc1 第一个点
     * @param loc2 第二个点
     */
    public Region(Location loc1, Location loc2) {
        if (!loc1.getWorld().equals(loc2.getWorld())) {
            throw new IllegalArgumentException("Locations must be in the same world");
        }
        this.world = loc1.getWorld();
        this.min = Vector.getMinimum(loc1.toVector(), loc2.toVector());
        this.max = Vector.getMaximum(loc1.toVector(), loc2.toVector());
        for(double value:new double[]{min.getX(),min.getY(),min.getZ(),max.getX(),max.getY(),max.getZ()})
            if(!Double.isFinite(value) || Math.abs(value)>30000000)throw new IllegalArgumentException("Region coordinates are invalid");
        double volume=(max.getBlockX()-(double)min.getBlockX()+1)*(max.getBlockY()-(double)min.getBlockY()+1)*(max.getBlockZ()-(double)min.getBlockZ()+1);
        if(volume>65536)throw new IllegalArgumentException("Region exceeds the 65536 block operation budget");
    }

    /**
     * 获取世界
     *
     * @return 世界实例
     */
    public World getWorld() {
        return world;
    }

    /**
     * 获取最小点向量
     *
     * @return 最小点
     */
    public Vector getMin() {
        return min;
    }

    /**
     * 获取最大点向量
     *
     * @return 最大点
     */
    public Vector getMax() {
        return max;
    }

    /**
     * 获取区域中心点
     *
     * @return 中心点位置
     */
    public Location getCenter() {
        return min.getMidpoint(max).toLocation(world);
    }

    /**
     * 检查位置是否在区域内
     *
     * @param loc 位置
     * @return 是否在区域内
     */
    public boolean contains(Location loc) {
        if (loc == null || !loc.getWorld().equals(world))
            return false;
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        return x >= min.getX() && x <= max.getX()
                && y >= min.getY() && y <= max.getY()
                && z >= min.getZ() && z <= max.getZ();
    }

    /**
     * 高效遍历区域内的所有方块
     * 避免创建巨大的 List，减少内存开销
     *
     * @param consumer 方块处理逻辑
     */
    public void forEachBlock(java.util.function.Consumer<Block> consumer) {
        int minX = min.getBlockX();
        int maxX = max.getBlockX();
        int minY = min.getBlockY();
        int maxY = max.getBlockY();
        int minZ = min.getBlockZ();
        int maxZ = max.getBlockZ();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    consumer.accept(world.getBlockAt(x, y, z));
                }
            }
        }
    }

    /**
     * 获取区域内的所有方块 (不仅建议，性能较差，请改用 forEachBlock)
     *
     * @return 方块列表
     */
    public List<Block> getBlocks() {
        List<Block> blocks = new ArrayList<>();
        forEachBlock(blocks::add);
        return blocks;
    }

    @Override
    public Iterator<Block> iterator() {
        return getBlocks().iterator();
    }

    public java.util.Map<String, Object> serialize() {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("world", world.getName());
        data.put("minX", min.getX());
        data.put("minY", min.getY());
        data.put("minZ", min.getZ());
        data.put("maxX", max.getX());
        data.put("maxY", max.getY());
        data.put("maxZ", max.getZ());
        return data;
    }

    public static Region deserialize(java.util.Map<String, Object> data) {
        if (data == null)
            return null;
        World world = org.bukkit.Bukkit.getWorld((String) data.get("world"));
        if (world == null)
            return null;

        double minX = ((Number) data.get("minX")).doubleValue();
        double minY = ((Number) data.get("minY")).doubleValue();
        double minZ = ((Number) data.get("minZ")).doubleValue();
        double maxX = ((Number) data.get("maxX")).doubleValue();
        double maxY = ((Number) data.get("maxY")).doubleValue();
        double maxZ = ((Number) data.get("maxZ")).doubleValue();

        Location minLoc = new Location(world, minX, minY, minZ);
        Location maxLoc = new Location(world, maxX, maxY, maxZ);
        return new Region(minLoc, maxLoc);
    }
}
