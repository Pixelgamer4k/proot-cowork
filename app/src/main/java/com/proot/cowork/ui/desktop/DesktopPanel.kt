package com.proot.cowork.ui.desktop

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.data.prootcontainer.ProotContainerTarballLocator
import com.proot.cowork.data.rootfs.RootfsTarballLocator
import com.proot.cowork.domain.desktop.TERMUX_STACK_DESKTOP
import com.proot.cowork.domain.importing.ImportPhase
import com.proot.cowork.domain.importing.ImportUiState
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.ui.theme.Motion

@Composable
fun DesktopPanel(
    desktopState: DesktopState,
    importUiState: ImportUiState,
    distroName: String,
    desktopLogHint: String? = null,
    dropDirectoryLabel: String,
    importError: String? = null,
    isImportBusy: Boolean = false,
    onImportPrimary: () -> Unit,
    onImportDroppedFile: () -> Unit,
    onPowerOff: () -> Unit,
    onReboot: () -> Unit,
    onScreenshot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val useWideLayout = desktopState == DesktopState.RUNNING || desktopState == DesktopState.STARTING

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusLabel(desktopState, importUiState, distroName),
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
                .weight(1f)
                .fillMaxWidth()
                .then(
                    if (useWideLayout) {
                        Modifier.aspectRatio(16f / 9f)
                    } else {
                        Modifier.fillMaxSize()
                    },
                )
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (TERMUX_STACK_DESKTOP) {
                when (desktopState) {
                    DesktopState.NO_ROOTFS -> NoProotContainerContent(
                        dropDirectoryLabel = dropDirectoryLabel,
                        importError = importError,
                        isImportBusy = isImportBusy,
                        onImportPrimary = onImportPrimary,
                        onImportDroppedFile = onImportDroppedFile,
                    )
                    DesktopState.IMPORTING -> ImportingContent(importUiState)
                    DesktopState.STOPPED -> StoppedContent(onReboot, desktopLogHint)
                    else -> TermuxStackPanel(
                        modifier = Modifier.fillMaxSize(),
                        desktopState = desktopState,
                    )
                }
            } else when (desktopState) {
                DesktopState.NO_ROOTFS -> NoRootfsContent(
                    dropDirectoryLabel = dropDirectoryLabel,
                    importError = importError,
                    isImportBusy = isImportBusy,
                    onImportPrimary = onImportPrimary,
                    onImportDroppedFile = onImportDroppedFile,
                )
                DesktopState.IMPORTING -> ImportingContent(importUiState)
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
private fun statusLabel(
    desktopState: DesktopState,
    import: ImportUiState,
    distroName: String,
): String {
    if (import.active && desktopState == DesktopState.IMPORTING) {
        return stringResource(
            when (import.phase) {
                ImportPhase.PREPARING -> R.string.import_phase_preparing
                ImportPhase.EXTRACTING -> R.string.import_phase_extracting
                ImportPhase.INSTALLING -> R.string.import_phase_installing
                ImportPhase.FINALIZING -> R.string.import_phase_finalizing
                ImportPhase.STARTING_DESKTOP -> R.string.import_phase_starting
            },
        )
    }
    return if (TERMUX_STACK_DESKTOP) {
        when (desktopState) {
            DesktopState.NO_ROOTFS -> stringResource(R.string.no_proot_container)
            DesktopState.IMPORTING -> stringResource(R.string.proot_container_importing)
            DesktopState.STARTING -> stringResource(R.string.desktop_starting)
            DesktopState.RUNNING -> distroName.ifEmpty { stringResource(R.string.proot_container_ready) }
            DesktopState.STOPPED -> stringResource(R.string.desktop_stopped)
        }
    } else when (desktopState) {
        DesktopState.NO_ROOTFS -> stringResource(R.string.no_rootfs)
        DesktopState.IMPORTING -> stringResource(R.string.rootfs_importing)
        DesktopState.STARTING -> "Starting desktop…"
        DesktopState.RUNNING -> distroName.ifEmpty { stringResource(R.string.rootfs_ready) }
        DesktopState.STOPPED -> stringResource(R.string.desktop_stopped)
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
private fun NoProotContainerContent(
    dropDirectoryLabel: String,
    importError: String?,
    isImportBusy: Boolean,
    onImportPrimary: () -> Unit,
    onImportDroppedFile: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.add_proot_container),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.add_proot_container_hint_short,
                ProotContainerTarballLocator.DEFAULT_FILENAME,
                dropDirectoryLabel,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!importError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = importError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onImportPrimary,
            enabled = !isImportBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isImportBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(stringResource(R.string.import_ubuntu_desktop))
        }
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick = onImportDroppedFile,
            enabled = !isImportBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_dropped_file))
        }
    }
}

@Composable
private fun NoRootfsContent(
    dropDirectoryLabel: String,
    importError: String?,
    isImportBusy: Boolean,
    onImportPrimary: () -> Unit,
    onImportDroppedFile: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.add_rootfs),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.add_rootfs_hint,
                dropDirectoryLabel,
                RootfsTarballLocator.DEFAULT_FILENAME,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!importError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = importError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onImportPrimary,
            enabled = !isImportBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_choose_file))
        }
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick = onImportDroppedFile,
            enabled = !isImportBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_dropped_file))
        }
    }
}

@Composable
private fun ImportingContent(state: ImportUiState) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = Motion.springSmooth,
        label = "importProgress",
    )
    val phaseTitle = stringResource(
        when (state.phase) {
            ImportPhase.PREPARING -> R.string.import_phase_preparing
            ImportPhase.EXTRACTING -> R.string.import_phase_extracting
            ImportPhase.INSTALLING -> R.string.import_phase_installing
            ImportPhase.FINALIZING -> R.string.import_phase_finalizing
            ImportPhase.STARTING_DESKTOP -> R.string.import_phase_starting
        },
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = phaseTitle,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (state.detail.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(animatedProgress * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (state.phase == ImportPhase.EXTRACTING) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.import_extract_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StoppedContent(onReboot: () -> Unit, errorHint: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.desktop_stopped),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
