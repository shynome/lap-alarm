# Looply 设计文档

循环倒计时。一键把「接下来 N 分钟」交给系统时钟，锁屏与通知栏都能触发。

- 包名 / applicationId：`looply.shynome.com`
- 语言：Kotlin + Jetpack Compose（Material 3）
- minSdk 26 ｜ targetSdk 34 ｜ compileSdk 35
- 目标机型：小米 HyperOS（实测 24115RA8EC / Android 16）

---

## 1. 需求

1. 反复使用同一批时间档位（默认 5 / 15 / 25 / 45 分钟，覆盖微休息到深度专注），可自定义增删。
2. 通知栏与锁屏都能快速启动一轮。
3. 抗 MIUI 杀后台。
4. 不需要自动循环，每一轮由用户手动触发。
5. 不需要「暂停 / 恢复」，只需要启动与关闭。

---

## 2. 核心决策：把倒计时交给系统时钟

用 `AlarmClock.ACTION_SET_TIMER` 把计时整件事交给系统时钟应用：

```kotlin
Intent(AlarmClock.ACTION_SET_TIMER).apply {
    putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)   // 秒
    putExtra(AlarmClock.EXTRA_SKIP_UI, true)          // 不打开时钟界面
    putExtra(AlarmClock.EXTRA_MESSAGE, "Looply")
}
```

| 环节 | 负责方 |
|---|---|
| 计时 | 系统时钟（前台服务） |
| 响铃 | 系统时钟（系统铃声 / 全屏 / 锁屏） |
| 剩余时间展示 | 状态栏胶囊 + 系统倒计时通知 |
| 关闭 | 系统胶囊 / 系统通知上的 ✕ |

应用本身不持有任何计时状态，进程被清理也不影响。

### 2.1 为什么不是自建 AlarmManager

早期版本用 `AlarmManager.setAlarmClock()` 自建闹钟，在 HyperOS 上遇到一连串问题：

- 进程被清理后，响铃通知在锁屏被「隐藏通知内容」吞掉；
- `fullScreenIntent` 被系统拒绝（`FSI_REQUESTED_BUT_DENIED`），全屏页弹不出来；
- 响铃不亮屏，用户完全察觉不到；
- 为补救需要引入 `MediaSession` 伪装媒体卡、`WakeLock` 强制亮屏等一堆补丁。

结论：锁屏可控性太差，不该自己实现闹钟。

### 2.2 关闭倒计时的能力边界（实测）

| Action | 小米时钟是否实现 | 说明 |
|---|---|---|
| `SET_TIMER` | ✅ `HandleSetAlarmActivity` | 可带 `EXTRA_SKIP_UI` 静默启动 |
| `DISMISS_TIMER` | ❌ **No activities found** | 该 ROM 没实现；AOSP 语义也只清「已过期」的计时器 |
| `STOP_WATCH` | ⚠️ 是「秒表」快捷方式 | 与关闭倒计时无关 |
| `SHOW_TIMERS` | ✅ | 打开时钟的倒计时页，用户可手动关闭 |

因此**程序化关闭系统倒计时不可行**，只能引导用户去点系统自己的 ✕。

---

## 3. 启动入口

| 入口 | 实现 | 特点 |
|---|---|---|
| app 主按钮 | `startActivity(timerIntent)` | 选好档位后启动，每次都是新一轮 |
| 通知栏普通通知 | `PendingIntent.getActivity(timerIntent)` | **永远可见**，不受其他媒体应用影响 |
| 锁屏 / 媒体区媒体卡 | `MediaSession` + `MediaStyle` | 锁屏唯一稳定入口，▶ 永远表示「开始」 |
| app / 通知「关闭倒计时」 | `PendingIntent.getActivity(SHOW_TIMERS)` | 跳到时钟倒计时页 |

`PendingIntent` 每次刷新都用 `FLAG_UPDATE_CURRENT` 重建，保证档位改动后立即生效。

### 3.1 媒体卡的暂停/播放语义

只发**一条** `MediaStyle` 通知，中间的键有两种形态：

| 当前状态 | 中间键 | 点击后 |
|---|---|---|
| 正在计时 | ⏸「暂停」 | **不真的停掉系统倒计时**，只把卡片切回 ▶，并显示「点播放重新开始」 |
| 已暂停 / 未开始 | ▶「播放」 | 由中转页启动一轮**全新的**倒计时 |

⏸ 之所以不真暂停：系统时钟没有暂停 API，第三方只能启动不能控制。
这里的「暂停」只是一个显示开关，作用是让用户二次确认，避免误触直接重置。

