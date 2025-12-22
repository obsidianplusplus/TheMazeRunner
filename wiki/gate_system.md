[< 返回主页 (Back to Home)](../README.md)

# 大门系统 (Gate System) 技术白皮书

> **版本适用性**: Minecraft 1.20+ (核心依赖 `BlockDisplay` 实体)

## 目录
1. [系统概述](#1-系统概述)
2. [核心架构与原理](#2-核心架构与原理)
3. [完整配置详解 (YAML)](#3-完整配置详解-yaml)
4. [参数深度解析](#4-参数深度解析)
   - [动画模式 (MoveMode)](#41-动画模式-movemode)
   - [安全模式 (SafeMode)](#42-安全模式-safemode)
   - [性能优化参数](#43-性能优化参数)
5. [操作指南 (Step-by-Step)](#5-操作指南-step-by-step)
6. [指令与权限指南](#6-指令与权限指南)
7. [常见问题与故障排除 (Troubleshooting)](#7-常见问题与故障排除-troubleshooting)

---

## 1. 系统概述

`The Maze Runner` 插件的大门系统（Gate System）是为了满足电影级场景复刻需求而开发的超高性能动态建筑系统。与传统的 "FallingBlock"（掉落沙）或 "Clone"（方块克隆）方案不同，本系统利用了 Minecraft 现代版本引入的显示实体（Display Entities）技术。

**核心优势：**
*   **毫秒级丝滑动画**：利用客户端插值技术（Interpolation），无论服务器 TPS 如何，客户端都能看到 60FPS 甚至 144FPS 的流畅开门动作。
*   **超远视距渲染**：突破传统实体的 64 格渲染限制，支持可视距离自定义，确保在巨型迷宫的另一端也能看到大门状态。
*   **极低服务器负载**：在动静分离设计下，静态时回归普通方块，动态时仅需维护少量实体，对服务器性能影响微乎其微。

---

## 2. 核心架构与原理

大门被设计为**双扇对开**结构。

### 2.1 区域划分 (Regions)
每个大门逻辑上包含两个长方体区域：
*   **Left Region (左扇区)**：开启时向左滑动。
*   **Right Region (右扇区)**：开启时向右滑动。

> **注意**：这里的"左"和"右"是相对于大门的**中轴线**而言的。系统会自动计算两个区域的中心点，并根据选区位置自动判定滑动方向。

### 2.2 动静分离机制 (State Switching)
为了兼顾物理碰撞和动画效果，大门在不同状态下有完全不同的物理存在形式：

1.  **关闭状态 (CLOSED)**
    *   **表现**：由真实的 Minecraft 方块组成（如石砖、铁块等）。
    *   **特性**：拥有完整的物理碰撞体积、防爆属性、不透明度。玩家和怪物会被阻挡。
    *   **性能**：消耗为 0（即普通地图方块）。

2.  **开启状态/动画中 (OPEN/OPENING/CLOSING)**
    *   **变化瞬间**：真实方块被替换为 `AIR`（空气）。
    *   **生成实体**：在原位置生成 `BlockDisplay` 实体。
    *   **动画**：实体开始执行插值位移。
    *   **特性**：无物理碰撞（玩家可穿过），视觉上不仅是方块，还可以根据配置进行缩放或形变（目前主要使用位移）。

---

## 3. 完整配置详解 (YAML)

所有大门数据存储于 `gates.yml` 中。以下是一份包含所有字段的完整配置示例：

```yaml
gates:
  # 大门唯一标识符 (ID)
  NorthGate:
    # 所在世界名称
    world: "world"
    
    # ---------------- 区域定义 ----------------
    # 左扇区 (Min/Max 坐标)
    leftRegion:
      min:
        x: 100.0
        y: 64.0
        z: -50.0
      max:
        x: 105.0
        y: 74.0
        z: -48.0
    # 右扇区
    rightRegion:
      min:
        x: 106.0
        y: 64.0
        z: -50.0
      max:
        x: 111.0
        y: 74.0
        z: -48.0
        
    # ---------------- 核心参数 ----------------
    # 开启方向 (通常由系统自动计算，无需手动修改)
    # 格式: Vector(x, y, z)
    openDirection:
      x: 1.0
      y: 0.0
      z: 0.0
      
    # 门体材质 (用于生成真实方块)
    material: STONE_BRICKS
    
    # ---------------- 动画控制 ----------------
    # 滑动轴向: AUTO (自动), X, Z
    axis: AUTO
    
    # 滑动距离 (方块数)
    # 设置为 -1.0 时为自动模式，距离 = 扇区宽度
    slide-distance: 10.0
    
    # 动画时长 (秒)
    duration: 5.0
    
    # 关门延迟 (秒): 调用 open 后多少秒自动关闭
    # 设置为 -1 代表手动控制，不自动关闭
    close-delay: -1
    
    # ---------------- 高级技术参数 ----------------
    # 移动/动画模式: TRANSFORM (推荐) 或 TELEPORT
    move-mode: TRANSFORM
    
    # 安全模式: NONE, BARRIER, PUSH
    safe-mode: PUSH
    
    # 保持实体常驻: true/false
    keep-open: false
    
    # 仅渲染外壳优化: true/false
    shell-only: true
    
    # 视距倍率因子 (默认 1.0)
    view-range: 1.5
    
    # 剔除框补偿 (宽 高) - 仅 TRANSFORM 模式
    cull-width: 0.0
    cull-height: 0.0
```

---

## 4. 参数深度解析

### 4.1 动画模式 (MoveMode)

这是大门系统的核心引擎设置，决定了动画如何被发送给客户端。

#### A. `TRANSFORM` (变换模式) - **[强烈推荐]**
*   **技术原理**：通过修改 Display 实体的 `transformation` 矩阵数据（Translation/Scale/Rotation）。
*   **动画时长**：**无限制**。可以设置 10 秒、60 秒甚至 1 小时的超慢速移动。
*   **流畅度**：**极高**。完全依赖客户端 GPU 插值。
*   **局限性**：由于实体坐标实际上一直在原点没动（只是渲染偏移了），当实体的"原点"移出玩家屏幕外时，Minecraft 可能会判定该实体"不可见"从而停止渲染，导致大门突然消失。
    *   *解决方案*：调整 `cull-width` 和 `cull-height` 手动扩大包围盒。

#### B. `TELEPORT` (传送模式)
*   **技术原理**：使用 `entity.teleport()` 并配合 `teleportDuration` 标志。
*   **动画时长**：**严格限制在 59 ticks (约 2.95秒) 以内**。
    *   *警告*：如果你在 TELEPORT 模式下设置 `duration: 10.0`，系统会强制将其钳制（Clamp）在 59 ticks。表现为：大门慢慢移动了3秒，然后瞬间跳到终点。这是 Minecraft 协议层的限制，无法突破。
*   **流畅度**：高，但受限于时长限制。
*   **优势**：实体的真实坐标会跟随移动，因此**不容易出现视锥剔除（消失）**的问题。适合且仅适合短平快的小型门。

---

### 4.2 安全模式 (SafeMode)

当大门关闭（从虚体变回实体方块）时，如果不加处理，站在门框内的玩家会卡在方块里窒息。

*   **`NONE` (无)**：不做检查。若玩家在门中间，直接被方块掩埋。适合非玩家活动区域的装饰门。
*   **`BARRIER` (空气墙)**：在方块生成的前一刻，先填充 `BARRIER` 隐形基岩。这样玩家会被挤在外面进不来，也不会被卡死。
*   **`PUSH` (气浪排斥 - 推荐)**：
    *   **机制**：在关门过程中，持续检测门洞区域内的实体（玩家、怪物）。
    *   **效果**：一旦检测到实体，给予其一个反向的 **Velocity (速度矢量)**，模拟大门闭合产生的巨大气压将人推开。
    *   **特效**：伴随 `ENTITY_GENERIC_EXPLODE` (爆炸/气浪) 声音和 `BLOCK_CRACK` (地面碎石) 粒子效果，极具沉浸感。

---

### 4.3 性能优化参数

对于大型服务器，这些参数至关重要。

*   **`shell-only` (仅空心外壳)**
    *   如果大门非常厚（例如 5 格厚的城墙），内部的 3 层方块玩家根本看不见。
    *   开启此选项后，系统**只生成最外层一圈**的显示实体。
    *   **效果**：直接**减少 60%-80% 的实体数量**，大幅提升客户端 FPS。

*   **`keep-open` (保持常驻)**
    *   `true`：大门完全开启后，显示实体**不销毁**，继续保持在开启位置。
        *   *优点*：关门时无需重新生成实体，响应零延迟，动画无缝衔接。
        *   *缺点*：长期占用服务端内存和客户端渲染资源。
    *   `false` (默认)：大门开启到位后，**销毁所有实体**。
        *   *优点*：平时不占资源。
        *   *缺点*：下次关门时需要 1 tick 重新生成实体，可能会有极微小的闪烁。

---

## 5. 操作指南 (Step-by-Step)

### 步骤一：准备工具
1.  输入 `/gate wand` 获取选区魔杖（通常是木斧，兼容 WorldEdit）。

### 步骤二：选区 (Selection)
1.  **左键点击**左扇区的左下角。
2.  **右键点击**左扇区的右上角。
    *   *提示*：确保选区包含完整的门体材质。
3.  输入指令保存左扇区：
    *   `/gate select left`

4.  重复上述步骤框选**右扇区**。
5.  输入指令保存右扇区：
    *   `/gate select right`

### 步骤三：创建大门
输入创建指令：
` /gate create <名称> [滑动距离] [时间]`

*   **示例**：创建一个名为 `MainGate`，自动计算距离，开门耗时 8 秒的大门。
    *   `/gate create MainGate -1 8`

### 步骤四：后期微调 (重要)
新创建的大门可能默认使用了 `TELEPORT` 模式（如果版本较旧）或需要调整轴向。强烈建议创建后根据情况调整：

1.  **切换为平滑模式**（推荐）：
    `/gate edit MainGate movemode TRANSFORM`
2.  **开启气浪保护**：
    `/gate edit MainGate safemode PUSH`
3.  **开启空心优化**（如果门很厚）：
    `/gate edit MainGate shellonly true`

---

## 6. 指令与权限指南

所有指令属于 `/gate` 主命令。

| 子命令 | 参数 | 权限节点 | 描述 |
| :--- | :--- | :--- | :--- |
| `wand` | - | `tmr.gate.wand` | 获取魔杖 |
| `select` | `<left/right>` | `tmr.gate.select` | 将当前WE选区设为门扇区 |
| `create` | `<name> [dist] [time]` | `tmr.gate.admin` | 创建大门 |
| `delete` | `<name>` | `tmr.gate.admin` | 删除大门配置（需手动清除方块） |
| `open` | `<name>` | `tmr.gate.control` | 开启大门 |
| `close` | `<name>` | `tmr.gate.control` | 关闭大门 |
| `list` | - | `tmr.gate.admin` | 列出所有大门 |
| `info` | `<name>` | `tmr.gate.admin` | 查看大门详细参数 |
| `edit` | `<name> <prop> <val>` | `tmr.gate.admin` | 修改属性 (支持 Tab 补全) |

---

## 7. 常见问题与故障排除 (Troubleshooting)

### Q1: 大门移动到一半突然消失了？
*   **原因**：你使用了 `TRANSFORM` 模式，且大门移动距离较远。实体的"原始坐标"留在了原地，已经移出了你的视线范围（Frustum Culling）。
*   **解决**：
    1.  尝试增大 `viewrange` 参数：`/gate edit <name> viewrange 2.0`
    2.  (推荐) 使用 `cull` 参数手动扩大包围盒：`/gate edit <name> cull 30 30` (单位是方块数，设置得比门稍大一点即可)。

### Q2: 既然 `TRANSFORM` 这么好，为什么还要 `TELEPORT` 模式？
*   **回答**：`TRANSFORM` 是 1.19.4+ 的新特性。`TELEPORT` 是兼容性方案。此外，对于**极快速**（1-2秒）的短距离门（如自动玻璃门），`TELEPORT` 的同步性可能略好于 `TRANSFORM`，且不需要配置 Culling。但对于迷宫大门，请**务必**使用 `TRANSFORM`。

### Q3: 设置了动画时间 10秒，但大门 3秒就跑完了？
*   **原因**：你当前的模式是 `TELEPORT`。Minecraft 协议限制单次平滑传送最大时长为 **59 ticks (2.95秒)**。
*   **解决**：请切换模式：`/gate edit <name> movemode TRANSFORM`。

### Q4: 关门时玩家被卡在墙里了。
*   **解决**：请设置 SafeMode：`/gate edit <name> safemode PUSH`。这会把玩家推开。

### Q5: 服务器 TPS 很高，但大门动画看起来一卡一卡的。
*   **原因**：可能是 `close-delay` 或逻辑触发频率过高？或者您在观察别的实体的动画？
*   **澄清**：本系统的动画是在**客户端**运行的。即使服务器 TPS 掉到 5，只要客户端帧率正常，大门动画就应该是丝滑的。如果卡顿，请检查是否误用了 `TELEPORT` 模式并频繁刷新位置，或者客户端本身 FPS 过低。开启 `shell-only` 优化通常能解决客户端掉帧问题。

---

## 8. 开发者接口 (API)

开发者可以通过 `GateManager` 访问大门系统的核心功能。

```java
// 1. 获取 GateManager 实例 (通常从插件主类获取)
GateManager gateManager = plugin.getGateManager();

// 2. 获取指定大门
Gate gate = gateManager.getGate("NorthGate");
if (gate != null) {
    // 3. 执行操作
    gate.open();
    
    // 4. 修改配置 (支持热更新，即时生效)
    gate.setMoveMode(Gate.MoveMode.TRANSFORM);
    gate.setSafeMode(Gate.SafeMode.PUSH);
    gate.setShellOnly(true); // 开启极致优化
    
    // 5. 获取状态
    if (gate.isOpen()) {
        plugin.getLogger().info("大门已开启");
    }
}

// 6. 获取所有大门名称
Collection<String> allGates = gateManager.getGateNames();
```
