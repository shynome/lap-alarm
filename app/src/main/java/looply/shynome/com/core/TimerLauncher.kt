package looply.shynome.com.core

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

/**
 * 把倒计时交给系统时钟应用。
 *
 * 实测（HyperOS / Android 16）：
 * - `SET_TIMER` 可用，`EXTRA_SKIP_UI` 时直接开始、不打开时钟界面；
 * - `DISMISS_TIMER` 该 ROM **未实现**（No activities found），所以无法程序化关闭；
 * - `STOP_WATCH` 是「秒表」快捷方式，与关闭倒计时无关；
 * - `SHOW_TIMERS` 可用，可把用户送到倒计时页面手动关闭。
 */
object TimerLauncher {

    /** 系统里是否存在能处理倒计时的时钟应用（需要 manifest 里的 <queries> 才能查到）。 */
    fun isAvailable(context: Context): Boolean = runCatching {
        context.packageManager.resolveActivity(Intent(AlarmClock.ACTION_SET_TIMER), 0) != null
    }.getOrDefault(false)

    /**
     * 构造交给系统时钟的倒计时 Intent。
     *
     * [EXTRA_SKIP_UI] 让时钟应用直接开始倒计时而不打开自己的界面。
     *
     * message 末尾追加不可见的零宽字符：系统时钟对「参数完全相同且未被使用」的计时器
     * 会直接复用，那样再点一次播放就不会从头计时。让 message 每次都不同即可强制新建，
     * 零宽字符在界面上不可见，不影响观感。
     */
    fun timerIntent(context: Context, minutes: Int, label: String): Intent =
        Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_MESSAGE, label + uniqueTag())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun uniqueTag(): String =
        "\u200B".repeat((System.currentTimeMillis() % 97 + 1).toInt())

    /** 打开时钟应用的倒计时页面，供用户手动关闭正在跑的那一轮。 */
    fun showTimersIntent(): Intent =
        Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun launch(context: Context, minutes: Int, label: String): Boolean =
        runCatching { context.startActivity(timerIntent(context, minutes, label)) }.isSuccess

    fun openTimers(context: Context): Boolean =
        runCatching { context.startActivity(showTimersIntent()) }.isSuccess
}
