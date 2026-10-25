package looply.shynome.com

import android.os.Bundle
import androidx.activity.ComponentActivity
import looply.shynome.com.core.Looply

/**
 * 启动倒计时的中转页（无界面）。
 *
 * 为什么需要它：媒体卡上的「播放」必须同时做两件事——拉起时钟应用、更新本地状态。
 * - 从 BroadcastReceiver 启动 Activity 会被 BAL 拦截；
 * - 由通知 PendingIntent 拉起的 Activity 是前台的，可以正常 startActivity。
 *
 * 后台场景下这条路径同样受 BAL 限制，需要「显示在其他应用上层」权限才能走通，
 * 所以应用内会引导用户开启。
 */
class LaunchTimerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Looply.init(this)
        Looply.startTimer(this)
        finish()
    }
}
