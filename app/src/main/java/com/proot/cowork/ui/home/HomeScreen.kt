package com.proot.cowork.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proot.cowork.R
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.data.rootfs.RootfsRepository
import com.proot.cowork.ui.agent.AgentPanel
import com.proot.cowork.ui.desktop.DesktopPanel

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
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
                modifier = Modifier.weight(9f),
            )
            AgentPanel(
                messages = uiState.messages,
                swarmTasks = uiState.swarmTasks,
                executionMode = uiState.executionMode,
                inputText = uiState.inputText,
                isExecuting = uiState.isExecuting,
                onModeChange = viewModel::onModeChange,
                onInputChange = viewModel::onInputChange,
                onSend = viewModel::onSend,
                onOpenTerminal = viewModel::onOpenTerminal,
                onOpenBrowser = viewModel::onOpenBrowser,
                onOpenSkills = viewModel::onOpenSkills,
                modifier = Modifier.weight(11f),
            )
        }
    }
}
