# 从 Baritone `26.3` 源码编译 API

本仓库**不**下载 GitHub Release 预编译 jar。`libs/baritone-api-fabric-1.20.0.jar` 是本机从源码分支 `26.3` 自编译的 API，只进编译 classpath。

## 已纳入的产物

| 项 | 值 |
|---|---|
| 上游 | https://github.com/cabaletta/baritone |
| 分支 | `26.3` |
| Commit | `25111daedf1d59e6a8dfb5a3e61885cdb8d953df`（短 `25111da`） |
| 构建 | `./gradlew :fabric:build` |
| 文件 | `dist/baritone-api-fabric-1.20.0.jar` |
| sha256 | `8787294cc5835110f6e19b82840d7fc5df99d9ebce7631a44e5a13128984a899` |
| 角色 | **仅** `compileOnly`。发布 jar 不 nest Baritone |

同一次构建还会产出运行用的 `baritone-fabric-1.20.0.jar`（以及 standalone / unoptimized）。那些**不要**放进本模组 jar，玩家单独安装 fabric 模组。

校验：

```bash
sha256sum libs/baritone-api-fabric-1.20.0.jar
# 8787294cc5835110f6e19b82840d7fc5df99d9ebce7631a44e5a13128984a899
```

## 自己重编

需要 Java 25（带 jmods 的发行版；本次 Baritone 构建用的是 BellSoft Liberica 25.0.4.1，Temurin 25.0.4.1 缺少 jmods，ProGuard 会失败）。

```bash
git clone https://github.com/cabaletta/baritone
cd baritone
git checkout 26.3   # 不要跟默认的 1.21.4
./gradlew :fabric:build --no-daemon
cp dist/baritone-api-fabric-1.20.0.jar /path/to/baritone-automate/libs/
```

Baritone 工程基线（与其 `gradle.properties` 对齐）：Minecraft `26.3`，模组版本 `1.20.0`，Fabric Loader `0.19.5`，Java 25。

本模组 `build.gradle` 用：

```gradle
repositories {
    flatDir { dirs 'libs' }
}
dependencies {
    compileOnly files('libs/baritone-api-fabric-1.20.0.jar')
}
```

`compileOnly` 不会把 Baritone 打进 `baritone-automate-*.jar`。`./gradlew build` 里的 `verifyBaritoneNotNested` 会再查一次 jar 条目。

## 运行时

开发或玩家都要**另装**同一次源码构建的 Baritone Fabric 模组（`baritone-fabric-1.20.0.jar`）。`fabric.mod.json` 写了 `"baritone": "*"`。缺模组时加载器不会加载本模组；即便如此，入口仍用 `Class.forName` 探测，避免直接链到 API 类时在缺失场景下硬崩。
