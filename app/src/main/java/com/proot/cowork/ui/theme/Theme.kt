package com.proot.cowork.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CoworkDarkScheme = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF00332E),
    primaryContainer = Color(0xFF0D4F4A),
    onPrimaryContainer = Color(0xFFB8FFF5),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFFA78BFA),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569),
)

@Composable
fun ProotCoworkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CoworkDarkScheme,
        typography = AppTypography,
        content = content,
    )
}
