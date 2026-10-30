package com.shynome.lap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA7F2E6),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4A635D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8E1),
    onSecondaryContainer = Color(0xFF06201B),
    background = Color(0xFFF6F7F5),
    onBackground = Color(0xFF1A1C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1A),
    surfaceVariant = Color(0xFFECEFEB),
    onSurfaceVariant = Color(0xFF5F6360),
    outline = Color(0xFFBFC5C0),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DD0BA),
    onPrimary = Color(0xFF04302A),
    primaryContainer = Color(0xFF0B4B41),
    onPrimaryContainer = Color(0xFFA7F2E6),
    secondary = Color(0xFFB1CCC5),
    onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B46),
    onSecondaryContainer = Color(0xFFCDE8E1),
    background = Color(0xFF111413),
    onBackground = Color(0xFFE2E3E1),
    surface = Color(0xFF1A1D1C),
    onSurface = Color(0xFFE2E3E1),
    surfaceVariant = Color(0xFF262A28),
    onSurfaceVariant = Color(0xFFA8AEAB),
    outline = Color(0xFF414744),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

@Composable
fun LapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
