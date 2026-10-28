package lap.shynome.com.core

import android.content.Context
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.graphics.drawable.toBitmap
import lap.shynome.com.R

/**
 * 把「启动倒计时」伪装成一个媒体会话，让系统在锁屏上渲染媒体控制卡。
 *
 * 用户在锁屏选择「隐藏通知内容」后，普通通知会整个消失，只有媒体投递卡能穿透该设置，
 * 这是第三方应用在锁屏上唯一稳定的可操作入口。
 *
 * 按钮语义（借用播放器的三个键）：
 *   上一首 = 上一个档位   播放 = 启动这一轮   下一首 = 下一个档位
 */
object MediaPlayback {
    private var session: MediaSessionCompat? = null

    fun ensure(context: Context): MediaSessionCompat {
        session?.let { return it }
        val app = context.applicationContext
        val s = MediaSessionCompat(app, "Lap").apply {
            setCallback(object : MediaSessionCompat.Callback() {

                /**
                 * 播放键：空闲→开新一轮，暂停中→继续。
                 *
                 * 只有暂停的这一轮仍属于当前档位才能继续；被切档位搁置的旧一轮不带剩余时间回来，
                 * 直接按当前档位的完整时长重新开一轮。
                 */
                override fun onPlay() {
                    val state = Store.state.value
                    when {
                        // 响铃中正常只会下发「暂停」；万一收到播放，兜底只关铃声，不直接开新一轮
                        state.ringing -> Lap.stopTimer(app)
                        state.paused && state.isTimerRunning() -> Lap.resumeTimer(app)
                        else -> Lap.startTimer(app)
                    }
                }

                /**
                 * 暂停键。
                 *
                 * 响铃中锁屏显示的是 ⏸，点它是「关闭铃声」：铃声停掉后卡片回到 ▶，
                 * 再点一次才会按当前档位重新开一轮，避免手滑一下就直接重新响。
                 */
                override fun onPause() {
                    val state = Store.state.value
                    if (state.ringing) Lap.stopTimer(app) else Lap.pauseTimer(app)
                }

                /** 拖动进度条：把计时跳到对应位置。 */
                override fun onSeekTo(pos: Long) {
                    Lap.seekTo(app, pos)
                }

                override fun onStop() {
                    Lap.stopTimer(app)
                }

                override fun onSkipToNext() {
                    Lap.shiftPreset(app, 1)
                }

                override fun onSkipToPrevious() {
                    Lap.shiftPreset(app, -1)
                }
            })
            isActive = true
        }
        session = s
        return s
    }

    fun sessionToken(context: Context) = ensure(context).sessionToken

    private var iconCache: android.graphics.Bitmap? = null

    /** 用应用图标作为媒体卡的封面图。 */
    private fun appIcon(context: Context): android.graphics.Bitmap? {
        iconCache?.let { return it }
        return runCatching {
            context.applicationInfo.loadIcon(context.packageManager)
                .toBitmap(256, 256)
                .also { iconCache = it }
        }.getOrNull()
    }

    fun update(context: Context, state: AppState) {
        val s = ensure(context)
        // 用户点媒体卡上的 ✕ 后，系统会把会话置为非活跃并可能移除卡片。
        // 每次刷新都重新激活，保证卡片始终作为锁屏/通知栏的常驻入口存在。
        if (!s.isActive) runCatching { s.isActive = true }
        // 标题固定为档位，不随剩余时间变化
        val title = context.getString(R.string.minutes_value, state.intervalMinutes)
        val subtitle = context.getString(
            when {
                state.ringing -> R.string.media_subtitle_ringing
                state.isPlaying() -> R.string.media_subtitle_builtin_running
                state.isTimerRunning() -> R.string.notif_subtitle_builtin_paused
                else -> R.string.media_subtitle
            },
        )

        // 进度条：总长 = 本轮时长，当前点 = 已过去的时长。
        // 给真实 position + speed=1 后，系统会自己推进进度条，不需要每秒刷新通知。
        // 切到别的档位后旧一轮被搁置，卡片这时按新档位显示成「整段未开始」。
        val sessionMinutes = when {
            state.ringing -> state.runningMinutes.takeIf { it > 0 } ?: state.intervalMinutes
            state.isTimerRunning() -> state.runningMinutes
            else -> state.intervalMinutes
        }
        val totalMs = sessionMinutes * 60_000L
        val elapsedMs = if (state.isActive()) {
            (totalMs - state.remainingMs()).coerceIn(0L, totalMs)
        } else {
            0L
        }
        val playing = state.isPlaying()

        s.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, totalMs)
                .apply { appIcon(context)?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) } }
                .build(),
        )

        // 计时中、响铃中 → PLAYING（锁屏显示 ⏸）；其余 PAUSED（显示 ▶）。
        // 响铃时也上报 PLAYING，是为了锁屏那颗键先当「关闭铃声」用：
        // 点它只停铃、卡片随即回到 ▶，再点 ▶ 才重新开一轮。
        // 速度给 0，响铃期间进度条不会被系统继续往前推。
        val stateForUi = if (playing || state.ringing) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        s.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(
                    stateForUi,
                    elapsedMs,
                    if (playing) 1f else 0f,
                )
                .build(),
        )
    }
}
