package com.proot.cowork.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.domain.desktop.TERMUX_STACK_DESKTOP
import com.proot.cowork.domain.proot.DesktopState

@Composable
fun DesktopPanel(
    desktopState: DesktopState,
    importProgress: Float,
    distroName: String,
    desktopLogHint: String? = null,
    dropDirectoryLabel: String,
    onImportDroppedFile: () -> Unit,
    onImportChooseFile: () -> Unit,
    onPowerOff: () -> Unit,
    onReboot: () -> Unit,
    onScreenshot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (TERMUX_STACK_DESKTOP) {
                    "Termux + X11"
                } else when (desktopState) {
                    DesktopState.NO_ROOTFS -> stringResource(R.string.no_rootfs)
                    DesktopState.IMPORTING -> stringResource(R.string.rootfs_importing)
                    DesktopState.STARTING -> "Starting desktop…"
                    DesktopState.RUNNING -> distroName.ifEmpty { stringResource(R.string.rootfs_ready) }
                    DesktopState.STOPPED -> "Desktop stopped"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (desktopState == DesktopState.RUNNING || desktopState == DesktopState.STOPPED) {
                Row {
                    IconButton(onClick = onScreenshot) {
                        Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.screenshot))
                    }
                    IconButton(onClick = onReboot) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reboot))
                    }
                    IconButton(onClick = onPowerOff) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = stringResource(R.string.power_off))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (TERMUX_STACK_DESKTOP) {
                TermuxStackPanel(modifier = Modifier.fillMaxSize())
            } else when (desktopState) {
                DesktopState.NO_ROOTFS -> NoRootfsContent(
                    dropDirectoryLabel = dropDirectoryLabel,
                    onImportDroppedFile = onImportDroppedFile,
                    onImportChooseFile = onImportChooseFile,
                )
                DesktopState.IMPORTING -> ImportingContent(importProgress)
                DesktopState.STARTING -> VncDesktopWithOverlay("Booting XFCE over VNC…")
                DesktopState.RUNNING -> VncDesktopView(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
                DesktopState.STOPPED -> StoppedContent(onReboot, desktopLogHint)
            }
        }
    }
}

@Composable
private fun VncDesktopWithOverlay(message: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        VncDesktopView(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(message)
        }
    }
}

@Composable
private fun NoRootfsContent(
    dropDirectoryLabel: String,
    onImportDroppedFile: () -> Unit,
    onImportChooseFile: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.add_rootfs),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.add_rootfs_hint,
                dropDirectoryLabel,
                com.proot.cowork.data.rootfs.RootfsTarballLocator.DEFAULT_FILENAME,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        FilledTonalButton(onClick = onImportDroppedFile) {
            Text(stringResource(R.string.import_dropped_file))
        }
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(onClick = onImportChooseFile) {
            Text(stringResource(R.string.import_choose_file))
        }
    }
}

@Composable
private fun ImportingContent(progress: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.rootfs_importing))
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(0.6f),
        )
    }
}

@Composable
private fun StoppedContent(onReboot: () -> Unit, errorHint: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Text("Desktop powered off", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!errorHint.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                errorHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        FilledTonalButton(onClick = onReboot) {
            Text(stringResource(R.string.reboot))
        }
    }
}
