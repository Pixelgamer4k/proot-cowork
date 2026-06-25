package com.proot.cowork.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.data.rootfs.RootfsRepository
import com.proot.cowork.ui.agent.AgentPanel
import com.proot.cowork.ui.agent.ChatComposer
import com.proot.cowork.ui.desktop.DesktopPanel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    settingsRepository: SettingsRepository,
    rootfsRepository: RootfsRepository,
    dropDirectoryLabel: String,
    onImportDroppedFile: () -> Unit,
    onImportChooseFile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(settingsRepository, rootfsRepository),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val density = LocalDensity.current
    var composerHeightPx by remember { mutableStateOf(0) }
    var composerFocused by remember { mutableStateOf(false) }

    val composerBottomPadding = with(density) { composerHeightPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DesktopPanel(
                desktopState = uiState.desktopState,
                importProgress = uiState.importProgress,
                distroName = uiState.distroName,
                desktopLogHint = uiState.desktopLogHint,
                dropDirectoryLabel = dropDirectoryLabel,
                onImportDroppedFile = onImportDroppedFile,
                onImportChooseFile = onImportChooseFile,
                onPowerOff = viewModel::onPowerOff,
                onReboot = viewModel::onReboot,
                onScreenshot = viewModel::onScreenshot,
                modifier = Modifier.weight(0.46f),
            )
            AgentPanel(
                messages = uiState.messages,
                swarmTasks = uiState.swarmTasks,
                executionMode = uiState.executionMode,
                isExecuting = uiState.isExecuting,
                onModeChange = viewModel::onModeChange,
                onOpenTerminal = viewModel::onOpenTerminal,
                onOpenBrowser = viewModel::onOpenBrowser,
                onOpenSkills = viewModel::onOpenSkills,
                onNavigateToSettings = onNavigateToSettings,
                composerBottomPadding = composerBottomPadding,
                scrollOnInput = composerFocused,
                modifier = Modifier.weight(0.54f),
            )
        }

        ChatComposer(
            value = uiState.inputText,
            onValueChange = viewModel::onInputChange,
            onSend = viewModel::onSend,
            onStop = viewModel::onStop,
            isExecuting = uiState.isExecuting,
            onFocusChange = { composerFocused = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(20f)
                .imePadding()
                .navigationBarsPadding()
                .onSizeChanged { composerHeightPx = it.height },
        )
    }
}
