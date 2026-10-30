package com.shynome.lap.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shynome.lap.MainActivity
import com.shynome.lap.receiver.AlarmReceiver

/**
 * 自建引擎的闹钟调度。
 *
 * 用 [AlarmManager.setAlarmClock]：这是系统里优先级最高的闹钟类型，
 * 会显示状态栏闹钟图标，且不受省电模式 / 待机分组影响，MIUI 也不会随意丢弃。
 */
object AlarmScheduler {

    private const val REQUEST_FIRE = 201
    private const val REQUEST_SHOW = 202

    private fun alarmManager(context: Context): AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    /** Android 12+ 需要精确闹钟权限，否则 `setAlarmClock` 会抛 `SecurityException`。 */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager(context)?.canScheduleExactAlarms() == true

    fun schedule(context: Context, triggerAt: Long): Boolean {
        val am = alarmManager(context) ?: return false
        val show = PendingIntent.getActivity(
            context,
            REQUEST_SHOW,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return runCatching {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), fireIntent(context))
        }.isSuccess
    }

    fun cancel(context: Context) {
        runCatching { alarmManager(context)?.cancel(fireIntent(context)) }
    }

    private fun fireIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_FIRE,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