### 3.1.1 PendingIntent 必须用不同的 requestCode

播放按钮用 `getActivity`、暂停按钮用 `getBroadcast`，两者**不能共用同一个 requestCode**。
共用时系统可能让两个 PendingIntent 互相覆盖，表现是「第一次点击有效，之后点暂停没反应」。

现在固定为：播放 `102`、暂停 `101`、上一个档位 `11`、下一个档位 `12`、正文 `42`。

### 3.2 为什么要一个透明中转页

「播放」需要同时做两件事：启动系统倒计时、记录本地状态并刷新通知。

- 从 `BroadcastReceiver` 直接 `startActivity` 会被 Android 10+ 的 BAL 限制拦截；
- 而由通知 `PendingIntent` 拉起的 Activity 处于前台，可以正常 `startActivity`。

所以用 `LaunchTimerActivity`（`Theme.Translucent.NoTitleBar`、`noHistory`）做中转：
记录启动时刻 → 刷新通知 → 拉起时钟 → `finish()`。用户看不到这个页面。

### 3.3 为什么不再发两条通知

早期尝试过「普通通知 + MediaStyle」双通知，用于规避「其他媒体应用占用媒体区导致卡片消失」。
但这会让通知栏出现两条相似的条目，体验冗余，已改回单条。

代价：当别的应用正在播放音视频时，Looply 的媒体卡会被系统让位（媒体区只保留一个会话），
此时需要下拉通知栏才能找到。

### 3.4 强制「从头计时」

AOSP 文档写明：如果存在「参数完全相同且未被使用」的计时器，系统时钟**可以复用**它，
导致再点一次播放时剩余时间不重置。

解决：在 `EXTRA_MESSAGE` 末尾追加不可见的零宽字符（`\u200B`），让每次参数都不同，
强制新建计时器。零宽字符在界面上不占宽度、完全看不见。

验证：媒体卡 `00:30 / 01:00` 与系统倒计时 `00:31` 吻合，且系统内仅一个计时器。

---

## 4. 组件

```
looply.shynome.com
├── LooplyApp               Application：初始化 Store 与通知渠道
├── MainActivity            Compose 主界面：档位选择 + 启动 / 关闭
├── core/
│   ├── Store.kt            仅保存档位配置（无计时状态）
│   ├── TimerLauncher.kt    可用性检查 + 构造 SET_TIMER / SHOW_TIMERS Intent
│   ├── MediaPlayback.kt    MediaSession：锁屏卡片的三键 ↔ 业务动作
│   ├── Notifications.kt    常驻入口通知（MediaStyle）
│   └── Looply.kt           业务门面
├── receiver/
│   └── ControlReceiver.kt  通知里的「切档位」按钮
└── ui/                     Compose 主题与页面
```

### 4.1 状态

| key | 类型 | 说明 |
|---|---|---|
| `interval` | Int | 当前档位（分钟） |
| `presets` | String | 档位列表，逗号分隔，升序 |

---

## 5. 权限与 manifest

| 声明 | 用途 |
|---|---|
| `POST_NOTIFICATIONS` | 常驻入口通知（Android 13+） |
| `com.android.alarm.permission.SET_ALARM` | 调用 `SET_TIMER` 必需（normal 级，安装即授予） |
| `<queries><intent SET_TIMER />` | Android 11+ 包可见性，否则 `resolveActivity` 恒为 null |

缺少 `SET_ALARM` 时系统抛 `Permission Denial: ... requires com.android.alarm.permission.SET_ALARM`，
`startActivity` 静默失败。缺少 `<queries>` 时界面会误报「没有系统时钟」。

---

## 6. MIUI 特有的坑

### 6.1 链式启动拦截

首次从本应用跳转时钟会弹出：

> 启动应用 — Looply 想要打开 时钟，是否允许？ [本次允许] [始终允许] [拒绝]

这是 `com.miui.securitycenter/.wakepath.ConfirmStartActivity`。
选「始终允许」后不再提示。属系统行为，应用无法绕过，只能引导用户允许。

### 6.2 建议的系统设置

- 允许「自启动」，省电策略设为「无限制」（保证通知不被清理）
- 建议保留链式启动的「始终允许」，否则每次启动都会弹确认

---

## 7. 验证记录（Xiaomi 24115RA8EC / HyperOS, Android 16）

