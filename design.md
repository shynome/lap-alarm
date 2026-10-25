# Looply 设计文档

循环倒计时。一键把「接下来 N 分钟」变成一轮倒计时，锁屏与通知栏都能控制。

- 包名 / applicationId：`looply.shynome.com`
- 语言：Kotlin + Jetpack Compose（Material 3）
- minSdk 26 ｜ targetSdk 34 ｜ compileSdk 35
- 目标机型：小米 HyperOS（实测 24115RA8EC / Android 16）

---

## 1. 需求

1. 反复使用同一批时间档位（默认 5 / 15 / 25 / 45 分钟，覆盖微休息到深度专注），可自定义增删。
2. 通知栏与锁屏都能快速启动一轮。
3. 抗 MIUI 杀后台。
4. 每轮由用户手动触发，不自动循环。
5. 支持暂停 / 继续 / 关闭。

---

## 2. 核心决策：自己计时

用 `AlarmManager.setAlarmClock()` 登记系统级闹钟，到点由 `RingService` 前台服务响铃：

| 环节 | 负责方 |
|---|---|
| 计时 | `AlarmManager`（`RTC_WAKEUP`，精确闹钟） |
| 到点触发 | `AlarmReceiver` → `RingService` |
| 响铃 | `RingService`：铃声循环 + 振动 + 亮屏 |
| 剩余时间展示 | 主界面圆环 + 媒体卡 |
| 控制 | 媒体卡三键 + app 内按钮 |

`setAlarmClock` 是最高的闹钟优先级：会显示状态栏闹钟图标、不受省电模式与待机分组影响，
MIUI 也不会随意丢弃。实测登记结果：

```
RTC_WAKEUP tag=*walarm*:looply.action.ALARM_FIRE window=0
exactAllowReason=policy_permission
Alarm clock: triggerTime=...
idle-options=Bundle[{android.pendingIntent.backgroundActivityAllowed=2, ...}]
```

### 2.1 为什么不用系统时钟

早期版本把计时交给系统时钟（`ACTION_SET_TIMER`），响铃界面更精致，但在 HyperOS 上撞到硬墙：
**应用在后台时无法启动系统时钟的 Activity**。

```
D/ActivityStarterImpl: MIUILOG- Permission Denied Activity :
  Intent { act=android.intent.action.SET_TIMER ... cmp=com.android.deskclock/.HandleSetAlarmActivity }
  pkg : looply.shynome.com  uid : 10317  tuid : 10136     // 10136 = com.miui.home
```

排查过程：

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
改为自己计时后全程不跨应用，问题消失，连带不再需要 `SYSTEM_ALERT_WINDOW`。

---

## 3. 媒体卡

只发**一条** `MediaStyle` 通知（`ID_STATUS = 100`），同时承担通知栏与锁屏入口。

| 状态 | 标题 | 中间键 |
|---|---|---|
| 空闲 | `Looply · N 分钟` | ▶ 开始 |
| 计时中 | 同上 | ⏸ 暂停 |
| 暂停 | 同上 | ▶ 继续 |
| 响铃 | `⏰ 时间到 · N 分钟` | ⏹ 关闭 |

左右两键固定为切换档位。系统媒体卡右上角的 ✕ 走 `onStop()`，等价于关闭本轮，卡片保留。

### 3.1 进度条可拖动

`PlaybackState` 给出真实的 `position`（已过毫秒）与 `speed`（播放 1f / 暂停 0f），
系统据此自行推进进度条，不需要每秒刷新通知。

```kotlin
.setActions(... or PlaybackStateCompat.ACTION_SEEK_TO)
.setState(state, elapsedMs, if (playing) 1f else 0f)
```

拖动回调 `onSeekTo(elapsedMs)` → `Looply.seekTo()`：取消旧闹钟、按新剩余时间重设。
暂停中拖动只挪动冻结点，不会自己跑起来。

### 3.2 会话保活

用户点 ✕ 后系统会把会话置为非活跃并可能移除卡片。
因此每次刷新都重新 `isActive = true`，保证卡片始终作为常驻入口存在。

---

## 4. 组件

