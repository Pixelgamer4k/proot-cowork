package com.proot.cowork.ui.desktop

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.proot.cowork.R
import com.proot.cowork.domain.desktop.TermuxStackSession
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
        val x11Ready by TermuxStackSession.x11Ready.collectAsState()
        val showLiveDesktop = desktopState == DesktopState.RUNNING && x11Ready

        Box(Modifier.fillMaxSize()) {
            if (showLiveDesktop) {
                if (TERMUX_STACK_DESKTOP) {
                    TermuxStackPanel(
                        modifier = Modifier.fillMaxSize(),
                        desktopState = desktopState,
                    )
                } else {
                    VncDesktopView(modifier = Modifier.fillMaxSize())
                }
            } else {
                DesktopReferenceMock(Modifier.fillMaxSize())
            }

            when (desktopState) {
                DesktopState.IMPORTING -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(CoworkTokens.Bg.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) { ImportingContent(importUiState) }
                DesktopState.STARTING -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(CoworkTokens.Bg.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CoworkTokens.Mint, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(stringResource(R.string.desktop_starting), color = CoworkTokens.TextSecondary)
                    }
                }
                else -> Unit
            }
        }
    }
}
