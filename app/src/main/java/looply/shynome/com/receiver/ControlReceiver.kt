package looply.shynome.com.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import looply.shynome.com.core.Looply

/**
 * 通知按钮里「切档位」这类不需要启动界面动作用广播处理，
 * 进程被杀也能由系统拉起。
 */
class ControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Looply.init(context)
        when (intent.action) {
            ACTION_PREV -> Looply.shiftPreset(context, -1)
            ACTION_NEXT -> Looply.shiftPreset(context, 1)
            ACTION_PAUSE -> Looply.pauseTimer(context)
            ACTION_RESUME -> Looply.resumeTimer(context)
            ACTION_STOP -> Looply.stopTimer(context)
            ACTION_SET_INTERVAL -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
                if (minutes > 0) Looply.setInterval(context, minutes)
            }
            ACTION_START -> Looply.startTimer(context)
        }
    }

    companion object {
        const val ACTION_PREV = "looply.action.PREV"
        const val ACTION_NEXT = "looply.action.NEXT"
        const val ACTION_PAUSE = "looply.action.PAUSE"
        const val ACTION_RESUME = "looply.action.RESUME"
        const val ACTION_STOP = "looply.action.STOP"
        const val ACTION_SET_INTERVAL = "looply.action.SET_INTERVAL"
        const val ACTION_START = "looply.action.START"
        const val EXTRA_MINUTES = "minutes"
    }
}
