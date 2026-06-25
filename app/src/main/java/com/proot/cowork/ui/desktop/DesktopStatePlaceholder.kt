package com.proot.cowork.ui.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.domain.importing.ImportUiState
import com.proot.cowork.ui.design.CoworkTokens
import com.proot.cowork.ui.theme.Motion

@Composable
fun DesktopImportPlaceholder(
    onImport: () -> Unit,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(if (isBusy) 0.92f else 1f, Motion.springSnappy, label = "importScale")
    Box(
        modifier = modifier
            .fillMaxSize()
            .desktopBackdrop(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    enabled = !isBusy,
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = onImport,
                )
                .padding(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size((64 * scale).dp)
                    .clip(CircleShape)
                    .background(CoworkTokens.SurfaceElevated)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = CoworkTokens.Mint,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.import_ubuntu_desktop),
                        tint = CoworkTokens.Mint,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun DesktopBootingPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().desktopBackdrop(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = CoworkTokens.Mint,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.size(10.dp))
            Text(
                stringResource(R.string.desktop_booting),
                color = CoworkTokens.TextSecondary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun DesktopOffPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().desktopBackdrop(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.PowerSettingsNew,
                contentDescription = null,
                tint = CoworkTokens.TextMuted,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.desktop_off),
                color = CoworkTokens.TextMuted,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun DesktopImportingPlaceholder(
    state: ImportUiState,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        state.progress.coerceIn(0f, 1f),
        Motion.springSmooth,
        label = "importProgress",
    )
    Box(
        modifier = modifier.fillMaxSize().desktopBackdrop(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = CoworkTokens.Mint,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.size(14.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(width = 120.dp, height = 3.dp),
                color = CoworkTokens.Mint,
                trackColor = CoworkTokens.Border,
            )
        }
    }
}

private fun Modifier.desktopBackdrop(): Modifier = background(
    Brush.verticalGradient(
        listOf(
            CoworkTokens.SurfaceElevated,
            CoworkTokens.Surface,
        ),
    ),
)
