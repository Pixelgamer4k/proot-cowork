package com.proot.cowork.ui.desktop

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.proot.cowork.domain.desktop.TERMUX_STACK_DESKTOP
import com.proot.cowork.domain.importing.ImportUiState
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.ui.components.DesktopChromeFrame
import com.proot.cowork.ui.design.CoworkTokens
import com.proot.cowork.ui.home.CoworkTab
import com.proot.cowork.ui.theme.Motion

@Composable
fun DesktopPreviewCard(
    desktopState: DesktopState,
    importUiState: ImportUiState,
    selectedTab: CoworkTab,
    dropDirectoryLabel: String,
    importError: String?,
    isImportBusy: Boolean,
    onImportPrimary: () -> Unit,
    onImportDroppedFile: () -> Unit,
    onReboot: () -> Unit,
    desktopLogHint: String?,
    modifier: Modifier = Modifier,
) {
    val targetHeight = when (selectedTab) {
        CoworkTab.Terminal -> CoworkTokens.DesktopHeightTerminal
        CoworkTab.Chat -> CoworkTokens.DesktopHeightChat
        else -> CoworkTokens.DesktopHeightDefault
    }
    val height by animateDpAsState(targetHeight, Motion.springSmoothDp, label = "desktopHeight")

    DesktopChromeFrame(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 16.dp),
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
            DesktopState.RUNNING -> VncDesktopView(modifier = Modifier.fillMaxSize())
            DesktopState.STOPPED -> StoppedContent(onReboot, desktopLogHint)
        }
    }
}
