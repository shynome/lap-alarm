# Lap

循环倒计时。一键把「接下来 N 分钟」变成一轮倒计时，锁屏和通知栏都能直接控制。

- 包名：`lap.shynome.com`
- Kotlin + Jetpack Compose（Material 3）
- minSdk 26 ｜ targetSdk 36 ｜ compileSdk 36

---

## 特性

- **后台一键启动** —— 不打开应用，直接从锁屏 / 通知栏的媒体卡片开始新一轮
- **媒体卡控制** —— 播放开始、暂停（真暂停）、停止结束，进度条可拖动调整剩余时间
- **到点响铃** —— 循环播放系统闹钟铃声 + 振动 + 点亮屏幕，无人处理时 5 分钟后自动停止
- **档位管理** —— 默认 5 / 15 / 25 / 45 分钟，支持自定义增删
- **不自动循环** —— 每轮由用户手动触发

---

## 为什么自己计时

早期版本用 `AlarmClock.ACTION_SET_TIMER` 把计时交给系统时钟，响铃界面更精致，但在小米 HyperOS 上撞到硬墙：

**应用处于后台时无法启动系统时钟的 Activity。**

```
D/ActivityStarterImpl: MIUILOG- Permission Denied Activity :
  Intent { act=android.intent.action.SET_TIMER ... cmp=com.android.deskclock/.HandleSetAlarmActivity }
  pkg : lap.shynome.com  uid : 10317  tuid : 10136   // 10136 = com.miui.home
```

排查过的路，全部无效：

| 尝试 | 结果 |
|---|---|
| 授予 `SYSTEM_ALERT_WINDOW`（Android BAL 豁免） | Android 层已放行，MIUI 再加一层 |
| 系统「链式启动」开关 | 已开启，仍被拒 |
| 提前拉起前台服务提升进程优先级 | 无效 |
| `setPendingIntentBackgroundStartMode` | 被系统丢弃 |
| `cmd appops` 改 MIUI 策略 | 无可改项 |

根因：媒体卡按钮走 **MediaSession 进程内回调**，系统不代为发起跳转，必须应用自己 `startActivity`；而 MIUI 在 AOSP 之上对「后台应用拉起其他应用」做了硬拦截。

**结论**：只要需要后台一键启动，就不能把动作委托给另一个应用。改为自己计时后全程不跨应用，问题消失，连带不再需要悬浮窗权限。

---

## 实现要点

### 计时

`AlarmManager.setAlarmClock()` 登记系统级闹钟——最高优先级，会显示状态栏闹钟图标，不受省电模式和待机分组影响。到点由 `AlarmReceiver` 拉起 `RingService` 前台服务响铃。

### 媒体卡

只发**一条** `MediaStyle` 通知，同时承担通知栏与锁屏入口：

| 状态 | 中间键 |
|---|---|
| 空闲 | 播放（开始新一轮） |
| 计时中 | 暂停 |
| 暂停 | 播放（按剩余时间继续） |
| 响铃 | 停止（结束本轮） |

进度条给出真实的 `position` 与 `speed`，由系统自行推进，不需要每秒刷新通知；拖动通过 `ACTION_SEEK_TO` 回调重新登记闹钟。

### 暂停

自建计时下是**真暂停**：取消系统闹钟 + 冻结剩余时间；恢复时按剩余时间重设，并反推起点保证 `endsAt()` 吻合。

---

## 权限

| 权限 | 用途 |
|---|---|
| `POST_NOTIFICATIONS` | 媒体卡与常驻通知（Android 13+ 运行时权限） |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | 精确闹钟 |
| `VIBRATE` | 响铃振动 |
| `WAKE_LOCK` | 响铃时点亮屏幕 |
| `FOREGROUND_SERVICE` + `SYSTEM_EXEMPTED` | 响铃期间的前台服务（需持有精确闹钟权限） |

通知权限在点击「开始」时才申请，不在一打开应用就弹窗；授权后会自动继续启动刚才那一轮。

---

## 构建

需要 JDK 17 与 Android SDK。

```bash
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

---

## 已知限制

- **不做全屏响铃页**：`fullScreenIntent` 在目标机型上被系统拒绝（`FSI_REQUESTED_BUT_DENIED`），因此响铃表现为通知 + 铃声 + 亮屏
- **静音仍会响**：铃声走 `USAGE_ALARM`，媒体音量调到 0 也照样响，要静音需调「闹钟音量」或开启勿扰
- **切档位的行为随状态而变**：计时中切档位会作废旧一轮、按新时长重新开始；暂停中切档位只替换档位并保留冻结的剩余时间

---

## 文档

设计决策与排查记录见 [design.md](design.md)。
