package com.proot.cowork.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.proot.cowork.data.prootcontainer.ProotContainerRepository
import com.proot.cowork.data.rootfs.RootfsRepository
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.ui.agent.AgentPanel
import com.proot.cowork.ui.agent.ChatComposer
import com.proot.cowork.ui.desktop.DesktopPanel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    settingsRepository: SettingsRepository,
    rootfsRepository: RootfsRepository,
    prootContainerRepository: ProotContainerRepository,
    dropDirectoryLabel: String,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            settingsRepository,
            rootfsRepository,
            prootContainerRepository,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val density = LocalDensity.current
    var composerHeightPx by remember { mutableStateOf(0) }
    var composerFocused by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFromUri(uri)
        }
    }

    LaunchedEffect(uiState.importError) {
        val message = uiState.importError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearImportError()
    }

    val composerBottomPadding = with(density) { composerHeightPx.toDp() }
    val showChatComposer = uiState.desktopState == DesktopState.RUNNING
    val desktopWeight = if (uiState.desktopState == DesktopState.NO_ROOTFS) 0.58f else 0.46f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DesktopPanel(
                desktopState = uiState.desktopState,
                importUiState = uiState.importUiState,
                distroName = uiState.distroName,
                desktopLogHint = uiState.desktopLogHint,
                dropDirectoryLabel = dropDirectoryLabel,
                importError = uiState.importError,
                isImportBusy = uiState.isImportBusy,
                onImportPrimary = {
                    importLauncher.launch(IMPORT_MIME_TYPES)
                },
                onImportDroppedFile = viewModel::importAutoDiscover,
                onPowerOff = viewModel::onPowerOff,
                onReboot = viewModel::onReboot,
                onScreenshot = viewModel::onScreenshot,
                modifier = Modifier.weight(desktopWeight),
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
                composerBottomPadding = if (showChatComposer) composerBottomPadding else 0.dp,
                scrollOnInput = composerFocused,
                modifier = Modifier.weight(1f - desktopWeight),
            )
        }

        if (showChatComposer) {
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .zIndex(30f),
        )
    }
}

private val IMPORT_MIME_TYPES = arrayOf(
    "*/*",
    "application/gzip",
    "application/x-gzip",
    "application/octet-stream",
    "application/x-compressed-tar",
)
