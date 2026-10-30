package com.shynome.lap.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shynome.lap.core.Lap

/**
 * 通知按钮里「切档位」这类不需要启动界面动作用广播处理，
 * 进程被杀也能由系统拉起。
 */
class ControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Lap.init(context)
        when (intent.action) {
            ACTION_PREV -> Lap.shiftPreset(context, -1)
            ACTION_NEXT -> Lap.shiftPreset(context, 1)
            ACTION_PAUSE -> Lap.pauseTimer(context)
            ACTION_RESUME -> Lap.resumeTimer(context)
            ACTION_STOP -> Lap.stopTimer(context)
            ACTION_SET_INTERVAL -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
                if (minutes > 0) Lap.setInterval(context, minutes)
            }
            ACTION_START -> Lap.startTimer(context)
        }
    }

    companion object {
        const val ACTION_PREV = "lap.action.PREV"
        const val ACTION_NEXT = "lap.action.NEXT"
        const val ACTION_PAUSE = "lap.action.PAUSE"
        const val ACTION_RESUME = "lap.action.RESUME"
        const val ACTION_STOP = "lap.action.STOP"
        const val ACTION_SET_INTERVAL = "lap.action.SET_INTERVAL"
        const val ACTION_START = "lap.action.START"
        const val EXTRA_MINUTES = "minutes"
    }
}
