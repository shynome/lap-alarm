package lap.shynome.com

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
import lap.shynome.com.core.AlarmScheduler
import lap.shynome.com.core.Lap
import lap.shynome.com.core.Store
import lap.shynome.com.ui.LapScreen
import lap.shynome.com.ui.theme.LapTheme

class MainActivity : ComponentActivity() {

    private var exactAlarmGranted by mutableStateOf(true)
    private var notifGranted by mutableStateOf(true)
    private var askedNotif = false
    private var pendingStartAfterNotif = false

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifGranted = granted
        Lap.refresh(this)
        if (pendingStartAfterNotif) {
            pendingStartAfterNotif = false
            // 用户授权后直接把他刚才想开的那一轮开起来
            if (granted) startTimerInternal()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lap.init(this)
        enableEdgeToEdge()
        notifGranted = notificationsEnabled()

        setContent {
            LapTheme {
                val state by Store.state.collectAsState()
                LapScreen(
                    state = state,
                    notifGranted = notifGranted,
                    onStart = { startTimer() },
                    onPause = { Lap.pauseTimer(this) },
                    onResume = { Lap.resumeTimer(this) },
                    onStop = { Lap.stopTimer(this) },
                    onRequestNotifications = { openNotificationSettings() },
                    onSelectPreset = { Lap.setInterval(this, it) },
                    onAddPreset = { Lap.addPreset(this, it) },
                    onRemovePreset = { minutes ->
                        Lap.removePreset(this, minutes)
                        Toast.makeText(this, getString(R.string.toast_preset_removed, minutes), Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        exactAlarmGranted = AlarmScheduler.canScheduleExact(this)
        notifGranted = notificationsEnabled()
        Lap.refresh(this)
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
     * 用户还没表达意图之前弹窗容易被直接拒掉。
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
            Toast.makeText(this, getString(R.string.toast_need_notif), Toast.LENGTH_LONG).show()
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
        if (!exactAlarmGranted) {
            Toast.makeText(this, getString(R.string.toast_need_exact_alarm), Toast.LENGTH_LONG).show()
            openExactAlarmSettings()
            return
        }
        if (!Lap.startTimer(this)) {
            Toast.makeText(this, getString(R.string.toast_start_failed), Toast.LENGTH_LONG).show()
        }
    }
}
