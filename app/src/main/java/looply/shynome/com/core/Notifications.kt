package looply.shynome.com.core

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import looply.shynome.com.MainActivity
import looply.shynome.com.R
import looply.shynome.com.receiver.ControlReceiver
import looply.shynome.com.receiver.MediaButtonRelay
import android.view.KeyEvent

/**
 * 只发一条 MediaStyle 通知，同时承担通知栏与锁屏入口。
 *
 * 中间按钮的两种形态：
 * - 计时中 → 「暂停」：点了不会真的停掉系统倒计时，只把卡片切回 ▶
 * - 已暂停 / 未开始 → 「播放」：点了由中转页启动一轮新的倒计时
 */
object Notifications {
    const val CHANNEL_STATUS = "looply_status"
    const val ID_STATUS = 100

    private fun piFlags(): Int =
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** Android 14+：声明此 PendingIntent 允许在后台启动 Activity。 */
    private fun backgroundStartOptions(): Bundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
                .toBundle()
        } else {
            null
        }

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.channel_status),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_status_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun update(context: Context) {
        ensureChannels(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        runCatching { NotificationManagerCompat.from(context).notify(ID_STATUS, buildNotification(context)) }
    }

    /** 供前台服务使用的同一个通知。 */
    fun buildNotification(context: Context): Notification = build(context)

    private fun build(context: Context): Notification {
        val state = Store.state.value
        val ringing = state.ringing
        val playing = state.isPlaying()

        val contentIntent = PendingIntent.getActivity(
            context,
            42,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            piFlags(),
        )

        MediaPlayback.update(context, state)

        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_loop)
            .setContentTitle(
                if (ringing) {
                    context.getString(R.string.notif_title_ringing, state.runningMinutes)
                } else {
                    context.getString(R.string.notif_title, state.intervalMinutes)
                },
            )
            .setContentText(
                context.getString(
                    when {
                        ringing -> R.string.notif_subtitle_ringing
                        playing -> R.string.media_subtitle_builtin_running
                        state.paused -> R.string.notif_subtitle_builtin_paused
                        state.isTimerRunning() -> R.string.notif_subtitle_paused
                        else -> R.string.notif_subtitle
                    },
                ),
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(ContextCompat.getColor(context, R.color.notif_primary))
            // 按钮用「媒体按键」而不是自定义广播：
            // 点击后会作为 KEYCODE_MEDIA_* 下发到 MediaSession 的 transport 回调，
            // 即事件回到应用进程内，而不是由系统代发一个跨应用 PendingIntent。
            .addAction(
                R.drawable.ic_chevron_left,
                context.getString(R.string.cd_prev),
                mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 11),
            )
            .addAction(
                when {
                    ringing -> R.drawable.ic_stop
                    playing -> R.drawable.ic_stop
                    else -> R.drawable.ic_play
                },
                when {
                    ringing -> context.getString(R.string.action_stop_ring)
                    playing -> context.getString(R.string.action_pause)
                    else -> context.getString(R.string.action_start_timer, state.intervalMinutes)
                },
                mediaButton(
                    context,
                    when {
                        ringing -> KeyEvent.KEYCODE_MEDIA_STOP
                        playing -> KeyEvent.KEYCODE_MEDIA_PAUSE
                        else -> KeyEvent.KEYCODE_MEDIA_PLAY
                    },
                    if (ringing) 103 else 13,
                ),
            )
            .addAction(
                R.drawable.ic_chevron_right,
                context.getString(R.string.cd_next),
                mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT, 12),
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(MediaPlayback.sessionToken(context))
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    /** 把媒体按键包成一个发往 [MediaButtonRelay] 的广播 PendingIntent。 */
    private fun mediaButton(context: Context, keyCode: Int, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, MediaButtonRelay::class.java).apply {
                action = Intent.ACTION_MEDIA_BUTTON
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            },
            piFlags(),
        )

    private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ControlReceiver::class.java).setAction(action),
            piFlags(),
        )

    @SuppressLint("MissingPermission")
    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_STATUS) }
    }
}
