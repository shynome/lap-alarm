package lap.shynome.com.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import lap.shynome.com.R
import lap.shynome.com.core.AppState
import lap.shynome.com.core.formatRemaining

@Composable
fun LapScreen(
    state: AppState,
    notifGranted: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRequestNotifications: () -> Unit,
    onSelectPreset: (Int) -> Unit,
    onAddPreset: (Int) -> Unit,
    onRemovePreset: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            BrandHeader()
            Spacer(Modifier.height(20.dp))
            DialCard(state)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                state = state,
                enabled = true,
                onStart = onStart,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
            )
            if (state.isTimerRunning()) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onStart,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text(
                        "重新开始 ${state.intervalMinutes} 分钟",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            PresetSection(
                state = state,
                onSelectPreset = onSelectPreset,
                onAddPreset = onAddPreset,
                onRemovePreset = onRemovePreset,
            )
            Spacer(Modifier.height(16.dp))
            if (!notifGranted) {
                NoteCard(
                    title = "还需要通知权限",
                    body = "通知栏和锁屏的媒体卡片靠通知权限显示。没有它，计时中看不到控件，" +
                        "只有响铃时才会短暂出现。",
                    action = "去开启",
                    onAction = onRequestNotifications,
                )
                Spacer(Modifier.height(12.dp))
            }
            BehaviorNote()
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 页面在前台且计时中存在时，每 250ms 刷新一次当前时间，用来局部重绘倒计时。 */
@Composable
private fun rememberNow(active: Boolean): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        while (active) {
            now = System.currentTimeMillis()
            delay(250L)
        }
    }
    return now
}

// ------------------------------------------------------------------ 顶部

