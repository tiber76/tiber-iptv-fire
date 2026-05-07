package com.tiberiptv.fire

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TiberColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF8E7BFF),
    onPrimary = Color(0xFF111225),
    secondary = Color(0xFF47D3C2),
    onSecondary = Color(0xFF061F1C),
    background = Color(0xFF0A0C1A),
    onBackground = Color(0xFFF6F4FF),
    surface = Color(0xFF161829),
    onSurface = Color(0xFFF6F4FF),
    surfaceVariant = Color(0xFF22243B),
    onSurfaceVariant = Color(0xFFC7C3E3),
    outline = Color(0xFF4A4D76),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun TiberTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TiberColorScheme,
        content = content
    )
}
