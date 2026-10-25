package looply.shynome.com.core

import android.content.Context
import looply.shynome.com.service.RingService

/**
 * 业务门面：档位维护与计时控制。
 *
 * 计时由本应用自己负责：[AlarmScheduler] 登记系统级闹钟，到点后 [RingService] 响铃。
 * 全程不涉及跨应用跳转，因此在 MIUI 等限制后台拉起其他应用的 ROM 上也能正常工作。
 */
object Looply {

    fun init(context: Context) {
        Store.init(context)
        Notifications.ensureChannels(context)
    }

    // ---------------------------------------------------------------- 计时控制

    /** 开始新一轮。返回 false 表示闹钟没能登记成功（通常是缺精确闹钟权限）。 */
    fun startTimer(context: Context, minutes: Int? = null): Boolean {
        Store.init(context)
        val interval = (minutes ?: Store.state.value.intervalMinutes)
            .coerceIn(AppState.MIN_MINUTES, AppState.MAX_MINUTES)
        val now = System.currentTimeMillis()
        if (!AlarmScheduler.schedule(context, now + interval * 60_000L)) return false
        Store.update {
            it.copy(
                intervalMinutes = interval,
                runningMinutes = interval,
                timerStartedAt = now,
                paused = false,
                pausedRemainingMs = 0L,
                ringing = false,
            )
        }
        refresh(context)
        return true
    }

    /** 暂停：取消系统闹钟并把剩余时间冻结下来。 */
    fun pauseTimer(context: Context) {
        Store.init(context)
        val state = Store.state.value
        if (!state.isTimerRunning() || state.paused) return
        val remaining = state.remainingMs()
        AlarmScheduler.cancel(context)
        Store.update { it.copy(paused = true, pausedRemainingMs = remaining) }
        refresh(context)
    }

    fun resumeTimer(context: Context) {
        Store.init(context)
        val state = Store.state.value
        if (!state.paused) return
        val remaining = state.pausedRemainingMs.coerceAtLeast(1_000L)
        val now = System.currentTimeMillis()
        if (!AlarmScheduler.schedule(context, now + remaining)) return
        // 反推起点，使 endsAt() 正好等于 now + remaining
        val startedAt = now + remaining - state.runningMinutes * 60_000L
        Store.update {
            it.copy(timerStartedAt = startedAt, paused = false, pausedRemainingMs = 0L)
        }
        refresh(context)
    }

    /** 彻底结束这一轮：取消闹钟、停掉响铃、清空状态。 */
    fun stopTimer(context: Context) {
        Store.init(context)
        AlarmScheduler.cancel(context)
        RingService.stop(context)
        Store.update {
            it.copy(paused = false, pausedRemainingMs = 0L, ringing = false, runningMinutes = 0)
        }
        refresh(context)
    }

    /**
     * 媒体卡进度条拖动。
     *
     * @param elapsedMs 本轮已经过去的时长（毫秒），即进度条当前所在位置。
     */
    fun seekTo(context: Context, elapsedMs: Long) {
        Store.init(context)
        val state = Store.state.value
        if (!state.isTimerRunning() || state.runningMinutes <= 0) return

        val total = state.runningMinutes * 60_000L
        val remaining = (total - elapsedMs).coerceIn(1_000L, total)
        if (state.paused) {
            // 暂停中拖动只挪动冻结点，不重新开始跑
            Store.update { it.copy(pausedRemainingMs = remaining) }
        } else {
            val now = System.currentTimeMillis()
            if (!AlarmScheduler.schedule(context, now + remaining)) return
            Store.update {
                it.copy(
                    timerStartedAt = now + remaining - total,
                    pausedRemainingMs = 0L,
                )
            }
        }
        refresh(context)
    }

    /** 到点了：标记响铃态，通知与媒体卡切到「关闭」形态。 */
    fun onAlarmFired(context: Context) {
        Store.init(context)
        Store.update { it.copy(ringing = true) }
        refresh(context)
    }

    /** 响铃结束（用户关闭，或服务因超时自行停止）。 */
    fun onRingStopped(context: Context) {
        Store.init(context)
        if (!Store.state.value.ringing) return
        Store.update {
            it.copy(
                ringing = false,
                paused = false,
                pausedRemainingMs = 0L,
                runningMinutes = 0,
            )
        }
        refresh(context)
    }

    // ---------------------------------------------------------------- 档位

    fun shiftPreset(context: Context, delta: Int) {
        Store.init(context)
        val current = Store.state.value
        val presets = current.presets
        if (presets.isEmpty()) return
        val index = presets.indexOf(current.intervalMinutes).let { if (it >= 0) it else 0 }
        val size = presets.size
        setInterval(context, presets[((index + delta) % size + size) % size])
    }

    fun setInterval(context: Context, minutes: Int) {
        Store.init(context)
        val clamped = minutes.coerceIn(AppState.MIN_MINUTES, AppState.MAX_MINUTES)
        Store.update { it.copy(intervalMinutes = clamped) }
        refresh(context)
    }

    fun addPreset(context: Context, minutes: Int) {
        Store.init(context)
        val clamped = minutes.coerceIn(AppState.MIN_MINUTES, AppState.MAX_MINUTES)
        Store.update { it.copy(presets = (it.presets + clamped).distinct().sorted()) }
        refresh(context)
    }

    fun removePreset(context: Context, minutes: Int) {
        Store.init(context)
        Store.update {
            val presets = it.presets - minutes
            if (presets.isEmpty()) it
            else it.copy(
                presets = presets,
                intervalMinutes = if (it.intervalMinutes == minutes) presets.first() else it.intervalMinutes,
            )
        }
        refresh(context)
    }

    fun refresh(context: Context) {
        Notifications.update(context)
    }
}
