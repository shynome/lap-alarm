package com.shynome.lap.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用的全部状态。
 *
 * [timerStartedAt] 是本轮计时的起点，配合 [runningMinutes] 可算出结束时刻；
 * [paused] 为真时 [pausedRemainingMs] 保存冻结的剩余时间。
 */
data class AppState(
    val intervalMinutes: Int = 25,
    val presets: List<Int> = DEFAULT_PRESETS,
    val timerStartedAt: Long = 0L,
    /** 正在跑的这一轮的时长（分钟）。与 [intervalMinutes] 分开，切档位不会打乱当前轮。 */
    val runningMinutes: Int = 0,
    /** 是否暂停。暂停时系统闹钟已取消，剩余时间冻结在 [pausedRemainingMs]。 */
    val paused: Boolean = false,
    /** 暂停时冻结的剩余毫秒。 */
    val pausedRemainingMs: Long = 0L,
    /** 是否正在响铃。 */
    val ringing: Boolean = false,
) {
    fun endsAt(): Long =
        if (timerStartedAt <= 0L || runningMinutes <= 0) 0L
        else timerStartedAt + runningMinutes * 60_000L

    /**
     * 这一轮是否仍属于当前选中的档位。
     *
     * 切档位时旧一轮会被暂停并「搁置」：它既不展示也不响应按键，
     * 但剩余时间仍然留着——切回同一档位时可以接着走，切到别的档位则被新一轮覆盖。
     */
    fun sessionMatchesInterval(): Boolean = runningMinutes > 0 && runningMinutes == intervalMinutes

    /** 是否存在进行中的一轮（含暂停与响铃），且它属于当前档位。 */
    fun isTimerRunning(now: Long = System.currentTimeMillis()): Boolean =
        sessionMatchesInterval() && timerStartedAt > 0L && (paused || now < endsAt())

    /** 媒体卡是否显示「计时中」。 */
    fun isPlaying(now: Long = System.currentTimeMillis()): Boolean =
        isTimerRunning(now) && !paused && !ringing && now < endsAt()

    fun remainingMs(now: Long = System.currentTimeMillis()): Long =
        if (paused) pausedRemainingMs else (endsAt() - now).coerceAtLeast(0L)

    /** 主界面是否要突出显示「正在进行」区域。 */
    fun isActive(now: Long = System.currentTimeMillis()): Boolean =
        ringing || isTimerRunning(now)

    companion object {
        /** 默认档位：微休息 → 短任务 → 番茄钟 → 深度专注。 */
        val DEFAULT_PRESETS = listOf(5, 15, 25, 45)
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 24 * 60
    }
}

object Store {
    private const val FILE = "lap_state"
    private const val KEY_INTERVAL = "interval"
    private const val KEY_PRESETS = "presets"
    private const val KEY_TIMER_STARTED = "timer_started_at"
    private const val KEY_RUNNING_MINUTES = "running_minutes"
    private const val KEY_PAUSED = "paused"
    private const val KEY_PAUSED_REMAINING = "paused_remaining_ms"

    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        _state.value = AppState(
            intervalMinutes = p.getInt(KEY_INTERVAL, 25)
                .coerceIn(AppState.MIN_MINUTES, AppState.MAX_MINUTES),
            presets = parsePresets(p.getString(KEY_PRESETS, null)),
            timerStartedAt = p.getLong(KEY_TIMER_STARTED, 0L),
            runningMinutes = p.getInt(KEY_RUNNING_MINUTES, 0),
            paused = p.getBoolean(KEY_PAUSED, false),
            pausedRemainingMs = p.getLong(KEY_PAUSED_REMAINING, 0L),
        )
    }

    private fun parsePresets(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return AppState.DEFAULT_PRESETS
        val list = raw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in AppState.MIN_MINUTES..AppState.MAX_MINUTES }
            .distinct()
            .sorted()
        return list.ifEmpty { AppState.DEFAULT_PRESETS }
    }

    @Synchronized
    fun update(block: (AppState) -> AppState) {
        val next = block(_state.value)
        _state.value = next
        prefs?.edit()
            ?.putInt(KEY_INTERVAL, next.intervalMinutes)
            ?.putString(KEY_PRESETS, next.presets.sorted().joinToString(","))
            ?.putLong(KEY_TIMER_STARTED, next.timerStartedAt)
            ?.putInt(KEY_RUNNING_MINUTES, next.runningMinutes)
            ?.putBoolean(KEY_PAUSED, next.paused)
            ?.putLong(KEY_PAUSED_REMAINING, next.pausedRemainingMs)
            ?.apply()
    }
}
