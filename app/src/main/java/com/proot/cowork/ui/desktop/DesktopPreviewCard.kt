package com.proot.cowork.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.domain.desktop.TermuxStackSession
import com.proot.cowork.domain.desktop.TERMUX_STACK_DESKTOP
import com.proot.cowork.domain.importing.ImportUiState
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.ui.design.CoworkTokens
import com.termux.x11.X11SurfaceHostRegistry

@Composable
fun DesktopPreviewCard(
    desktopState: DesktopState,
    importUiState: ImportUiState,
    dropDirectoryLabel: String,
    importError: String?,
    isImportBusy: Boolean,
    onImportPrimary: () -> Unit,
    onImportDroppedFile: () -> Unit,
    onReboot: () -> Unit,
    desktopLogHint: String?,
    desktopInputEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val x11Ready by TermuxStackSession.x11Ready.collectAsState()
    val showLiveDesktop = desktopState == DesktopState.RUNNING && x11Ready

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(16f / 9f)
            .clip(CoworkTokens.ShapeCard)
            .border(1.dp, CoworkTokens.Border, CoworkTokens.ShapeCard)
            .background(CoworkTokens.Surface),
    ) {
        when {
            showLiveDesktop -> {
                Box(Modifier.fillMaxSize()) {
                    if (TERMUX_STACK_DESKTOP) {
                        TermuxStackPanel(
                            modifier = Modifier.fillMaxSize(),
                            desktopState = desktopState,
                            desktopInputEnabled = desktopInputEnabled,
                        )
                    } else {
                        VncDesktopView(modifier = Modifier.fillMaxSize())
                    }
                    if (desktopInputEnabled) {
                        Surface(
                            modifier = Modifier
                                .align(androidx.compose.ui.Alignment.BottomEnd)
                                .padding(8.dp),
                            shape = CoworkTokens.ShapePill,
                            color = CoworkTokens.Surface.copy(alpha = 0.92f),
                        ) {
                            IconButton(
                                onClick = { X11SurfaceHostRegistry.current?.focusDesktopKeyboard() },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Default.Keyboard, stringResource(R.string.desktop_keyboard), tint = CoworkTokens.Mint)
                            }
                        }
                    }
                }
            }
            desktopState == DesktopState.IMPORTING -> DesktopImportingPlaceholder(importUiState)
            desktopState == DesktopState.STARTING ||
                (desktopState == DesktopState.RUNNING && !x11Ready) -> DesktopBootingPlaceholder()
            desktopState == DesktopState.STOPPED -> DesktopOffPlaceholder()
            else -> DesktopImportPlaceholder(
                onImport = onImportPrimary,
                isBusy = isImportBusy,
            )
        }
    }
}
