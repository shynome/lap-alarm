package lap.shynome.com.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import lap.shynome.com.core.Lap
import lap.shynome.com.service.RingService

/**
 * 自建引擎到点时的入口。只负责把响铃服务拉起来，其余逻辑都在 [RingService] 里。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        Lap.init(context)
        Lap.onAlarmFired(context)
        RingService.start(context)
    }

    companion object {
        const val ACTION_FIRE = "lap.action.ALARM_FIRE"
    }
}