@Composable
private fun BrandHeader() {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            // 与启动图标一致的「一圈跑道」：轨道 + 270° 进度弧 + 起跑点
            Canvas(Modifier.size(24.dp)) {
                val stroke = 2.6.dp.toPx()
                val radius = (size.minDimension - stroke) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(
                    color = colors.onPrimaryContainer.copy(alpha = 0.22f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = colors.onPrimaryContainer,
                    startAngle = -90f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawCircle(
                    color = colors.onPrimaryContainer,
                    radius = stroke * 0.95f,
                    center = Offset(center.x, center.y - radius),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "Lap",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "专注一圈",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------ 主卡片

@Composable
private fun DialCard(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val now = rememberNow(state.isActive())

    val ringing = state.ringing
    val active = state.isTimerRunning(now)
    val totalMs =
        (if (state.runningMinutes > 0) state.runningMinutes else state.intervalMinutes) * 60_000L
    val remaining = if (active) state.remainingMs(now) else totalMs
    val fraction = if (active && totalMs > 0L) {
        (remaining.toFloat() / totalMs).coerceIn(0f, 1f)
    } else {
        1f
    }

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = colors.surface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, colors.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = androidx.compose.ui.geometry.Size(
                        size.width - stroke,
                        size.height - stroke,
                    )
                    val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)

                    drawArc(
                        color = colors.primaryContainer,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    // 剩余部分用主色盖上去，随时间递减
                    if (fraction < 1f || ringing) {
                        drawArc(
                            color = if (ringing) colors.error else colors.primary,
                            startAngle = -90f,
                            sweepAngle = 360f * fraction,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        ringing -> {
                            Text(
                                text = "时间到",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.error,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "点下方按钮关闭",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.error,
                            )
                        }

                        active -> {
                            Text(
                                text = formatRemaining(remaining),
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontSize = 52.sp,
                                    fontWeight = FontWeight.Bold,
                                    // 等宽数字，避免秒数跳动时整体左右抖
                                    fontFeatureSettings = "tnum",
                                ),
                                color = colors.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (state.paused) "已暂停" else "进行中",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (state.paused) colors.onSurfaceVariant else colors.primary,
                            )
                        }

                        else -> {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = state.intervalMinutes.toString(),
                                    fontSize = 64.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface,
                                )
                                Text(
                                    text = "分",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "准备开始",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 主按钮

@Composable
private fun PrimaryButton(
    state: AppState,
    enabled: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val label: String
    val action: () -> Unit
    when {
        state.ringing -> {
            label = "关闭"
            action = onStop
        }

        state.isPlaying() -> {
            label = "暂停"
            action = onPause
        }

        state.isTimerRunning() -> {
            label = "继续"
            action = onResume
        }

        else -> {
            label = "开始 ${state.intervalMinutes} 分钟"
            action = onStart
        }
    }

    Button(
        onClick = action,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ 档位

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetSection(
    state: AppState,
    onSelectPreset: (Int) -> Unit,
    onAddPreset: (Int) -> Unit,
    onRemovePreset: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle(
            title = "档位",
            subtitle = if (editing) "点 × 删除" else null,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { editing = !editing }) {
            Text(
                text = if (editing) "完成" else "编辑",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
    Spacer(Modifier.height(12.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.presets.forEach { minutes ->
            PresetChip(
                minutes = minutes,
                selected = minutes == state.intervalMinutes,
                editing = editing,
                // 只剩一个档位时不允许再删
                canRemove = state.presets.size > 1,
                onClick = { if (!editing) onSelectPreset(minutes) },
                onRemove = { onRemovePreset(minutes) },
            )
        }
        AddChip(onClick = { showDialog = true })
    }

    if (showDialog) {
        AddPresetDialog(
            onDismiss = { showDialog = false },
            onConfirm = {
                onAddPreset(it)
                showDialog = false
            },
        )
    }

    Spacer(Modifier.height(10.dp))
    Text(
        text = "当前选中 ${state.intervalMinutes} 分钟 · 共 ${state.presets.size} 个档位",
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
    )
}

@Composable
private fun PresetChip(
    minutes: Int,
    selected: Boolean,
    editing: Boolean,
    canRemove: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // 宽度跟着文字走；编辑态只在右侧追加一个删除按钮
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected && !editing) colors.primary else colors.surface)
            .border(
                width = 1.dp,
                color = if (selected && !editing) Color.Transparent else colors.outline,
                shape = RoundedCornerShape(50),
            )
            .clickable(enabled = !editing, onClick = onClick)
            .padding(
                start = 18.dp,
                end = if (editing) 10.dp else 18.dp,
                top = 11.dp,
                bottom = 11.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$minutes 分钟",
            fontSize = 14.sp,
            fontWeight = if (selected && !editing) FontWeight.Bold else FontWeight.Medium,
            color = if (selected && !editing) colors.onPrimary else colors.onSurface,
            maxLines = 1,
        )
        if (editing && canRemove) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "删除 $minutes 分钟档位",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun AddChip(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Text(
            text = "+ 自定义",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddPresetDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var input by remember { mutableStateOf("") }
    val parsed = input.toIntOrNull()
    val valid = parsed != null && parsed in AppState.MIN_MINUTES..AppState.MAX_MINUTES
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // 对话框窗口挂载完成前请求焦点会被忽略，稍等一拍
        delay(120L)
        runCatching { focusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加档位") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        if (value.length <= 4 && value.all { it.isDigit() }) input = value
                    },
                    singleLine = true,
                    label = { Text("分钟数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "范围 ${AppState.MIN_MINUTES} ~ ${AppState.MAX_MINUTES} 分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = valid) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ------------------------------------------------------------------ 说明

@Composable
private fun NoteCard(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun BehaviorNote() {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("怎么工作", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "点开始后由 Lap 自己计时，到点响铃、震动并点亮屏幕。\n\n" +
                    "锁屏和通知栏的媒体卡片可以直接控制：播放键开始或继续、" +
                    "暂停键暂停、停止键结束；进度条也能拖动，把剩余时间提前或推后。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------ 通用

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        if (subtitle != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
