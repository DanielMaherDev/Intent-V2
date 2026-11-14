package com.intent.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Sand = Color(0xFFF7F2EB)
private val Ink = Color(0xFF1A2B3C)
private val Accent = Color(0xFF2F6F5E)
private val Mist = Color(0xFFE6E0D6)

private val Scheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Sand,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    secondary = Mist,
    onSecondary = Ink
)

@Composable
fun IntentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        content = content
    )
}
