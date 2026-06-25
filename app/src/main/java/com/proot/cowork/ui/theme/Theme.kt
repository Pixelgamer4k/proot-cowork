package com.proot.cowork.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CoworkDarkScheme = darkColorScheme(
    primary = Color(0xFF4FD1C5),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF0F3D3B),
    onPrimaryContainer = Color(0xFFB8FFF5),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFFF97316),
    background = Color(0xFF050505),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF0C0C0E),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1A1A1E),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF3F4C5E),
    error = Color(0xFFFF6B6B),
)

@Composable
fun ProotCoworkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CoworkDarkScheme,
        typography = AppTypography,
        content = content,
    )
}
