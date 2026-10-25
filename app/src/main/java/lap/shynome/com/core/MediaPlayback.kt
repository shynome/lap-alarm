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

                /** 快进键：空闲→开新一轮，暂停中→继续，响铃中→关闭铃声。 */
                override fun onPlay() {
                    val state = Store.state.value
                    when {
                        state.ringing -> Lap.stopTimer(app)
                        state.paused -> Lap.resumeTimer(app)
                        else -> Lap.startTimer(app)
                    }
                }

                /** 暂停。自建引擎是真暂停，系统时钟引擎只是切换显示。 */
                override fun onPause() {
                    Lap.pauseTimer(app)
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
        val totalMs = (if (state.runningMinutes > 0) state.runningMinutes else state.intervalMinutes) * 60_000L
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

        // 正在计时且未手动切到暂停 → PLAYING（锁屏显示 ⏸）；否则 PAUSED（显示 ▶）
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
                    when {
                        playing -> PlaybackStateCompat.STATE_PLAYING
                        state.ringing -> PlaybackStateCompat.STATE_PAUSED
                        else -> PlaybackStateCompat.STATE_PAUSED
                    },
                    elapsedMs,
                    if (playing) 1f else 0f,
                )
                .build(),
        )
    }
}
