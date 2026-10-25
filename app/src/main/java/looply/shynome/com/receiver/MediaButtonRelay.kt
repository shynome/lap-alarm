package looply.shynome.com.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import looply.shynome.com.core.MediaPlayback

/**
 * 把通知按钮发出的媒体按键转发给 MediaSession。
 *
 * 通知的 action 用的是 `Intent.ACTION_MEDIA_BUTTON` + KeyEvent，
 * 点击后先到这里（广播，能唤醒进程且不受 BAL 限制），
 * 再交给 MediaSession 的 transport 回调处理，
 * 这样点击事件就回到了应用进程内部，而不是由系统代发跨应用 PendingIntent。
 */
class MediaButtonRelay : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        runCatching {
            MediaPlayback.ensure(context).controller.dispatchMediaButtonEvent(event)
        }
    }
}
