# Minecraft Java 26.3 升级

本次目标为Minecraft Java版26.3服务端插件，不是JDK26或26.0.3。生产编译基线为**Java25 / Paper26.3.build.200-stable**，插件版本仍为2.0.0-SNAPSHOT。

## 版本依据

2026-10-17实际查询并交叉核对：

- [Mojang版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)：26.3为release，其版本描述中的javaVersion.majorVersion为25。
- [Paper26.3最新构建](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/latest)：当时为200，channel=STABLE。
- [Paper Maven元数据](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml)：包含26.3.build.200-stable。元数据的latest字段当时指向26.3-pre-2.build.0-alpha，因此没有直接使用latest。
- [该API的Gradle模块描述](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/26.3.build.200-stable/paper-api-26.3.build.200-stable.module)：org.gradle.jvm.version=25。

依赖固定到上述稳定版本；构建时不会自动追踪预发布版本。

## 代码与构建变化

- Maven使用release=25，Enforcer要求JDK25+与Maven3.9+；CI同步使用Temurin25。旧Java环境在构建早期得到明确提示。
- plugin.yml声明api-version: '26.3'，停止将1.21.x作为当前运行支持目标。
- 玩家生命恢复、医疗技能和巡猎实体改用Attribute.MAX_HEALTH、Attribute.MOVEMENT_SPEED。
- 模板世界改用GameRules.SPAWN_MOBS、ADVANCE_TIME、ADVANCE_WEATHER、MOB_GRIEFING和FIRE_SPREAD_RADIUS_AROUND_PLAYER。禁用火焰传播使用整数0，对应旧DO_FIRE_TICK=false的API桥接语义。
- 新模板关闭LOCATOR_BAR，避免原生定位栏绕过路线探索与位置情报。已有自有地图需要管理员自行决定是否关闭定位栏。
- 更新Compiler3.16.0、Surefire3.6.0、Shade3.6.2和JUnit6.1.3。运行依赖仍全部provided，没有把服务器API或测试框架打进JAR。
- 状态恢复仍使用Paper公开ItemStack.serializeAsBytes/deserializeBytes接口；没有引入NMS、版本反射或自制生产物品编码。

## 回归测试与边界

中央仓库目前只有MockBukkit26.2模型（4.117.0），没有26.3模型。将其直接与26.3 API运行会在加载decorated_pot_pattern注册表时报错，无法代表插件自身缺陷，也不能靠忽略错误获得可信结果。

Maven在同一clean verify中执行两组独立测试：

1. 28项纯规则/接口单测使用生产26.3 API运行。
2. 16项MockBukkit模拟集成使用独立26.2.build.134-stable API运行。该API由Dependency插件复制到target/mockbukkit-runtime，只替换此Surefire执行的API classpath；生产与测试源码始终使用26.3编译，生产POM依赖、插件描述和发布JAR仍面向26.3。

测试夹具迁移到org.mockbukkit.mockbukkit包，删除旧版模拟标题、饥饿/速度与材质数量编码替身。使用MockBukkit自身的物品映射序列化，并补充自定义名称/PDC/速度/饥饿消耗恢复断言。该映射不是Paper原生NBT。模拟玩家存盘仍为空操作，有限世界仍只把AIR视为可通行；命令模拟补齐真实客户端PlayerCommandPreprocessEvent要求的斜杠前缀。

此安排保留全部既有回归，不声称完成26.3真实服务端或原生组件验收。MockBukkit发布26.3模型后应移除独立26.2运行API并统一测试运行环境。

## 部署步骤

1. 使用旧插件结束正在进行的对局，完成玩家状态恢复，正常停服。不要在升级时删除待恢复快照。
2. 备份整个服务器目录，包括世界、玩家数据和plugins/TheMazeRunner。Minecraft数据升级后，回退应恢复完整备份，不要把升级后的世界直接交给旧版服务端。
3. 准备JDK25和Paper26.3；在JDK25环境执行mvn clean verify，替换plugins内旧插件JAR。避免重复保留不同版本的同一插件。
4. 启动后执行/maze validate，再进行双客户端完整局、重连/死亡/恢复及重启验收。已有地图检查大门碰撞、Griever寻路和定位栏设置。详细验收条目见VALIDATION.md。
5. Simple Voice Chat是可选项，安装时须选其明确支持26.3的服务端版本；本项目保留原有2.5.0公开API接口，未声称验证了其真实26.3发行包。Purpur26.3兼容也需单独实测。

本轮没有运行Minecraft服务端或连接客户端；最终自动化结果和JAR摘要以[验证记录](VALIDATION.md)为准。
