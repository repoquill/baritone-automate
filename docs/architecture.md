# Fabric 客户端模组架构方案（MC 26.3 + Baritone）

目标：客户端模组，另装 Baritone；提供操作/设置 UI；状态机编排 Baritone 完成「进末地并打完龙」演示级通关。不打包、不重写寻路。

**范围（产品已确认）**：运行环境按 **单机 + 局域网集成服（Open to LAN）** 设计；**不做**公共多人服反作弊适配、远端操控。其余：屠龙通关、Baritone 另装、MC 26.3 Fabric。

---

## 1. 版本基线

| 项 | 锁定 | 说明 |
|---|---|---|
| Minecraft | **26.3** | 与 Baritone `26.3` 分支对齐；**勿跟默认 1.21.4 分支** |
| Fabric API | **0.163.0+26.3** | 本模组侧 |
| Fabric Loader | **≥0.19.5**（工程可写 0.19.5；若本机已用 0.20.0 亦可） | Baritone 1.20.0 工程写 `fabricloader=0.19.5`；与之对齐即可 |
| Baritone | **源码分支 26.3 → 自编译**（对齐 mod **1.20.0**） | 官方源：[cabaletta/baritone@26.3](https://github.com/cabaletta/baritone/tree/26.3)；**默认不下载**预编译 release jar；安全分析通过后再编译 |
| Loom | **1.17+**（`net.fabricmc.fabric-loom`） | Gradle **9.6.x**；Java **25** |
| 许可证 | Baritone **LGPL-3.0** | 另装、不打包进本模组 jar，合规路径清晰 |

构建（26.x）：`implementation` 替代 `modImplementation`；无 Yarn/remap。Baritone **无官方 Maven**，且本项目约定：**不拉取预编译 jar**；在安全分析通过后，从 `26.3` 分支自行编译，将产物纳入 **本地 Maven（`mavenLocal`）或 `libs/`**，本模组以 `compileOnly`/`flatDir`/`files` 引用；**不 nest** 进发布包。玩家运行时仍 **另装** 自编译或官方兼容的 Baritone。`fabric.mod.json`：`"baritone": "*"`，`environment: "client"`。

---

## 2. 总体边界（已收紧）

```
[玩家输入/UI/热键] → [本模组 Client]
                         ├─ BaritoneGateway ──→ [另装 Baritone]（Process / Settings / CommandManager）
                         ├─ RunController（通关状态机，只编排 Gateway + inventory + combat）
                         └─ CombatController（炸水晶/屠龙；不经 Baritone）
```

| 维度 | 决策 |
|---|---|
| 环境 | `environment: client`；无独立服务端模组、无自定义玩法同步包、无远端 API |
| 目标运行 | **单机**；**局域网「对局域网开放」集成服**（同一进程里的 Integrated Server） |
| 非目标（非 P0） | 公共/专用多人服、反作弊绕过、跨客户端遥控、服务器端插件协同 |
| 网络 | 不新增 C2S 控制包；全部动作走原版客户端输入 + Baritone 本地 API。局域网下行为与单机同属本机客户端驱动 |
| 职责 | 本模组 = UI + 编排 + 轻量战斗；Baritone = 寻路类 Process；**无高层通关 API，必须组合 Process** |

---

## 3. 模块划分

| 模块 | 职责 | 谁实现 |
|---|---|---|
| `bootstrap` | Client 入口、Baritone 探测、KeyBinding、生命周期 | 后端 |
| `bridge` | 唯一 Baritone 封装（§4） | **后端** |
| `ui.control` | 暂停/取消/强取；goal/goto/thisway；方块；mine；explore；follow；路点；状态 | **前端** |
| `ui.settings` | 全量+搜索、类型编辑、重置、持久化、常用快捷 | **前端** |
| `hud` | ETA/进程/阶段/Baritone 状态 | **前端** |
| `automate` | 通关状态机、阶段、取消/暂停、失败恢复 | **后端** |
| `combat` | 水晶 + 简易屠龙；可演示、可取消 | **后端** |
| `inventory`（薄） | 合成/熔炼/用物等 Process 覆盖不到的步骤 | **后端**；P0 最小集 |

---

## 4. Baritone 封装层（`BaritoneGateway`）

**原则**：UI / 状态机只依赖 Gateway；禁止散落调 Baritone。

### 4.1 官方入口（实现侧锚定）

- 文档：[Javadocs](https://baritone.leijurv.com/)
- 获取实例：`BaritoneAPI.getProvider().getPrimaryBaritone()` → `IBaritone`
- 设置：`IBaritone` / API 侧 **Settings**（全量读写供 Settings UI）
- 能力：**CustomGoal**、**Mine**、**Explore**、**Follow**、**Farm** 等 **Process**；另 **CommandManager.execute** 可走命令字符串（UI「高级/调试」或与聊天命令对齐时用）
- **无通关级 API** → `RunController` 必须组合多个 Process + 本模组 inventory/combat

### 4.2 Gateway 能力分组

1. **进程控制**：pause / resume / cancel / forceCancel（取消当前 Process / 强取）
2. **目标**：CustomGoal + path；goto；thisway（可 Process 或 `CommandManager.execute`）
3. **Process**：mine / explore / follow（+ 按需 farm）；封装 start + 完成/失败观测
4. **路点**：对齐 Baritone 路点模型（list/save/goto/delete）
5. **Settings**：getAll / get / set / reset；暴露类型元数据给 UI
6. **观测**：当前 Process、是否 pathing、目标摘要；ETA 用段进度+启发式（标注估算）
7. **可用性**：`isBaritonePresent()`；`fabric.mod.json` 用 `suggests.baritone`，缺模组仍加载并走 Absent；缺失则 UI 提示另装

### 4.3 依赖方式（落地 · 源码构建优先）

**默认路径（产品要求）**

1. 安全分析通过后，克隆 [cabaletta/baritone](https://github.com/cabaletta/baritone) 并检出 **`26.3`**（不要跟默认 1.21.4）。
2. 按该分支工程（mc=26.3，mod=1.20.0，fabricloader=0.19.5，java=25）**本地编译**，产出 `baritone-api-fabric`（及运行所需的 fabric 发行物）。
3. 将 api 产物发布到 **`mavenLocal`**，或复制到本仓库 **`libs/`**（仅作编译/开发 classpath）。
4. 本模组：`mavenLocal()` + 坐标，或 `flatDir`/`files` 指向自编译 api；**发布 jar 不打包 Baritone**。
5. 运行时：玩家/开发者安装**同一次源码构建的 Baritone fabric 模组**（或经确认的等价构建）。`fabric.mod.json` 把 `baritone` 放在 `suggests`，不放进 `depends`，这样没装时本模组仍能加载并报告 `isBaritonePresent()==false`。

**明确不做**：把「下载 GitHub Release 预编译 `baritone-api-fabric-*.jar`」作为默认/推荐依赖获取方式（浏览器曾报不安全；待安全结论后再议是否允许作备选）。

```
# 开发机示例（示意）
git clone …/baritone && git checkout 26.3 && ./gradlew …   # 产出 api + fabric 模组
# → ~/.m2/... 或本仓 libs/baritone-api-fabric-*.jar（自编译）
# 本模组 repositories { mavenLocal() } 或 flatDir { dirs 'libs' }
```

---

## 5. UI 技术选型

| 界面 | 建议 |
|---|---|
| 操作台 + 通关 | **原生 Screen** + 热键（必有） |
| Settings | **原生可搜索列表**；Cloth Config 仅 optional（视 26.3 可用性） |
| HUD | 客户端 Hud 回调：阶段 / Process / ETA / 战斗中 |
| 入口 | Mod Menu optional |

局域网/单机无额外「联机 UI」；不提供「远端授权/旁路反作弊」相关界面。

---

## 6. 通关状态机与战斗

### 6.1 RunController

状态：`IDLE | RUNNING | PAUSED | COMBAT | FAILED | CANCELLED | DONE`。  
只编排 Gateway Process + inventory + Combat。取消 → 停战斗 → forceCancel → IDLE。

### 6.2 阶段（P0 演示路径）

`CHECK` → `EARLY_WOOD` → `IRON` → `DIAMOND`(可跳过) → `NETHER_PORTAL` → `BLAZE_PEARL` → `STRONGHOLD` → `ENTER_END` → `END_APPROACH` → `CRYSTALS`(Combat) → `DRAGON`(Combat) → `DONE`

每阶段：enter → 下发 Process/步骤 → tick 完成/超时/卡住 → 重试或 FAILED（可暂停让玩家手动后「继续」）。P0 建议 **允许跳过阶段**。

### 6.3 Combat 边界

- **做**：末地水晶（Gateway 靠近 + 客户端打碎）；简易屠龙（停栖近战 / 飞行拉弓）；全程可取消；低血量可暂停提示。
- **不做**：完整战斗 AI、床爆、通用 PvP。
- 分界：走路/挖障 → Gateway；对实体伤害/碎水晶 → Combat。

---

## 7. 仓库与实现顺序

### 7.1 仓库

建议 **Origin `new_repo`**。

```
<repo>/
  libs/                                # 可选：放置「自编译」api jar（勿提交预编译来路不明的包；勿进发布 jar）
  gradle.properties                    # mc=26.3, java=25, loader, fabric-api, loom
  src/client/java/<pkg>/{bridge,ui,hud,automate,combat,inventory}/
  src/main/resources/fabric.mod.json   # environment:client; suggests.baritone
  docs/architecture.md
  docs/baritone-build.md               # 从 26.3 源码编译并安装到 mavenLocal/libs 的步骤
  README.md                            # 单机/局域网；Baritone 源码自编译另装；LGPL 说明
```

### 7.2 顺序

1. 骨架（26.3 + **自编译** Baritone api 接入 mavenLocal/libs + 探测）；阻塞项：安全分析通过后再编译  
2. Gateway 最小集（IBaritone + Mine/Explore/Follow/CustomGoal + cancel + Settings 读写）  
3. 操作台 Screen  
4. Settings + HUD  
5. RunController + 前几阶段  
6. inventory 最小集  
7. 末地 + Combat  
8. 一键通关联调  

**分派**：后端 = bridge / automate / combat / inventory / 骨架；前端 = ui.* / hud / 热键。

---

## 8. 风险与开放问题

**风险（相对原「公共服」已降级）**

- 局域网集成服仍可能有延迟/区块加载导致 Process 卡住 → 超时与「暂停继续」必需。
- 合成熔炼等仍靠 `inventory` 薄层（Baritone 无通关 API）。
- 下界/要塞/屠龙失败率 → 跳过阶段 + 暂停手操为 P0 验收底线。
- 无官方 Maven → CI/文档以 **26.3 源码编译** 为准（缓存自建产物或 `mavenLocal`）；**默认不下载** release 预编译包。
- LGPL：另装不打包即可；文档写明依赖 Baritone。

**已关闭**

- 公共多人服反作弊 / 远端操控 —— **非 P0，方案不覆盖**。

**仍开放**

1. 通关默认「可跳过阶段」是否拍板？  
2. 难度（和平 vs 生存默认）？  
3. 地形破坏类 Settings 预设？  
4. 模组正式 id / 显示名？  

---

## 9. 修订记录

- **v2**：运行环境收紧为单机 + 局域网集成服；并入 Baritone 26.3 分支、API 入口与 Process 模型、`depends "*"`、LGPL/Javadocs。
- **v3**：依赖获取改为 **26.3 源码自编译 → mavenLocal/libs**；去掉「下载 release 预编译 jar」作为默认路径；前后端开工仍待安全分析与用户确认。
