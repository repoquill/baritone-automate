# Baritone Automate

Minecraft **26.3** Fabric **客户端**模组。另装 Baritone，由本模组编排寻路 Process、薄物品栏和简易末地战斗，演示「进末地并打龙」的阶段骨架。

本仓库这一版只做**后端**：`bootstrap`、`bridge`、`automate`、`combat`、`inventory`。操作台、Settings、HUD、热键打开的 Screen 由前端接手，对应包 `dev.baritoneautomate.ui` 和 `dev.baritoneautomate.hud` 是空的。

范围是单机和局域网「对局域网开放」。没有公共服反作弊、没有远端 API、没有自定义玩法包。

## 版本

| 项 | 锁定 |
|---|---|
| Minecraft | 26.3 |
| Java | 25 |
| Gradle | 9.7.1 |
| Loom | 1.18.4（`net.fabricmc.fabric-loom`，无 Yarn、无 remap） |
| Fabric Loader | 0.19.5（与 Baritone `depends` 对齐；0.20+ 只要仍支持 26.3 即可） |
| Fabric API | 0.163.0+26.3 |
| Baritone API | `libs/baritone-api-fabric-1.20.0.jar`，分支 `26.3`，commit `25111da`，`compileOnly` |

26.x 依赖用 `implementation` / `compileOnly`，不用 `modImplementation`。

架构原文写 Gradle 9.6.x。当前能编 26.3 的 Loom 1.18.x 在 Gradle Module Metadata 里声明 `org.gradle.plugin.api-version=9.7.0`，9.6.1 会在配置阶段直接解析失败。发布物里也没有声明 9.6.1 的 Loom。因此包装器用 **9.7.1**，与 Fabric 官方 `fabric-example-mod` 的 26.3 分支一致，Loom 固定 **1.18.4**（不跟 `1.18-SNAPSHOT` 浮动）。

## 构建

需要 JDK 25，并且第一次构建要能访问 Fabric / Mojang / Gradle 的 Maven。

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew build
```

产物：`build/libs/baritone-automate-0.1.0.jar`。里面没有 Baritone 类。运行前把自编译的 `baritone-fabric-1.20.0.jar` 放进 mods 目录。编译 API 的步骤见 [docs/baritone-build.md](docs/baritone-build.md)。

Baritone 是 LGPL-3.0，本模组源码是 MIT。另装、不打包，见 `NOTICE`。

## 包与入口

- 包名：`dev.baritoneautomate`
- mod id：`baritone-automate`
- 显示名：Baritone Automate
- 客户端入口：`dev.baritoneautomate.bootstrap.BaritoneAutomateClient`

前端只拿这些访问器，不要 `import baritone.api`：

- `BaritoneAutomateClient.gateway()`
- `BaritoneAutomateClient.runs()`
- `BaritoneAutomateClient.combat()`
- `BaritoneAutomateClient.inventory()`
- `BaritoneAutomateClient.keys().setOpenControlsHook(...)` 接操作台 Screen

热键已注册（控件里可改）：`K` 打开操作台（仅回调）、`]` 暂停/继续、`\` 取消、`End` 强制取消。取消走同一条序列：停战斗、`forceCancel`、状态 `CANCELLED`。

## 已完成的后端

- **bootstrap**：客户端入口、Baritone 类探测、缺失时降级到 `AbsentBaritoneGateway`、客户端 tick、热键 stub。
- **bridge**：`BaritoneGateway` 是唯一封装。pause 用我们自己的 `REQUEST_PAUSE` Process（Baritone 的 `#pause` 命令是私有的，注释写明不要用反射去调）。另有 resume、cancel、forceCancel、CustomGoal、goto、thisway、mine、explore、follow、farm、路点、Settings 读写和类型元数据、pathing / Process 观测。
- **automate**：`IDLE | RUNNING | PAUSED | COMBAT | FAILED | CANCELLED | DONE`，阶段 `CHECK → EARLY_WOOD → IRON → DIAMOND → NETHER_PORTAL → BLAZE_PEARL → STRONGHOLD → ENTER_END → END_APPROACH → CRYSTALS → DRAGON → DONE`。前段会真下发 mine。后段烈焰棒/珍珠、要塞、进末地是可跳过的 stub，末地接近会 `gotoXZ(0, 0)`，水晶和龙交给 combat。有跳过、暂停、超时（默认重试一次再失败）、失败后 `continueRun()`。
- **combat**：末地水晶接近后攻击；龙低速贴地近战，否则热键栏拉弓。低血量回调会暂停 Run，不判失败。可取消。
- **inventory**：计数、热键栏选物。`craft` / `smelt` 返回 `UNSUPPORTED`。

## 开放问题的默认

1. **允许跳过阶段**，`skipPhase()` 对每个阶段都成立。钻石**不会**自动跳过；`RunController.setSkipDiamond(true)` 才会跳过。
2. **难度**按生存向末地战写。和平难度没有龙时，屠龙阶段会失败，可以跳过。不检测游戏难度。
3. **不改 Baritone 破坏方块等预设**。地形相关设置留给 Settings UI，或直接 `gateway.setSetting`。
4. mod id / 显示名用上面这组，不再待定。
5. 用户取消后停在 **`CANCELLED`**，方便 HUD 读到终态。`reset()` 回到 `IDLE`；`start()` 也可以从终态直接开新一局。这和架构里「取消 → IDLE」的差别记在这里：终态保留，空闲要再走一步。
6. 下界门只挖黑曜石，**不点火**。烈焰人、扔眼、要塞寻路没有实现。熔炼和合成是占位。

架构全文在 [docs/architecture.md](docs/architecture.md)。
