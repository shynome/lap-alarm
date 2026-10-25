package lap.shynome.com.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import lap.shynome.com.core.Lap
import lap.shynome.com.core.Notifications

/**
 * 自建引擎的响铃服务。
 *
 * 到点后以前台服务的形式循环播放闹钟铃声、振动，并点亮屏幕。
 * 用户点通知上的「关闭」、媒体卡上的 ⏹，或最长 [MAX_RING_MS] 后自动结束。
 */
class RingService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Lap.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 复用同一条状态通知：前台服务接管它，结束时会自动降回普通通知
        if (!startForegroundCompat()) {
            // 用户回收了精确闹钟权限时系统会拒绝 systemExempted，此时静默退出即可
            Notifications.update(this)
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        playRingtone()
        startVibration()
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, MAX_RING_MS)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopRingtone()
        stopVibration()
        releaseWakeLock()
        // DETACH：把通知从服务上摘下来但保留，随后 onRingStopped 会把它刷成普通形态
        stopForeground(STOP_FOREGROUND_DETACH)
        Lap.onRingStopped(this)
        super.onDestroy()
    }

    /**
     * 以 systemExempted 类型进入前台。
     *
     * 该类型要求应用持有 SCHEDULE_EXACT_ALARM 或 USE_EXACT_ALARM。
     * 用户回收精确闹钟权限后（仅 SCHEDULE_EXACT_ALARM 可被回收），系统会拒绝本次调用。
     */
    private fun startForegroundCompat(): Boolean = runCatching {
        val notification = Notifications.buildNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notifications.ID_STATUS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
            )
        } else {
            startForeground(Notifications.ID_STATUS, notification)
        }
    }.isSuccess

    private fun playRingtone() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        val p = MediaPlayer()
        val ok = runCatching {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            p.setDataSource(this, uri)
            p.isLooping = true
            p.prepare()
            p.start()
        }.isSuccess
        if (ok) {
            player = p
        } else {
            runCatching { p.release() }
        }
    }

    private fun stopRingtone() {
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return
        val pattern = longArrayOf(0L, 800L, 600L)
        // repeat=0 表示从下标 0 开始无限循环
        runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, 0)) }
        vibrator = v
    }

    private fun stopVibration() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java) ?: return
        @Suppress("DEPRECATION")
        val lock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "lap:ring",
        )
        runCatching { lock.acquire(MAX_RING_MS) }
        wakeLock = lock
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
        wakeLock = null
    }

    companion object {
        /** 无人处理时最多响 5 分钟。 */
        private const val MAX_RING_MS = 5 * 60 * 1000L

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, RingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, RingService::class.java)) }
        }
    }
}