```
looply.shynome.com
├── LooplyApp               Application：初始化 Store 与通知渠道
├── MainActivity            Compose 主界面：档位选择 + 计时控制 + 权限引导
├── core/
│   ├── Store.kt            状态与持久化
│   ├── AlarmScheduler.kt   setAlarmClock 封装
│   ├── RingService.kt      响铃前台服务
│   ├── MediaPlayback.kt    MediaSession：锁屏卡片三键 ↔ 业务动作
│   ├── Notifications.kt    常驻通知（MediaStyle）
│   ├── Format.kt           剩余时间格式化
│   └── Looply.kt           业务门面
├── receiver/
│   ├── AlarmReceiver.kt    到点入口
│   ├── ControlReceiver.kt  通知/广播控制的按钮
│   └── MediaButtonRelay.kt 把媒体按键转给 MediaSession
└── ui/                     Compose 主题与页面
```

### 4.1 状态

| key | 类型 | 说明 |
|---|---|---|
| `interval` | Int | 当前选中档位（分钟） |
| `presets` | String | 档位列表，逗号分隔，升序 |
| `timer_started_at` | Long | 本轮起点；0 表示没有进行中的轮次 |
| `running_minutes` | Int | 本轮时长。与 `interval` 分开，切档位不会打乱当前轮 |
| `paused` | Boolean | 是否暂停 |
| `paused_remaining_ms` | Long | 暂停时冻结的剩余毫秒 |

### 4.2 暂停语义

自建计时下暂停是**真暂停**：取消系统闹钟 + 冻结剩余时间。
恢复时按剩余时间重设闹钟，并反推 `timer_started_at` 使 `endsAt()` 与之吻合。

---

## 5. 权限

| 声明 | 用途 |
|---|---|
| `POST_NOTIFICATIONS` | 媒体卡与常驻通知（Android 13+ 运行时权限） |
| `SCHEDULE_EXACT_ALARM` | Android 12+ 精确闹钟 |
| `USE_EXACT_ALARM` | Android 13+ 闹钟类应用免申请精确闹钟 |
| `VIBRATE` | 响铃振动 |
| `WAKE_LOCK` | 响铃时点亮屏幕并保持 CPU |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | 响铃期间的前台服务 |

### 5.1 通知权限按需申请

没有通知就没有媒体卡，但也不该在开屏时弹窗——用户还没表达意图之前容易被直接拒掉。
因此在点「开始」时才检查：

- 未授权 → 申请；授权后**自动继续启动**刚才那一轮
- 永久拒绝 → 系统不再弹框，Toast 提示并跳通知设置页
- 权限检测用 `checkSelfPermission`，**不能用 `areNotificationsEnabled()`**：
  MIUI 上后者会返回 `true` 而实际没有权限

---

## 6. MIUI 特有的坑

### 6.1 进程冻结（Greezer）

后台冻结状态下，广播会被缓存而非投递（用 adb 调试时尤其明显）：

```
W BroadcastQueue: Greezer Denial: sending Intent { act=looply.action.START ... }
  due to receiver ProcessRecord{...} need cached broadcast
```

不影响真实使用：媒体卡点击会让系统解冻进程；`setAlarmClock` 也能直接唤醒。

### 6.2 建议的系统设置

- 允许「自启动」，省电策略设为「无限制」
- 关闭对 Looply 通知的「隐藏通知内容」，否则锁屏看不到卡片

---

## 7. 验证记录（Xiaomi 24115RA8EC / HyperOS, Android 16）

- `setAlarmClock` 登记为系统级 `Alarm clock`，带唤醒 ✅
- 后台（桌面在前台）点媒体卡 ▶ 能直接开始新一轮 ✅
- 到点 `RingService` 以前台服务启动，铃声 + 振动 + 亮屏 ✅
- 媒体卡 ⏸ 为真暂停，⏹ 与 ✕ 都能结束本轮 ✅
- 进度条可拖动，剩余时间随之变化 ✅
- `fullScreenIntent` 在本机被拒（`FSI_REQUESTED_BUT_DENIED`），故不做全屏响铃页 ❌
