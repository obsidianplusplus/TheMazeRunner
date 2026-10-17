package com.panda.themazerunner.gate;

import com.panda.themazerunner.utils.Region;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 核心大门实体类
 * <p>
 * 该类负责管理单个大门的完整生命周期，包括状态管理、方块数据持久化、
 * 动画计算与渲染，以及与 Bukkit API 的实体交互。
 * 不直接处理玩家交互，所有操作应通过 GateManager 进行调度。
 * </p>
 */
public class Gate {

    /**
     * 大门当前运行状态
     */
    public enum State {
        /**
         * 开启状态（完全打开，实体可能已清理）
         */
        OPEN,
        /**
         * 关闭状态（完全闭合，显示为真实方块）
         */
        CLOSED,
        /**
         * 正在执行开启或关闭动画
         */
        ANIMATING
    }

    /**
     * 滑动轴向锁定
     */
    public enum Axis {
        AUTO, X, Z
    }

    /**
     * 关门时的安全保护模式
     */
    public enum SafeMode {
        NONE, BARRIER, PUSH
    }

    /**
     * 动画实现技术方案
     */
    public enum MoveMode {
        TELEPORT, TRANSFORM
    }

    /**
     * 内部使用的不可变方块坐标键
     */
    private record BlockKey(int x, int y, int z) {
    }

    // 常量预热时间，保证丝滑
    private static final long ANIM_DELAY = 4L;

    private final JavaPlugin plugin;
    private final String name;
    private final Region leftRegion;
    private final Region rightRegion;
    private final World world;

    // --- 基础配置参数 ---
    private double slideDistance;
    private int durationSeconds;

    // --- 高级控制参数 ---
    private Axis axis = Axis.AUTO;
    private SafeMode safeMode = SafeMode.PUSH;
    private MoveMode moveMode = MoveMode.TRANSFORM;
    private float viewRange = 12.0f;
    private float cullWidth = 0; // 0 表示自动计算
    private float cullHeight = 0;

    // 性能优化开关
    private boolean keepOpenDisplays = false;
    private boolean shellOnly = false; // 仅渲染外壳

    // --- 运行时状态 ---
    private State state;
    private final Map<BlockKey, BlockData> originalBlocks;

    // 追踪每个 Display 实体的创建原点
    private final Map<BlockDisplay, Location> displayOrigins;

    private final List<BlockDisplay> leftDisplays;
    private final List<BlockDisplay> rightDisplays;

    private BukkitTask cleanupTask;
    private BukkitTask effectTask;

