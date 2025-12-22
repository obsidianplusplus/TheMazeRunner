[< 返回主页 (Back to Home)](../README.md)

# 身份系统 (Identity System)

本文档详细介绍了迷宫行者 (The Maze Runner) 插件中的 **身份系统**。该系统独立于原有的职业系统 (Classes)，用于定义玩家在剧情或游戏中的特殊角色。

## 1. 简介

身份系统用于定义玩家的特殊身份，这些身份通常代表着阵营、特殊使命或剧情角色，与战斗职业 (如行者、医疗兵) 是并行的概念。

## 2. 可用身份

目前系统定义了以下三种身份：

| 身份 ID | 名称 | 描述 |
| :--- | :--- | :--- |
| `SAVIOR` | **救世主** | 拥有特殊使命的关键角色。 |
| `WCKD` | **W.C.K.D (实验员)** | 操控迷宫实验的幕后人员。 |
| `WANDERER` | **流浪者** | 在迷宫中游荡的未知角色。 |

## 3. 开发接口 (Developer API)

开发者可以通过 `IdentityManager` 管理玩家身份。

```java
// 获取管理器实例
IdentityManager identityManager = TheMazeRunner.getInstance().getIdentityManager();

// 设置身份
identityManager.setIdentity(player, Identity.SAVIOR);

// 获取身份
Identity identity = identityManager.getIdentity(player);
if (identity == Identity.WCKD) {
    // ...
}

// 检查身份
if (identityManager.hasIdentity(player, Identity.WANDERER)) {
    // ...
}

// 移除身份
identityManager.removeIdentity(player);
```
