# 更新日志

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循语义化版本。

## [0.1.5] - 2026-10-28

### 构建

- 升级 Android Gradle Plugin 8.6.0 → 9.0.1、Gradle 8.7 → 9.1.0，解决 Play Console 的
  「R8 配置可能导致内存用量增加和性能降低：请将 Android Gradle plugin 升级到 9.0 或更高版本」。
- 迁移到 AGP 内置 Kotlin（built-in Kotlin）：移除 `org.jetbrains.kotlin.android` 插件与
  `kotlinOptions`，Compose 编译器插件对齐到 AGP 依赖的 KGP 2.2.10。
- 移除 `android.suppressUnsupportedCompileSdk=36`：AGP 9 原生支持 compileSdk 36。

### 修复

- 删除 `values-night/colors.xml` 中无引用的 `ring_bg`：AGP 9 的 lint 会把它判为
  `MissingDefaultResource` 致命错误，导致 `bundleRelease`（即 Play 上传包）构建失败。

### 版本

- `versionCode` 6 → 7，`versionName` 0.1.4 → 0.1.5。

## [0.1.4] - 2026-10-28

### 修复

- 响铃时媒体卡改为显示暂停键（⏸）：此前响铃上报 `STATE_PAUSED`，锁屏显示的是 ▶，
  点一下只是关掉铃声，要再点一次才会重新开始，容易误触。
- 现在响铃期间上报 `STATE_PLAYING`（播放速度给 0，进度条不会被推进）：点 ⏸ 只关闭铃声
  并结束这一轮，卡片随即回到 ▶，再点 ▶ 才按当前档位重新开始。

### 构建

- `versionCode` 5 → 6，`versionName` 0.1.3 → 0.1.4。

## [0.1.3] - 2026-10-28

### 构建

- 开启 R8 混淆与资源压缩（`isMinifyEnabled` / `isShrinkResources`），修复 Play
  Console 报出的「DEX 代码优化程度未达阈值」——此前 release 包未混淆，混淆率
  仅 1%，低于 25% 门槛。
- 补充 ProGuard 规则：保留 `SourceFile` / `LineNumberTable` 并把源文件名归一化，
  混淆后崩溃堆栈仍可定位，同时减小包体。
- `versionCode` 4 → 5，`versionName` 0.1.2 → 0.1.3。

## [0.1.2] - 2026-10-28

### 修复

- 切换档位时按当前状态正确处理计时：
  - 计时中 / 响铃中切档位：旧一轮作废，新档位立即从头开始，时长立刻生效。
  - 暂停中切档位：只替换档位，暂停的那一轮继续冻结；切回原档位可接着走剩余时间。
- 删除正在运行或暂停中的档位，同时结束这一轮，避免留下失去归属的计时。
- 结束一轮时仅在确实处于响铃状态才停止 `RingService`，并合并为一次通知刷新。
- 响铃前台服务类型由 `specialUse` 改为 `systemExempted`：无需额外提交用途声明，
  并兼容精确闹钟权限被回收的情况——进入前台失败时静默退出，不再抛异常。

### 构建

- `versionCode` 3 → 4，`versionName` 0.1.1 → 0.1.2。

## [0.1.1] - 2026-10-25

> 未单独打 tag，内容包含在 `v0.1.2` 中。

### 变更

- 支持英文界面，英文为默认语言，中文仅在系统语言为中文时启用。
- 应用显示名调整为 `Lap`，界面标题跟随 `app_name` 资源。
- 重新设计图标（深青底 + 白色图形）。
- 移除 Gradle 签名配置，回归 Android Studio 打包流程。
- 补充 README。

### 构建

- `compileSdk` / `targetSdk` 35 → 36。
- `versionCode` 1 → 3，`versionName` 0.1.0 → 0.1.1。

## [0.1.0] - 2026-10-25

> 未单独打 tag，内容包含在 `v0.1.2` 中。

### 新增

- 循环倒计时：自建计时引擎替代系统时钟，后台一键启动不再跨应用跳转。
- 媒体卡控制：一条 `MediaStyle` 通知同时承担通知栏与锁屏入口，支持开始、暂停、
  停止与进度条拖动。
- 到点响铃：循环系统闹钟铃声 + 振动 + 点亮屏幕，无人处理时 5 分钟后自动停止。
- 档位管理：默认 5 / 15 / 25 / 45 分钟，支持自定义增删。
- 真暂停：取消系统闹钟并冻结剩余时间，恢复时按剩余时间重设。
- 精确闹钟：`AlarmManager.setAlarmClock()`，不受省电模式与待机分组影响。