- `SET_TIMER` + `EXTRA_SKIP_UI=true`：倒计时启动成功，界面**不跳转**（焦点仍在 Looply）✅
- 状态栏出现倒计时胶囊 `⏳ 00:54` ✅
- `DISMISS_TIMER`：`No activities found`，确认不可用 ❌
- `STOP_WATCH`：`ShortcutTrampolineActivity`，实际打开秒表 ❌
- `SHOW_TIMERS`：打开时钟倒计时页 ✅
- MIUI 链式启动确认框：选「始终允许」后顺利启动 ✅

---

## 8. 双引擎：系统时钟 / 自建计时

### 8.1 为什么必须回退

第 2.1 节判断「自建闹钟锁屏可控性差」，但系统时钟方案后来在 HyperOS 上撞到更硬的墙：
**媒体卡上的 ▶ 在应用处于后台时无法拉起时钟**。

实测证据（`com.miui.home` 是当前前台，`com.android.deskclock` 是目标，uid 对不上说明 MIUI 是拿「前台是谁」做判定）：

```
D/ActivityStarterImpl: MIUILOG- Permission Denied Activity :
  Intent { act=android.intent.action.SET_TIMER ... cmp=com.android.deskclock/.HandleSetAlarmActivity }
  pkg : looply.shynome.com  uid : 10317  tuid : 10136     // 10136 = com.miui.home
```

排雷记录：

| 尝试 | 结果 |
|---|---|
| `SYSTEM_ALERT_WINDOW`（Android BAL 豁免） | Android 层已放行 `BAL_ALLOW_SAW_PERMISSION`，MIUI 再加一层 |
| 系统「链式启动」开关 | 已开启，仍被拒 |
| 改成前台服务提升进程优先级 | 无效，日志不变 |
| `ActivityOptions.setPendingIntentBackgroundStartMode` | 被系统丢弃：`Resetting option ... because this option is meant for the pending intent SENDER` |
| `cmd appops` | 只有 `START_FOREGROUND`，无 MIUI 相关开关 |

根因：媒体卡按钮走 **MediaSession 进程内回调**，系统不代为发起跳转，必须应用自己 `startActivity`；
而 MIUI 在 AOSP 之上对「后台应用拉起其他应用」硬拦截，第三方无法绕过。

**结论**：只要需要后台一键启动，就不能把动作委托给另一个应用。

### 8.2 双引擎

保留系统时钟实现，新增自建引擎，用 `engine` 状态切换，默认走自建。

```kotlin
enum class Engine { BUILTIN, SYSTEM_CLOCK }
```

| 引擎 | 计时方 | 后台一键启动 | 响铃表现 |
|---|---|---|---|
| `BUILTIN` | 本应用 `AlarmManager` | ✅ 不跨应用跳转 | 通知 + 铃声 + 振动 |
| `SYSTEM_CLOCK` | 小米时钟 | ❌ 被 MIUI 拦 | 系统时钟原生全屏 |

### 8.3 自建引擎

```
开始 ──► AlarmManager.setAlarmClock(now + N 分钟)
              │
              ▼ 到点
        AlarmReceiver ──► RingService.startForeground()
                              │
                              ├── 循环铃声（RingtoneManager TYPE_ALARM）
                              ├── 振动（Vibrator，波形循环）
                              ├── WakeLock：点亮屏幕 + 保持 CPU
                              └── 前台通知：PUBLIC + 高优先级，「关闭」按钮
```

- **循环语义**：手动重复。一轮响铃被关闭后即结束，不自动开始下一轮。
- **暂停**：自建引擎是**真暂停**（取消闹钟 + 记录剩余毫秒），恢复时按剩余时间重设。
  系统时钟引擎仍是假暂停（无法取消系统计时器）。
- **关闭**：`AlarmManager.cancel` + 停止服务 + 清状态。
- **响铃保护**：最长 5 分钟后自动停止，避免无人看管时无限响。

### 8.4 权限变化

| 声明 | 用途 |
|---|---|
| `SCHEDULE_EXACT_ALARM` | Android 12+ 精确闹钟 |
| `USE_EXACT_ALARM` | Android 13+ 闹钟类应用免申请精确闹钟 |
| `VIBRATE` | 响铃振动 |
| `WAKE_LOCK` | 响铃点亮屏幕、保持 CPU |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | 响铃期间的前台服务 |

### 8.5 取舍

自建引擎的响铃界面**不如系统时钟精致**（不做全屏页：`fullScreenIntent` 在本机实测被拒
`FSI_REQUESTED_BUT_DENIED`，锁屏悬浮窗也不可靠）。换取的是后台完全可控。

用户若更看重响铃观感，可在设置里切回系统时钟引擎——代价是必须先把应用切到前台。

