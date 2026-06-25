package com.proot.cowork.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
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
import com.proot.cowork.ui.theme.Motion

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
        targetValue = if (online) 1f else 0.85f,
        animationSpec = Motion.springSnappy,
        label = "statusDot",
    )
    val statusColor by animateColorAsState(
        targetValue = if (online) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        animationSpec = Motion.tweenMedium,
        label = "statusColor",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings)) },
                    onClick = {
                        menuOpen = false
                        onMenuSettings()
                    },
                )
                if (onMenuImport != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_ubuntu_desktop)) },
                        onClick = {
                            menuOpen = false
                            onMenuImport()
                        },
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
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = distroName.ifBlank { "ubuntu" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .scale(statusScale)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
        }

        Row {
            IconButton(onClick = onScreenshot, enabled = online) {
                Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.screenshot))
            }
            IconButton(
                onClick = onReboot,
                enabled = online || desktopState == DesktopState.STOPPED,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reboot))
            }
            IconButton(
                onClick = onPowerOff,
                enabled = online || desktopState == DesktopState.STOPPED,
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = stringResource(R.string.power_off))
            }
        }
    }
}
