package looply.shynome.com

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import looply.shynome.com.core.AlarmScheduler
import looply.shynome.com.core.Engine
import looply.shynome.com.core.Looply
import looply.shynome.com.core.Store
import looply.shynome.com.core.TimerLauncher
import looply.shynome.com.ui.LooplyScreen
import looply.shynome.com.ui.theme.LooplyTheme

class MainActivity : ComponentActivity() {

    private var timerAvailable by mutableStateOf(true)
    private var overlayGranted by mutableStateOf(true)
    private var exactAlarmGranted by mutableStateOf(true)
    private var notifGranted by mutableStateOf(true)
    private var askedNotif = false
    private var pendingStartAfterNotif = false

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifGranted = granted
        Looply.refresh(this)
        if (pendingStartAfterNotif) {
            pendingStartAfterNotif = false
            // 用户授权后直接把他刚才想开的那一轮开起来
            if (granted) startTimerInternal()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Looply.init(this)
        enableEdgeToEdge()

        notifGranted = notificationsEnabled()

        setContent {
            LooplyTheme {
                val state by Store.state.collectAsState()
                LooplyScreen(
                    state = state,
                    timerAvailable = timerAvailable,
                    overlayGranted = overlayGranted,
                    notifGranted = notifGranted,
                    onStart = { startTimer() },
                    onPause = { Looply.pauseTimer(this) },
                    onResume = { Looply.resumeTimer(this) },
                    onStop = { closeOrOpenTimers() },
                    onRequestOverlay = { openOverlaySettings() },
                    onRequestNotifications = { openNotificationSettings() },
                    onSelectPreset = { Looply.setInterval(this, it) },
                    onAddPreset = { Looply.addPreset(this, it) },
                    onRemovePreset = { minutes ->
                        Looply.removePreset(this, minutes)
                        Toast.makeText(this, "已删除 $minutes 分钟档位", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 自建引擎不依赖系统时钟，也不依赖悬浮窗权限
        val builtin = Store.state.value.engine == Engine.BUILTIN
        timerAvailable = builtin || TimerLauncher.isAvailable(this)
        overlayGranted = builtin || Settings.canDrawOverlays(this)
        exactAlarmGranted = AlarmScheduler.canScheduleExact(this)
        notifGranted = notificationsEnabled()
        Looply.refresh(this)
    }

    private fun notificationsEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // MIUI 的 areNotificationsEnabled() 不反映运行时权限，必须直接查权限
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        }

    private fun openNotificationSettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
    }

    /** 「关闭正在进行的倒计时」：自建引擎直接结束本轮，系统时钟引擎只能跳到时钟页面。 */
    private fun closeOrOpenTimers() {
        if (Store.state.value.engine == Engine.BUILTIN) {
            Looply.stopTimer(this)
        } else if (!Looply.openTimers(this)) {
            Toast.makeText(this, "无法打开系统时钟", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openOverlaySettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            )
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    /**
     * 只在真正要开始计时时确保通知权限。
     *
     * 没有通知就没有媒体控件，所以这一步不能省；但也不该在开屏时就弹，
     * 用户还没表达意图之前弹窗会被直接拒掉。
     *
     * @return true 表示权限就绪，可以继续启动
     */
    private fun ensureNotificationPermission(): Boolean {
        if (notifGranted) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        if (askedNotif &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            // 已被永久拒绝，系统不会再弹框，只能引导去设置页
            Toast.makeText(this, "请允许 Looply 发送通知，否则看不到倒计时控件", Toast.LENGTH_LONG).show()
            openNotificationSettings()
            return false
        }

        askedNotif = true
        pendingStartAfterNotif = true
        runCatching { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        return false
    }

    private fun startTimer() {
        if (!ensureNotificationPermission()) return
        startTimerInternal()
    }

    private fun startTimerInternal() {
        notifGranted = notificationsEnabled()
        when (Store.state.value.engine) {
            Engine.BUILTIN -> if (!exactAlarmGranted) {
                Toast.makeText(this, "需要「闹钟和提醒」权限才能精确计时", Toast.LENGTH_LONG).show()
                openExactAlarmSettings()
                return
            }

            Engine.SYSTEM_CLOCK -> if (!TimerLauncher.isAvailable(this)) {
                timerAvailable = false
                Toast.makeText(this, "系统里没有可用的时钟应用", Toast.LENGTH_LONG).show()
                return
            }
        }
        if (!Looply.startTimer(this)) {
            Toast.makeText(this, "无法启动倒计时", Toast.LENGTH_LONG).show()
        }
    }
}
