package com.proot.cowork.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.proot.cowork.domain.desktop.TermuxStackSession
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.ui.agent.ChatComposer
import com.proot.cowork.ui.components.CoworkBottomNav
import com.proot.cowork.ui.components.CoworkTopBar
import com.proot.cowork.ui.desktop.DesktopPreviewCard
import com.proot.cowork.ui.tabs.AgentsTabContent
import com.proot.cowork.ui.tabs.ChatTabContent
import com.proot.cowork.ui.tabs.FilesTabContent
import com.proot.cowork.ui.tabs.ScheduleTabContent
import com.proot.cowork.ui.tabs.SkillsTabContent
import com.proot.cowork.ui.tabs.TerminalTabContent
import com.proot.cowork.ui.theme.Motion

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
    val stackLogs by TermuxStackSession.logLines.collectAsState()
    val desktopLogs by DesktopSession.logLines.collectAsState()
    val density = LocalDensity.current
    var composerHeightPx by remember { mutableStateOf(0) }
    var composerFocused by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(CoworkTab.Chat) }
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.importFromUri(uri)
    }

    LaunchedEffect(uiState.importError) {
        val message = uiState.importError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearImportError()
    }

    val composerBottomPadding = with(density) { composerHeightPx.toDp() }
    val showChatComposer = selectedTab == CoworkTab.Chat &&
        uiState.desktopState != DesktopState.IMPORTING
    val terminalLogs = remember(stackLogs, desktopLogs) { (stackLogs + desktopLogs).takeLast(120) }
    val artifactsDir = remember { settingsRepository.getArtifactsDir() }
    val skillsDir = remember { settingsRepository.getSkillsDir() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CoworkTopBar(
                distroName = uiState.distroName,
                desktopState = uiState.desktopState,
                onMenuSettings = onNavigateToSettings,
                onMenuImport = if (uiState.desktopState == DesktopState.NO_ROOTFS) {
                    { importLauncher.launch(IMPORT_MIME_TYPES) }
                } else {
                    null
                },
                onScreenshot = viewModel::onScreenshot,
                onReboot = viewModel::onReboot,
                onPowerOff = viewModel::onPowerOff,
            )

            DesktopPreviewCard(
                desktopState = uiState.desktopState,
                importUiState = uiState.importUiState,
                selectedTab = selectedTab,
                dropDirectoryLabel = dropDirectoryLabel,
                importError = uiState.importError,
                isImportBusy = uiState.isImportBusy,
                onImportPrimary = { importLauncher.launch(IMPORT_MIME_TYPES) },
                onImportDroppedFile = viewModel::importAutoDiscover,
                onReboot = viewModel::onReboot,
                desktopLogHint = uiState.desktopLogHint,
                modifier = Modifier.padding(horizontal = 14.dp),
            )

            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(Motion.springSmoothOffset) { direction * it / 4 } + fadeIn(Motion.tweenQuick))
                        .togetherWith(
                            slideOutHorizontally(Motion.springSmoothOffset) { -direction * it / 4 } + fadeOut(Motion.tweenQuick),
                        )
                },
                label = "coworkTab",
            ) { tab ->
                when (tab) {
                    CoworkTab.Chat -> ChatTabContent(
                        messages = uiState.messages,
                        swarmTasks = uiState.swarmTasks,
                        isExecuting = uiState.isExecuting,
                        composerBottomPadding = if (showChatComposer) composerBottomPadding else 0.dp,
                        onQuickPrompt = viewModel::onQuickPrompt,
                    )
                    CoworkTab.Agents -> AgentsTabContent(isExecuting = uiState.isExecuting)
                    CoworkTab.Skills -> SkillsTabContent(skillsDirLabel = skillsDir.absolutePath)
                    CoworkTab.Schedule -> ScheduleTabContent(onScheduleDraft = viewModel::onScheduleDraft)
                    CoworkTab.Files -> FilesTabContent(
                        artifactsDir = artifactsDir,
                        onOpenPath = viewModel::onOpenFilePath,
                    )
                    CoworkTab.Terminal -> TerminalTabContent(
                        logLines = terminalLogs,
                        onCommandSubmit = viewModel::onTerminalCommand,
                    )
                }
            }

            CoworkBottomNav(
                selected = selectedTab,
                onSelect = { selectedTab = it },
            )
        }

        if (showChatComposer) {
            ChatComposer(
                value = uiState.inputText,
                onValueChange = viewModel::onInputChange,
                onSend = viewModel::onSend,
                onStop = viewModel::onStop,
                isExecuting = uiState.isExecuting,
                executionMode = uiState.executionMode,
                onModeChange = viewModel::onModeChange,
                onFocusChange = { composerFocused = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(20f)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(bottom = 62.dp)
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