    private void startEffects(Region region, Vector moveVector) {
        if (effectTask != null && !effectTask.isCancelled()) {
            effectTask.cancel();
        }

        // 计算底部中心点用于播放声音
        Location center = region.getCenter();
        center.setY(region.getMin().getY());

        // 计算推力的中心点（整个大门的中心）
        // 假设 region 是当前半扇门的区域，我们需要推离开这扇门
        // 或者更准确地说，是从门缝向外推，或者简单的从门所在的区域向四周推
        org.bukkit.util.BoundingBox gateBox = getBoundingBox(region).expand(2.0, 2.0, 2.0);

        effectTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;

                // 播放轰鸣声
                if (ticks % 5 == 0) {
                    world.playSound(center, org.bukkit.Sound.ENTITY_MINECART_RIDING, 2.0f, 0.5f);
                }
                // 播放撞击声
                if (ticks % 40 == 0) {
                    world.playSound(center, org.bukkit.Sound.ENTITY_IRON_GOLEM_STEP, 1.5f, 0.5f);
                }

                // --- 气浪推力 (Air Wave) ---
                // 仅对活动区域附近的实体施加微小推力
                for (org.bukkit.entity.Entity entity : world.getNearbyEntities(gateBox)) {
                    if (!(entity instanceof org.bukkit.entity.LivingEntity)
                            && !(entity instanceof org.bukkit.entity.Player)) {
                        continue;
                    }
                    if (entity.getType() == EntityType.BLOCK_DISPLAY || entity.getType() == EntityType.ARMOR_STAND) {
                        continue;
                    }

                    // 计算从区域中心指向实体的向量
                    Location entLoc = entity.getLocation();
                    Vector pushDir = entLoc.toVector().subtract(center.toVector());

                    // 忽略Y轴差异，主要水平推开
                    pushDir.setY(0);

                    if (pushDir.lengthSquared() < 0.01) {
                        // 如果刚好重合，随机推一个方向
                        pushDir = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
                    }

                    pushDir.normalize().multiply(0.15); // 微小的反向推力
                    pushDir.setY(0.05); // 稍微带一点点上浮感，减少地面摩擦影响

                    // 施加力
                    entity.setVelocity(entity.getVelocity().add(pushDir));
                }

                // --- 粒子效果 ---
                double xLength = region.getMax().getX() - region.getMin().getX();
                double zLength = region.getMax().getZ() - region.getMin().getZ();

                // 随机生成 5 个粒子点
                for (int i = 0; i < 5; i++) {
                    double x = region.getMin().getX() + Math.random() * (xLength + 1);
                    double z = region.getMin().getZ() + Math.random() * (zLength + 1);
                    double y = region.getMin().getY();

                    Location particleLoc = new Location(world, x, y, z);

                    // 1. 底部扬尘: CAMPFIRE_COSY_SMOKE
                    world.spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, particleLoc, 1, 0.1, 0.05, 0.1, 0.01);

                    // 2. 接触面碎石: BLOCK_CRACK (STONE or MOSSY_COBBLESTONE)
                    // 让碎石稍微高一点掉落，模拟从门缝/摩擦面掉下来
                    Location debrisLoc = particleLoc.clone().add(0, 0.5 + Math.random() * 2.0, 0);
                    Material debrisMat = Math.random() > 0.5 ? Material.STONE : Material.MOSSY_COBBLESTONE;

                    world.spawnParticle(org.bukkit.Particle.BLOCK, debrisLoc, 3, 0.1, 0.1, 0.1,
                            debrisMat.createBlockData());
                }
            }
        }, 0L, 4L);
    }

    private void stopEffects() {
        if (effectTask != null && !effectTask.isCancelled()) {
            effectTask.cancel();
            effectTask = null;
        }
    }

    private long animationEpoch;
    private boolean disposed;
    private BukkitTask later(Runnable action, long delay) {
        long epoch=animationEpoch;
        return plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!disposed && epoch==animationEpoch) {
                try{action.run();}
                catch(RuntimeException error){
                    animationEpoch++;stopEffects();cleanupDisplays();
                    if(!doorwayOccupied()){restoreBlocks();state=State.CLOSED;}else state=State.OPEN;
                    plugin.getLogger().severe("Gate animation callback failed; state recovered: "+name+": "+error.getMessage());
                }
            }
        }, delay);
    }
    public java.util.Set<com.panda.themazerunner.game.arena.WalkGrid.Cell> footprint() {
        java.util.Set<com.panda.themazerunner.game.arena.WalkGrid.Cell> cells=new java.util.HashSet<>();
        for (Region r:java.util.List.of(leftRegion,rightRegion))
            for(int x=r.getMin().getBlockX();x<=r.getMax().getBlockX();x++)
                for(int z=r.getMin().getBlockZ();z<=r.getMax().getBlockZ();z++) cells.add(new com.panda.themazerunner.game.arena.WalkGrid.Cell(x,z));
        return java.util.Set.copyOf(cells);
    }
    public org.bukkit.World world() { return world; }
    public boolean forceRestore() {
        if(doorwayOccupied())return false;
        animationEpoch++;stopEffects();if(cleanupTask!=null)cleanupTask.cancel();
        restoreBlocks();cleanupDisplays();state=State.CLOSED;return true;
    }
    public void shutdownSafely() {
        boolean occupied=doorwayOccupied();dispose();
        if(!occupied)restoreBlocks();
        else plugin.getLogger().warning("Gate checkpoint retained until doorway is clear: "+name);
    }
    public void dispose() {
        disposed=true; animationEpoch++; stopEffects();
        if (cleanupTask!=null) cleanupTask.cancel();
        cleanupDisplays();
    }

    public Gate(JavaPlugin plugin, String name, Region leftRegion, Region rightRegion, double slideDistance,
            int durationSeconds) {
        if(name==null || !name.matches("[A-Za-z0-9_-]{1,48}"))throw new IllegalArgumentException("Invalid gate name");
        if(!leftRegion.getWorld().equals(rightRegion.getWorld()) || !Double.isFinite(slideDistance) || slideDistance>128 || slideDistance < -1
                || durationSeconds<1 || durationSeconds>30)throw new IllegalArgumentException("Invalid gate world, distance (-1..128) or duration (1..30)");
        long volume=0;
        for(Region region:java.util.List.of(leftRegion,rightRegion))volume+=(long)(region.getMax().getBlockX()-region.getMin().getBlockX()+1)
                *(region.getMax().getBlockY()-region.getMin().getBlockY()+1)*(region.getMax().getBlockZ()-region.getMin().getBlockZ()+1);
        if(volume>1024)throw new IllegalArgumentException("Gate volume budget exceeded (1024 blocks)");
        this.plugin = plugin;
        this.name = name;
        this.leftRegion = leftRegion;
        this.rightRegion = rightRegion;
        this.world = leftRegion.getWorld();
        this.slideDistance = slideDistance;
        this.durationSeconds = durationSeconds;
        this.state = State.CLOSED;
        this.originalBlocks = new HashMap<>();
        this.displayOrigins = new HashMap<>();
        this.leftDisplays = new ArrayList<>();
        this.rightDisplays = new ArrayList<>();
        recoverCheckpoint();
    }

    private java.nio.file.Path checkpoint() { return plugin.getDataFolder().toPath().resolve("gate-state").resolve(name+".yml"); }
    private void snapshotClosedGate() throws java.io.IOException {
        originalBlocks.clear();
        var yaml=new org.bukkit.configuration.file.YamlConfiguration();yaml.set("world",world.getName());
        for(Region region:java.util.List.of(leftRegion,rightRegion))region.forEachBlock(block->{
            BlockKey key=new BlockKey(block.getX(),block.getY(),block.getZ());
            BlockData data=block.getBlockData().clone();originalBlocks.put(key,data);
            yaml.set("blocks."+key.x()+","+key.y()+","+key.z(),data.getAsString());
        });
        com.panda.themazerunner.persistence.AtomicFiles.write(checkpoint(),yaml.saveToString());
    }
    private void recoverCheckpoint() {
        if(!java.nio.file.Files.exists(checkpoint()))return;
        try {
            var yaml=new org.bukkit.configuration.file.YamlConfiguration();yaml.load(checkpoint().toFile());
            if(!world.getName().equals(yaml.getString("world")))throw new IllegalArgumentException("Gate checkpoint world mismatch");
            var section=yaml.getConfigurationSection("blocks");
            if(section==null || section.getKeys(false).size()>1024)throw new IllegalArgumentException("Invalid gate checkpoint size");
            for(String id:section.getKeys(false)) {
                String[] xyz=id.split(",");if(xyz.length!=3)throw new IllegalArgumentException("Invalid checkpoint coordinates");
                BlockKey key=new BlockKey(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));
                org.bukkit.Location loc=new org.bukkit.Location(world,key.x(),key.y(),key.z());
                if(!leftRegion.contains(loc) && !rightRegion.contains(loc))throw new IllegalArgumentException("Checkpoint outside gate");
                originalBlocks.put(key,org.bukkit.Bukkit.createBlockData(section.getString(id)));
            }
            if(doorwayOccupied())throw new IllegalStateException("Cannot recover an occupied gate");
            restoreBlocks();
        }catch(Exception error){throw new IllegalStateException("Gate recovery blocked: "+error.getMessage(),error);}
    }

    // --- 属性访问器 ---
    public String getName() {
        return name;
    }

    public double getSlideDistance() {
        return slideDistance;
    }

    /**
     * 获取标准化的动画 tick 数，确保至少为 1 tick。
     * 防止因设置为 0 秒而导致动画异常或被跳过。
     */
    public int getDurationTicks() {
        if (durationSeconds <= 0) {
            return 1;
        }
        return durationSeconds * 20;
    }

    /**
     * 获取实际用于动画的 tick 数
     * <p>
     * TELEPORT 模式限制最大 59 ticks。
     * TRANSFORM 模式使用完整时长。
     * </p>
     */
    public int getActualAnimationTicks() {
        int ticks = getDurationTicks();
        if (moveMode == MoveMode.TELEPORT) {
            return Math.min(ticks, 59);
        }
        return ticks;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public boolean isOpen() {
        return state == State.OPEN;
    }

    // --- 配置设置器 ---
    public void setAxis(Axis axis) {
        this.axis = axis;
    }

    public void setSafeMode(SafeMode safeMode) {
        this.safeMode = safeMode;
    }

    public void setMoveMode(MoveMode moveMode) {
        this.moveMode = moveMode;
    }

    public void setViewRange(float viewRange) {
        if(!Float.isFinite(viewRange) || viewRange<0.1f || viewRange>64)throw new IllegalArgumentException("view-range must be 0.1..64");
        this.viewRange = viewRange;
    }

    public void setCulling(float w, float h) {
        if(!Float.isFinite(w) || !Float.isFinite(h) || w<0 || h<0 || w>256 || h>256)throw new IllegalArgumentException("culling must be finite in 0..256");
        this.cullWidth = w;
        this.cullHeight = h;
    }

    public void setKeepOpenDisplays(boolean keep) {
        this.keepOpenDisplays = keep;
    }

    public void setShellOnly(boolean shellOnly) {
        this.shellOnly = shellOnly;
    }

    public void open() {
        try{openInternal();}
        catch(RuntimeException error){
            animationEpoch++;stopEffects();cleanupDisplays();
            if(!doorwayOccupied()){restoreBlocks();state=State.CLOSED;}else state=State.OPEN;
            plugin.getLogger().severe("Gate opening rolled back: "+name+": "+error.getMessage());
        }
    }
    private void openInternal() {
        if (disposed || state != State.CLOSED)
            return;
        try{snapshotClosedGate();}
        catch(java.io.IOException error){plugin.getLogger().severe("Cannot journal gate; opening refused: "+error.getMessage());return;}
        animationEpoch++;

        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        state = State.ANIMATING;

        Location leftCenter = leftRegion.getCenter();
        Location rightCenter = rightRegion.getCenter();
        Location gateCenter = leftCenter.clone().add(rightCenter).multiply(0.5);

        Vector leftDir = calculateDirection(leftRegion, gateCenter);
        Vector rightDir = calculateDirection(rightRegion, gateCenter);

        animateRegion(leftRegion, leftDisplays, leftDir);
        animateRegion(rightRegion, rightDisplays, rightDir);

        // 开启特效
        startEffects(leftRegion, leftDir);

        // 修改：等待时间 = 动画时长 + 预热延迟
        long totalWait = getDurationTicks() + ANIM_DELAY;

        // 状态更新任务：动画结束时切换状态
        later( () -> {
            stopEffects(); // 停止特效
            if (state == State.ANIMATING) {
                state = State.OPEN;
            }
        }, totalWait);

        if (!keepOpenDisplays) {
            long delay = totalWait + 5;
            cleanupTask = later( () -> {
                if (state == State.OPEN) {
                    cleanupDisplays();
                }
                cleanupTask = null;
            }, delay);
        }
    }

    public void close() {
        try{closeInternal();}
        catch(RuntimeException error){
            animationEpoch++;stopEffects();cleanupDisplays();state=State.OPEN;
            plugin.getLogger().severe("Gate closing cancelled: "+name+": "+error.getMessage());
        }
    }
    private void closeInternal() {
        if (disposed || state != State.OPEN)
            return;
        animationEpoch++;

        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        state = State.ANIMATING;

        Location leftCenter = leftRegion.getCenter();
        Location rightCenter = rightRegion.getCenter();
        Location gateCenter = leftCenter.clone().add(rightCenter).multiply(0.5);

        Vector leftDir = calculateDirection(leftRegion, gateCenter);
        Vector rightDir = calculateDirection(rightRegion, gateCenter);

        if (leftDisplays.isEmpty()) {
            respawnDisplays(leftRegion, leftDisplays, leftDir);
        }
        if (rightDisplays.isEmpty()) {
            respawnDisplays(rightRegion, rightDisplays, rightDir);
        }

        later( () -> {
            reverseAnimate(leftDisplays, leftDir);
            reverseAnimate(rightDisplays, rightDir);

            long safetyCheckDelay = getDurationTicks() - 5;
            if (safetyCheckDelay < 1)
                safetyCheckDelay = 1;
            later( this::handleSafeMode, safetyCheckDelay);
        }, 2L);

        // 开启特效
        startEffects(leftRegion, leftDir);

        long totalWait = getDurationTicks() + ANIM_DELAY;

        // 状态更新与方块恢复任务
        later( () -> {
            stopEffects(); // 停止特效
            if (state == State.ANIMATING) {
                // A push is not proof the doorway is clear. Abort closing if occupied.
                if (doorwayOccupied()) {
                    cleanupDisplays(); state = State.OPEN;
                    plugin.getLogger().warning("Gate " + name + " closure aborted: doorway occupied.");
                    return;
                }
                restoreBlocks();
                cleanupDisplays(); // 恢复方块后立即清理实体
                state = State.CLOSED;
            }
        }, totalWait);
    }

    private boolean doorwayOccupied() {
        org.bukkit.World world = leftRegion.getWorld();
        return java.util.stream.Stream.concat(
                world.getNearbyEntities(getBoundingBox(leftRegion)).stream(),
                world.getNearbyEntities(getBoundingBox(rightRegion)).stream())
                .anyMatch(e -> e instanceof org.bukkit.entity.LivingEntity);
    }

    private void respawnDisplays(Region region, List<BlockDisplay> targetList, Vector openVector) {
        int minX = region.getMin().getBlockX();
        int maxX = region.getMax().getBlockX();
        int minY = region.getMin().getBlockY();
        int maxY = region.getMax().getBlockY();
        int minZ = region.getMin().getBlockZ();
        int maxZ = region.getMax().getBlockZ();

        region.forEachBlock(block -> {
            // 如启用外壳模式，跳过内部方块
            if (shellOnly) {
                int x = block.getX();
                int y = block.getY();
                int z = block.getZ();
                boolean isEdge = (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ);
                if (!isEdge)
                    return;
            }

            BlockKey key = new BlockKey(block.getX(), block.getY(), block.getZ());
            if (!originalBlocks.containsKey(key))
                return;

            BlockData data = originalBlocks.get(key);
            Location loc = block.getLocation();

            Location openLoc = loc.clone();
            if (moveMode == MoveMode.TELEPORT) {
                openLoc.add(openVector);
            }

            BlockDisplay display = (BlockDisplay) region.getWorld().spawnEntity(openLoc, EntityType.BLOCK_DISPLAY);
            display.setBlock(data);
            display.setPersistent(false); // 防止服务器崩溃后留下幽灵方块

            configureDisplay(display, region, openVector);

            displayOrigins.put(display, loc.clone());

            if (moveMode == MoveMode.TRANSFORM) {
                // TELEPORT 模式下 openLoc 已经偏移，TRANSFORM 模式下 openLoc 就是原点
                // 且刚生成就在位置上，无需多余 teleport
                Transformation cur = display.getTransformation();
                Vector3f translation = new Vector3f((float) openVector.getX(), (float) openVector.getY(),
                        (float) openVector.getZ());
                display.setTransformation(new Transformation(
                        translation,
                        cur.getLeftRotation(),
                        cur.getScale(),
                        cur.getRightRotation()));

                // 补全插值延迟设置为 0
                display.setInterpolationDelay(0);
            }

            targetList.add(display);
        });
    }

    private void handleSafeMode() {
        if (safeMode == SafeMode.NONE)
            return;

        // 获取左右区域的包围盒
        org.bukkit.util.BoundingBox leftBox = getBoundingBox(leftRegion);
        org.bukkit.util.BoundingBox rightBox = getBoundingBox(rightRegion);

        if (safeMode == SafeMode.PUSH) {
            // PUSH 模式：检测区域内实体并推开
            pushEntities(leftBox);
            pushEntities(rightBox);
        } else if (safeMode == SafeMode.BARRIER) {
            // Never create a collision block inside an occupied doorway.
            // Legacy BARRIER now uses the same non-solid evacuation as PUSH.
            pushEntities(leftBox);
            pushEntities(rightBox);
        }
    }

    private org.bukkit.util.BoundingBox getBoundingBox(Region region) {
        Vector min = region.getMin();
        Vector max = region.getMax();
        // 稍微扩大一点范围以确保检测准确
        return new org.bukkit.util.BoundingBox(
                min.getX(), min.getY(), min.getZ(),
                max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    private void pushEntities(org.bukkit.util.BoundingBox box) {
        // 查找区域内的所有生物（优化：仅查询包围盒附近）
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(box)) {
            // 过滤非生物实体（如掉落物、显示实体）
            if (!(entity instanceof org.bukkit.entity.LivingEntity)) {
                continue;
            }

            // 计算推力方向
            Location entLoc = entity.getLocation();
            Location gateCenter = leftRegion.getCenter().add(rightRegion.getCenter()).multiply(0.5);
            Vector pushDir = entLoc.toVector().subtract(gateCenter.toVector());

            // 修正推力方向：使其垂直于大门滑动轴向
            // 使用缓存的 resolvedAxis 以确保 AUTO 模式下的一致性
            Axis useAxis = this.resolvedAxis != null ? this.resolvedAxis : this.axis;

            if (useAxis == Axis.X) {
                pushDir.setX(0);
                if (Math.abs(pushDir.getZ()) < 0.1)
                    pushDir.setZ(1);
            } else if (useAxis == Axis.Z) {
                pushDir.setZ(0);
                if (Math.abs(pushDir.getX()) < 0.1)
                    pushDir.setX(1);
            } else {
                // 如果 resolvedAxis 仍未设定 (例如刚创建还没动过)，则尝试推测或默认处理
                if (Math.abs(pushDir.getX()) > Math.abs(pushDir.getZ())) {
                    pushDir.setZ(0); // 假设是X轴门
                } else {
                    pushDir.setX(0); // 假设是Z轴门
                }
                pushDir.setY(0);
            }

            if (pushDir.lengthSquared() < 0.01) {
                pushDir = new Vector(1, 0, 0);
            }

            pushDir.normalize().multiply(1.5);
            pushDir.setY(0.2);

            entity.setVelocity(pushDir);

            // 仅向玩家发送警告信息，防止 LivingEntity 编译错误
            if (entity instanceof org.bukkit.entity.Player p) {
                p.sendMessage("§c大门正在关闭，请勿靠近！");
            }
        }
    }

    private void fillBarriersFromOriginalBlocks() {
        for (Map.Entry<BlockKey, BlockData> entry : originalBlocks.entrySet()) {
            BlockKey k = entry.getKey();
            Block block = world.getBlockAt(k.x(), k.y(), k.z());
            if (block.getType() == Material.AIR) {
                block.setType(Material.BARRIER);
            }
        }
    }

    public void cleanupDisplays() {
        clearDisplays(leftDisplays);
        clearDisplays(rightDisplays);
    }

    private void clearDisplays(List<BlockDisplay> list) {
        for (BlockDisplay bd : list) {
            displayOrigins.remove(bd);
            if (bd.isValid())
                bd.remove();
        }
        list.clear();
    }

    private Axis resolvedAxis; // 缓存实际计算出的轴向 (用于 PUSH 模式等)

    private Vector calculateDirection(Region region, Location gateCenter) {
        Location regionCenter = region.getCenter();
        Vector direction = regionCenter.toVector().subtract(gateCenter.toVector());

        Axis currentAxis = this.axis;
        if (currentAxis == Axis.AUTO) {
            if (Math.abs(direction.getX()) > Math.abs(direction.getZ())) {
                currentAxis = Axis.X;
            } else {
                currentAxis = Axis.Z;
            }
        }

        this.resolvedAxis = currentAxis; // 缓存解析后的轴

        if (currentAxis == Axis.X) {
            direction.setX(Math.signum(direction.getX()));
            direction.setZ(0);
            direction.setY(0);
        } else { // Z
            direction.setX(0);
            direction.setZ(Math.signum(direction.getZ()));
            direction.setY(0);
        }

        double dist = this.slideDistance;
        if (dist <= 0) {
            if (direction.getX() != 0) {
                dist = region.getMax().getX() - region.getMin().getX() + 1;
            } else {
                dist = region.getMax().getZ() - region.getMin().getZ() + 1;
            }
        }

        return direction.multiply(dist);
    }

    private void animateRegion(Region region, List<BlockDisplay> targetDisplays, Vector moveVector) {
        if (targetDisplays.isEmpty()) {
            int minX = region.getMin().getBlockX();
            int maxX = region.getMax().getBlockX();
            int minY = region.getMin().getBlockY();
            int maxY = region.getMax().getBlockY();
            int minZ = region.getMin().getBlockZ();
            int maxZ = region.getMax().getBlockZ();

            region.forEachBlock(block -> {
                if (block.getType() == Material.AIR)
                    return;

                BlockKey key = new BlockKey(block.getX(), block.getY(), block.getZ());
                originalBlocks.put(key, block.getBlockData().clone());
                block.setType(Material.AIR);

                // 外壳模式判定：只给边缘方块生成 Display，内部方块只变空气
                if (shellOnly) {
                    int x = block.getX();
                    int y = block.getY();
                    int z = block.getZ();
                    boolean isEdge = (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ);
                    if (!isEdge)
                        return;
                }

                Location originLoc = block.getLocation().clone();

                BlockDisplay display = (BlockDisplay) region.getWorld().spawnEntity(originLoc,
                        EntityType.BLOCK_DISPLAY);
                display.setBlock(originalBlocks.get(key));
                display.setPersistent(false); // 防止服务器崩溃后留下幽灵方块

                configureDisplay(display, region, moveVector);

                // --- 第 0 tick: 预设动画时长 ---
                // 必须在移动发生前一个 tick 设置，否则客户端可能按 0 tick 处理直接瞬移
                if (moveMode == MoveMode.TELEPORT) {
                    display.setTeleportDuration(getActualAnimationTicks());
                } else {
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(getActualAnimationTicks());
                }

                display.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(1, 1, 1),
                        new AxisAngle4f(0, 0, 0, 1)));

                displayOrigins.put(display, originLoc);
                targetDisplays.add(display);
            });
        }

        // --- 第 1 tick: 触发移动 ---
        later( () -> {
            for (BlockDisplay display : targetDisplays) {
                if (!display.isValid())
                    continue;

                if (moveMode == MoveMode.TELEPORT) {
                    Location origin = displayOrigins.get(display);
                    if (origin != null) {
                        Location targetLoc = origin.clone().add(moveVector);
                        display.teleport(targetLoc);
                    }
                } else {
                    Transformation cur = display.getTransformation();
                    Vector3f translation = new Vector3f((float) moveVector.getX(), (float) moveVector.getY(),
                            (float) moveVector.getZ());

                    display.setTransformation(new Transformation(
                            translation,
                            cur.getLeftRotation(),
                            cur.getScale(),
                            cur.getRightRotation()));
                }
            }
        }, ANIM_DELAY);
    }

    private void configureDisplay(BlockDisplay display, Region region, Vector moveVector) {
        display.setViewRange(this.viewRange);

        if (moveMode == MoveMode.TRANSFORM) {
            float w = this.cullWidth;
            float h = this.cullHeight;

            if (w <= 0 || h <= 0) {
                double rX = region.getMax().getX() - region.getMin().getX() + 1;
                double rY = region.getMax().getY() - region.getMin().getY() + 1;
                double rZ = region.getMax().getZ() - region.getMin().getZ() + 1;
                double dist = moveVector.length();

                w = (float) (Math.max(rX, rZ) + dist + 5.0);
                h = (float) (rY + 5.0);
            }

            display.setDisplayWidth(w);
            display.setDisplayHeight(h);
        }
    }

    private void reverseAnimate(List<BlockDisplay> displays, Vector openVector) {
        int durationTicks = getActualAnimationTicks();

        // --- 第 0 tick: 预设时长 ---
        if (moveMode == MoveMode.TELEPORT) {
            for (BlockDisplay display : displays) {
                if (!display.isValid())
                    continue;
                display.setTeleportDuration(durationTicks);
            }
        } else {
            for (BlockDisplay display : displays) {
                if (!display.isValid())
                    continue;
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(durationTicks);
            }
        }

        // --- 第 1 tick: 触发移动 ---
        later( () -> {
            if (moveMode == MoveMode.TELEPORT) {
                for (BlockDisplay display : displays) {
                    if (!display.isValid())
                        continue;
                    Location origin = displayOrigins.get(display);
                    if (origin == null) {
                        origin = display.getLocation().clone().subtract(openVector);
                    }
                    if (origin != null) {
                        display.teleport(origin);
                    }
                }
            } else {
                for (BlockDisplay display : displays) {
                    if (!display.isValid())
                        continue;
                    Transformation cur = display.getTransformation();
                    display.setTransformation(new Transformation(
                            new Vector3f(0, 0, 0),
                            cur.getLeftRotation(),
                            cur.getScale(),
                            cur.getRightRotation()));
                }
            }
        }, 1L);
    }

    public void restoreBlocks() {
        for (Map.Entry<BlockKey, BlockData> entry : originalBlocks.entrySet()) {
            BlockKey k = entry.getKey();
            // 使用 false 参数禁止物理更新，防止大门关闭时触发红石/沙子掉落
            world.getBlockAt(k.x(), k.y(), k.z()).setBlockData(entry.getValue(), false);
        }
        originalBlocks.clear();
        try{java.nio.file.Files.deleteIfExists(checkpoint());}
        catch(java.io.IOException error){throw new IllegalStateException("Cannot finalize gate recovery: "+error.getMessage(),error);}
    }

    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("leftRegion", leftRegion.serialize());
        data.put("rightRegion", rightRegion.serialize());
        data.put("slideDistance", slideDistance);
        data.put("durationSeconds", durationSeconds);
        data.put("axis", axis.name());
        data.put("safeMode", safeMode.name());
        data.put("moveMode", moveMode.name());
        data.put("viewRange", viewRange);
        data.put("cullWidth", cullWidth);
        data.put("cullHeight", cullHeight);
        data.put("keepOpenDisplays", keepOpenDisplays);
        data.put("shellOnly", shellOnly);
        return data;
    }

    public static Gate deserialize(JavaPlugin plugin, Map<String, Object> data) {
        if (data == null)
            return null;

        String name = (String) data.get("name");

        Object leftObj = data.get("leftRegion");
        Object rightObj = data.get("rightRegion");

        @SuppressWarnings("unchecked")
        Map<String, Object> leftMap = (leftObj instanceof org.bukkit.configuration.MemorySection)
                ? ((org.bukkit.configuration.MemorySection) leftObj).getValues(true)
                : (Map<String, Object>) leftObj;

        @SuppressWarnings("unchecked")
        Map<String, Object> rightMap = (rightObj instanceof org.bukkit.configuration.MemorySection)
                ? ((org.bukkit.configuration.MemorySection) rightObj).getValues(true)
                : (Map<String, Object>) rightObj;

        Region left = Region.deserialize(leftMap);
        Region right = Region.deserialize(rightMap);

        if (left == null || right == null)
            return null;

        double slide = ((Number) data.get("slideDistance")).doubleValue();
        int duration = ((Number) data.get("durationSeconds")).intValue();

        Gate gate = new Gate(plugin, name, left, right, slide, duration);

        if (data.containsKey("axis"))
            gate.setAxis(Axis.valueOf((String) data.get("axis")));
        if (data.containsKey("safeMode"))
            gate.setSafeMode(SafeMode.valueOf((String) data.get("safeMode")));
        if (data.containsKey("moveMode"))
            gate.setMoveMode(MoveMode.valueOf((String) data.get("moveMode")));
        if (data.containsKey("viewRange"))
            gate.setViewRange(((Number) data.get("viewRange")).floatValue());
        if (data.containsKey("cullWidth"))
            gate.cullWidth = ((Number) data.get("cullWidth")).floatValue();
        if (data.containsKey("cullHeight"))
            gate.cullHeight = ((Number) data.get("cullHeight")).floatValue();
        if (data.containsKey("keepOpenDisplays"))
            gate.setKeepOpenDisplays((boolean) data.get("keepOpenDisplays"));
        if (data.containsKey("shellOnly"))
            gate.setShellOnly((boolean) data.get("shellOnly"));

        return gate;
    }
}
