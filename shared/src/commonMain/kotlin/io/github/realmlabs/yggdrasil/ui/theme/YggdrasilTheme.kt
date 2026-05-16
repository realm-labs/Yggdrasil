package io.github.realmlabs.yggdrasil.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF236A57),
    onPrimary = Color.White,
    secondary = Color(0xFF6750A4),
    tertiary = Color(0xFF7D5260),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7ECEA),
    outline = Color(0xFFCBD3D0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78D6BC),
    onPrimary = Color(0xFF00382C),
    secondary = Color(0xFFD0BCFF),
    tertiary = Color(0xFFEFB8C8),
    background = Color(0xFF111513),
    surface = Color(0xFF181D1B),
    surfaceVariant = Color(0xFF27302C),
    outline = Color(0xFF3F4945),
)

@Composable
fun YggdrasilTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
