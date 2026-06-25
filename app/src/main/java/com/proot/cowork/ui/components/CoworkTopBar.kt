package com.proot.cowork.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.ui.design.CoworkTokens
import com.proot.cowork.ui.theme.Motion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CoworkTopBar(
    distroName: String,
    desktopState: DesktopState,
    onMenuSettings: () -> Unit,
    onMenuImport: (() -> Unit)?,
    onScreenshot: () -> Unit,
    onReboot: () -> Unit,
    onPowerOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val online = desktopState == DesktopState.RUNNING
    val statusScale by animateFloatAsState(
        targetValue = if (online) 1f else 0.9f,
        animationSpec = Motion.springSnappy,
        label = "statusScale",
    )
    val statusColor by animateColorAsState(
        targetValue = if (online) CoworkTokens.Mint else CoworkTokens.TextMuted,
        animationSpec = Motion.tweenColorQuick,
        label = "statusColor",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = stringResource(R.string.menu),
                    tint = CoworkTokens.TextPrimary,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings)) },
                    onClick = { menuOpen = false; onMenuSettings() },
                )
                if (onMenuImport != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_ubuntu_desktop)) },
                        onClick = { menuOpen = false; onMenuImport() },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(CoworkTokens.UbuntuOrange),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(CoworkTokens.SpeakBg.copy(alpha = 0.9f)),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = distroName.ifBlank { "ubuntu" },
                color = CoworkTokens.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .scale(statusScale)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
        }

        Row {
            val enabledTint = CoworkTokens.TextPrimary
            val disabledTint = CoworkTokens.TextMuted
            IconButton(onClick = onScreenshot, enabled = online) {
                Icon(Icons.Default.CameraAlt, stringResource(R.string.screenshot), tint = if (online) enabledTint else disabledTint)
            }
            IconButton(onClick = onReboot, enabled = online || desktopState == DesktopState.STOPPED) {
                Icon(Icons.Default.Refresh, stringResource(R.string.reboot), tint = enabledTint)
            }
            IconButton(onClick = onPowerOff, enabled = online || desktopState == DesktopState.STOPPED) {
                Icon(Icons.Default.PowerSettingsNew, stringResource(R.string.power_off), tint = enabledTint)
            }
        }
    }
}

@Composable
fun DesktopChromeTitleBar(modifier: Modifier = Modifier) {
    val time = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CoworkTokens.DesktopTitleBar.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Applications", color = CoworkTokens.TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        Text(time, color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun DesktopChromeFrame(
    modifier: Modifier = Modifier,
    showTitleBar: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(CoworkTokens.ShapeCard)
            .border(1.dp, CoworkTokens.Border, CoworkTokens.ShapeCard)
            .background(CoworkTokens.Surface),
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
            if (showTitleBar) DesktopChromeTitleBar()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                content()
            }
        }
    }
}
