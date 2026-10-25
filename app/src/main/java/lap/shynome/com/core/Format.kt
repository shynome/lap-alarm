package lap.shynome.com.core

import java.util.Locale

/** 把剩余毫秒格式化成 mm:ss 或 h:mm:ss。 */
fun formatRemaining(ms: Long): String {
    val totalSeconds = ((ms + 999) / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = totalSeconds / 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
