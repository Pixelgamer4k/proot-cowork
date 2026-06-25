package com.proot.cowork.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.proot.cowork.ui.design.CoworkTokens

private val CoworkScheme = darkColorScheme(
    primary = CoworkTokens.Mint,
    onPrimary = CoworkTokens.SpeakFg,
    primaryContainer = CoworkTokens.Mint.copy(alpha = 0.15f),
    onPrimaryContainer = CoworkTokens.Mint,
    secondary = CoworkTokens.TextSecondary,
    onSecondary = CoworkTokens.Bg,
    secondaryContainer = CoworkTokens.SurfaceElevated,
    onSecondaryContainer = CoworkTokens.TextPrimary,
    tertiary = CoworkTokens.UbuntuOrange,
    background = CoworkTokens.Bg,
    onBackground = CoworkTokens.TextPrimary,
    surface = CoworkTokens.Surface,
    onSurface = CoworkTokens.TextPrimary,
    surfaceVariant = CoworkTokens.SurfaceElevated,
    onSurfaceVariant = CoworkTokens.TextSecondary,
    outline = CoworkTokens.Border,
    error = CoworkTokens.Failed,
)

@Composable
fun ProotCoworkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CoworkScheme,
        typography = AppTypography,
        content = content,
    )
}
