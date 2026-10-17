> 历史设计文档：部分内容未实现或已调整。当前状态请以 [审计报告](../docs/AUDIT_AND_ROADMAP.md) 和 [验证记录](../docs/VALIDATION.md) 为准；性能与语音宣传未经实测。

[< 返回主页 (Back to Home)](../README.md)

# 区域系统 (Area System)

本文档详细介绍了迷宫行者 (The Maze Runner) 插件中的 **区域系统**。该系统用于管理游戏中的关键地点和区域，支持触发事件、位置判定等功能。

## 1. 区域类型

| 类型 | 键名 (Keys) | 说明 |
| :--- | :--- | :--- |
| **林间空地 (Glade)** | `glade` | 定义了林间空地的整体范围 (长方体区域)。 |
| **余烬篝火 (Ember Bonfire)** | `bonfire_loc` (点)<br>`bonfire_area` (区) | **位置**: 篝火的中心坐标。<br>**区域**: 围绕篝火的会议与审判区域。 |
| **集结钟 (Gathering Bell)** | `bell_loc` | 集结钟的坐标位置。 |

## 2. 游戏机制 (Game Mechanics)

### 2.1 集结钟 (Gathering Bell)
集结钟是林间空地的核心互动元素，用于召集所有行者。

*   **敲钟权 (Ring Rights)**:
    *   **获取方式**: 当 **任意玩家死亡** 时，**全员** 都会自动获得一次敲钟权。
    *   **限制**: "实验员" (W.C.K.D) 身份的玩家无法敲钟，即使拥有权利。
    *   **消耗**: 全局共享一次机会。一旦 **任意玩家** 成功敲响集结钟，该权利即被消耗，**全员** 都会失去敲钟权。不可累积。

    *   **消耗**: 全局共享一次机会。一旦 **任意玩家** 成功敲响集结钟，该权利即被消耗，**全员** 都会失去敲钟权。不可累积。
    *   **后果 (Consequences)**:
        *   **归还倒计时**: 敲钟后，所有位于 **林间空地之外** 的玩家（流浪者除外）会收到 **60秒** 的倒计时警告。
        *   **强制传送**: 倒计时期间，如果这些玩家试图踏入林间空地，将被直接传送至 **余烬篝火** 并强制加入会议。
        *   **大门关闭**: 倒计时结束 (60秒后)，所有大门自动关闭。

### 2.2 会议机制 (Meeting Mechanics)
集结钟敲响后将触发复杂的会议流程（包括传送、茶水时间、正式会议、投票与审判）。

> **详细文档**: 请参阅 [会议与审判系统 (Meeting & Trial)](meeting_system.md) 获取完整机制说明。

### 2.3 流浪者限制 (Wanderer Restriction)
系统支持限制 **流浪者 (Wanderer)** 身份的玩家进入林间空地。
*   **默认状态**: 禁止进入。
*   **权限控制**: 管理员可通过命令 `/area togglewanderer` 随时开启或关闭此限制。
*   **效果**: 被限制的流浪者在尝试进入林间空地时会被弹开并收到提示。

## 3. 命令使用 (Commands)

所有区域命令均以 `/area` 开头。

### 3.1 获取工具
```
/area wand
```
*   获取 **区域选取工具** (木锄)。
*   **左键** 点击方块选择点 1。
*   **右键** 点击方块选择点 2。

### 3.2 设置区域
先使用工具选择两个点，确立一个长方体区域，然后执行以下命令：

*   **设置林间空地**:
    ```
    /area setglade
    ```
*   **设置篝火会议区域**:
    ```
    /area setbonfirearea
    ```

### 3.3 设置定点
将准星对准目标方块，然后执行以下命令：

*   **设置余烬篝火位置**:
    ```
    /area setbonfire
    ```
*   **设置集结钟位置**:
    ```
    /area setbell
    ```

### 3.4 游戏控制
*   **开启会议**:
    ```
    /area startmeeting
    ```
*   **结束会议**:
    ```
    /area stopmeeting
    ```
    *   熄灭铜灯。
    *   解除玩家的移动限制。
*   **切换流浪者进入权限**:
    ```
    /area togglewanderer
    ```
    *   允许/禁止流浪者进入林间空地。

## 4. 开发接口 (Developer API)

开发者可以通过 `AreaManager` 获取区域数据。

```java
// 获取管理器实例
AreaManager areaManager = TheMazeRunner.getInstance().getAreaManager();

// 获取林间空地区域
Region glade = areaManager.getGladeRegion();
if (glade.contains(player.getLocation())) {
    // 玩家在林间空地内
}

// 获取重要地标
Location bonfire = areaManager.getBonfireLocation();
Location bell = areaManager.getGatheringBellLocation();

// 会议状态
if (areaManager.isMeetingInProgress()) {
    // 会议进行中...
}

// 检查流浪者权限
if (areaManager.isWandererAccessAllowed()) {
    // ...
}
```

## 5. 数据存储
区域数据保存在 `plugins/TheMazeRunner/areas.yml` 文件中。
