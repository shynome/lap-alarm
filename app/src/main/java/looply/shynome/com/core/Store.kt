package looply.shynome.com.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 计时引擎。
 *
 * - [BUILTIN]：本应用用 [android.app.AlarmManager] 自己计时并响铃。后台可完全控制。
 * - [SYSTEM_CLOCK]：把计时交给系统时钟应用。响铃观感好，但后台无法一键启动（MIUI 拦截）。
 */
enum class Engine { BUILTIN, SYSTEM_CLOCK }

/**
 * 应用的全部状态。
 *
 * [SYSTEM_CLOCK] 引擎下本应用不持有计时，[timerStartedAt] 只用于推算系统倒计时是否还在跑；
 * [BUILTIN] 引擎下它是真实计时起点，[paused] 是真实暂停。
 */
data class AppState(
    val intervalMinutes: Int = 25,
    val presets: List<Int> = DEFAULT_PRESETS,
    val engine: Engine = Engine.BUILTIN,
    val timerStartedAt: Long = 0L,
    /** 正在跑的这一轮的时长（分钟）。与 [intervalMinutes] 分开，切档位不会打乱当前轮。 */
    val runningMinutes: Int = 0,
    /** 暂停状态。自建引擎下是真暂停，系统时钟引擎下只是媒体卡的显示开关。 */
    val paused: Boolean = false,
    /** 自建引擎真暂停时冻结的剩余毫秒。 */
    val pausedRemainingMs: Long = 0L,
    /** 自建引擎是否正在响铃。 */
    val ringing: Boolean = false,
) {
    private val builtin: Boolean get() = engine == Engine.BUILTIN

    fun endsAt(): Long =
        if (timerStartedAt <= 0L || runningMinutes <= 0) 0L
        else timerStartedAt + runningMinutes * 60_000L

    /** 是否存在进行中的一轮（含暂停与响铃）。 */
    fun isTimerRunning(now: Long = System.currentTimeMillis()): Boolean =
        timerStartedAt > 0L && (if (builtin && paused) true else now < endsAt())

    /** 媒体卡是否显示「计时中」。 */
    fun isPlaying(now: Long = System.currentTimeMillis()): Boolean =
        isTimerRunning(now) && !paused && !ringing && now < endsAt()

    fun remainingMs(now: Long = System.currentTimeMillis()): Long =
        if (builtin && paused) pausedRemainingMs else (endsAt() - now).coerceAtLeast(0L)

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
    private const val FILE = "looply_state"
    private const val KEY_INTERVAL = "interval"
    private const val KEY_PRESETS = "presets"
    private const val KEY_ENGINE = "engine"
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
            engine = if (p.getString(KEY_ENGINE, null) == Engine.SYSTEM_CLOCK.name) {
                Engine.SYSTEM_CLOCK
            } else {
                Engine.BUILTIN
            },
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
            ?.putString(KEY_ENGINE, next.engine.name)
            ?.putLong(KEY_TIMER_STARTED, next.timerStartedAt)
            ?.putInt(KEY_RUNNING_MINUTES, next.runningMinutes)
            ?.putBoolean(KEY_PAUSED, next.paused)
            ?.putLong(KEY_PAUSED_REMAINING, next.pausedRemainingMs)
            ?.apply()
    }
}
