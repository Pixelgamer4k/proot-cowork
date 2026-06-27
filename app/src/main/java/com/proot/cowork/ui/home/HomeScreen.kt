package com.proot.cowork.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.data.prootcontainer.ProotContainerRepository
import com.proot.cowork.data.rootfs.RootfsRepository
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.ui.agent.ChatComposer
import com.proot.cowork.ui.components.CoworkBottomNav
import com.proot.cowork.ui.components.CoworkTopBar
import com.proot.cowork.ui.design.CoworkTokens
import com.proot.cowork.ui.desktop.DesktopPreviewCard
import com.proot.cowork.ui.tabs.AgentsTabContent
import com.proot.cowork.ui.tabs.ChatTabContent
import com.proot.cowork.ui.tabs.FilesTabContent
import com.proot.cowork.ui.tabs.SkillsTabContent
import com.proot.cowork.ui.tabs.TerminalTabContent
import com.proot.cowork.ui.theme.Motion
import com.proot.cowork.util.VoiceInputHelper

@Composable
fun HomeScreen(
    settingsRepository: SettingsRepository,
    rootfsRepository: RootfsRepository,
    prootContainerRepository: ProotContainerRepository,
    dropDirectoryLabel: String,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            application = LocalContext.current.applicationContext as android.app.Application,
            settingsRepository = settingsRepository,
            rootfsRepository = rootfsRepository,
            prootContainerRepository = prootContainerRepository,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTab = uiState.selectedTab
    val density = LocalDensity.current
    var composerHeightPx by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val shareContext = LocalContext.current
    var voiceHelper by remember { mutableStateOf<VoiceInputHelper?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.importFromUri(uri)
    }

    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.onAttachFile(uri)
    }

    val fileUploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')
            viewModel.onUploadArtifact(uri, name)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            voiceHelper?.start()
        } else {
            viewModel.onVoiceError(shareContext.getString(com.proot.cowork.R.string.voice_permission_denied))
        }
    }

    DisposableEffect(Unit) {
        onDispose { voiceHelper?.stop() }
    }

    LaunchedEffect(uiState.shareArtifactUri, uiState.shareArtifactUris) {
        val multi = uiState.shareArtifactUris
        val single = uiState.shareArtifactUri
        if (multi.isEmpty() && single == null) return@LaunchedEffect
        val intent = when {
            multi.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(multi))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            multi.size == 1 -> Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, multi.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            else -> Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, single)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        shareContext.startActivity(Intent.createChooser(intent, shareContext.getString(com.proot.cowork.R.string.files_share)))
        viewModel.clearShareArtifactUri()
    }

    LaunchedEffect(uiState.shareTranscriptUri) {
        val uri = uiState.shareTranscriptUri ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, shareContext.getString(com.proot.cowork.R.string.chat_export_title))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        shareContext.startActivity(Intent.createChooser(intent, shareContext.getString(com.proot.cowork.R.string.chat_export)))
        viewModel.clearShareTranscriptUri()
    }

    LaunchedEffect(uiState.importError) {
        uiState.importError?.let { snackbarHostState.showSnackbar(it); viewModel.clearImportError() }
    }

    LaunchedEffect(uiState.chatError) {
        uiState.chatError?.let { snackbarHostState.showSnackbar(it); viewModel.clearChatError() }
    }

    LaunchedEffect(uiState.chatSnackbar) {
        uiState.chatSnackbar?.let { snackbarHostState.showSnackbar(it); viewModel.clearChatSnackbar() }
    }

    val composerBottomPadding = with(density) { composerHeightPx.toDp() }
    val showChatComposer = selectedTab == CoworkTab.Chat && uiState.desktopState != DesktopState.IMPORTING

    val startVoiceInput: () -> Unit = {
        val helper = VoiceInputHelper(
            context = shareContext,
            onPartial = viewModel::onVoiceResult,
            onResult = viewModel::onVoiceResult,
            onError = viewModel::onVoiceError,
        )
        voiceHelper = helper
        val granted = ContextCompat.checkSelfPermission(shareContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            helper.start()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CoworkTokens.Bg)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            CoworkTopBar(
                distroName = uiState.distroName,
                desktopState = uiState.desktopState,
                onMenuSettings = onNavigateToSettings,
                onMenuImport = if (uiState.desktopState == DesktopState.NO_ROOTFS) {
                    { importLauncher.launch(IMPORT_MIME_TYPES) }
                } else null,
                onScreenshot = viewModel::onScreenshot,
                onReboot = viewModel::onReboot,
                onPowerOff = viewModel::onPowerOff,
            )

            DesktopPreviewCard(
                desktopState = uiState.desktopState,
                importUiState = uiState.importUiState,
                dropDirectoryLabel = dropDirectoryLabel,
                importError = uiState.importError,
                isImportBusy = uiState.isImportBusy,
                onImportPrimary = { importLauncher.launch(IMPORT_MIME_TYPES) },
                onImportDroppedFile = viewModel::importAutoDiscover,
                onReboot = viewModel::onReboot,
                desktopLogHint = uiState.desktopLogHint,
                desktopInputEnabled = selectedTab != CoworkTab.Terminal,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(Motion.springSmoothOffset) { dir * it / 5 } + fadeIn(Motion.tweenQuick))
                        .togetherWith(slideOutHorizontally(Motion.springSmoothOffset) { -dir * it / 5 } + fadeOut(Motion.tweenQuick))
                },
                label = "coworkTab",
            ) { tab ->
                when (tab) {
                    CoworkTab.Chat -> ChatTabContent(
                        messages = uiState.messages,
                        swarmResponse = uiState.swarmResponse,
                        isExecuting = uiState.isExecuting,
                        isApiConfigured = uiState.isApiConfigured,
                        awaitingApproval = uiState.awaitingApproval,
                        toolCallCount = uiState.toolCallCount,
                        maxToolCalls = uiState.maxToolCalls,
                        toolLimitReached = uiState.toolLimitReached,
                        shellCommandLog = uiState.shellCommandLog,
                        pendingSkillWrite = uiState.pendingSkillWrite,
                        skillSaveOffer = uiState.skillSaveOffer,
                        composerBottomPadding = if (showChatComposer) composerBottomPadding + 8.dp else 0.dp,
                        onQuickPrompt = viewModel::onQuickPrompt,
                        onUpdateSwarmTask = viewModel::onUpdateSwarmTask,
                        onApprovePlan = viewModel::onApprovePlan,
                        onRejectPlan = viewModel::onRejectPlan,
                        onCancelSubtask = viewModel::onCancelSubtask,
                        onNavigateToSettings = onNavigateToSettings,
                        onNewConversation = viewModel::onClearConversation,
                        onExportTranscript = viewModel::onExportTranscript,
                        onMessageCopied = viewModel::onMessageCopied,
                        onEditUserMessage = viewModel::onEditUserMessage,
                        onRegenerateFrom = viewModel::onRegenerateFrom,
                        onApproveSkillWrite = viewModel::onApproveSkillWrite,
                        onRejectSkillWrite = viewModel::onRejectSkillWrite,
                        onAcceptSkillSaveOffer = viewModel::onAcceptSkillSaveOffer,
                        onDismissSkillSaveOffer = viewModel::onDismissSkillSaveOffer,
                    )
                    CoworkTab.Agents -> AgentsTabContent(
                        agentStates = uiState.agentStates,
                        isExecuting = uiState.isExecuting,
                        maxAgentPool = uiState.maxAgentPool,
                    )
                    CoworkTab.Skills -> SkillsTabContent(
                        skills = uiState.skills,
                        skillsDirLabel = settingsRepository.getSkillsDir().absolutePath,
                        onToggleSkill = viewModel::onToggleSkill,
                    )
                    CoworkTab.Files -> FilesTabContent(
                        entries = uiState.guestFiles,
                        currentPath = uiState.filesPath,
                        isLoading = uiState.filesLoading,
                        error = uiState.filesError,
                        viewMode = uiState.filesViewMode,
                        sortOrder = uiState.filesSortOrder,
                        containerInstalled = uiState.containerInstalled,
                        selectionMode = uiState.filesSelectionMode,
                        selectedPaths = uiState.filesSelectedPaths,
                        onNavigateUp = viewModel::onFilesNavigateUp,
                        onNavigateToPath = viewModel::onFilesNavigateToPath,
                        onOpenEntry = viewModel::onFilesOpenEntry,
                        onToggleSelect = viewModel::onFilesToggleSelect,
                        onEnterSelectionMode = viewModel::onFilesEnterSelectionMode,
                        onExitSelectionMode = viewModel::onFilesExitSelectionMode,
                        onShareSelected = viewModel::onFilesShareSelected,
                        onDownloadSelected = viewModel::onFilesDownloadSelected,
                        onDeleteSelected = viewModel::onFilesDeleteSelected,
                        onRenameSelected = viewModel::onFilesRenameSelected,
                        onUpload = { fileUploadLauncher.launch(arrayOf("*/*")) },
                        onCreateFolder = viewModel::onFilesCreateFolder,
                        onViewModeChange = viewModel::onFilesViewModeChange,
                        onSortOrderChange = viewModel::onFilesSortOrderChange,
                    )
                    CoworkTab.Terminal -> TerminalTabContent(
                        containerInstalled = uiState.containerInstalled,
                        isActive = selectedTab == CoworkTab.Terminal,
                    )
                }
            }

            if (showChatComposer) {
                ChatComposer(
                    value = uiState.inputText,
                    onValueChange = viewModel::onInputChange,
                    onSend = viewModel::onSend,
                    onStop = viewModel::onStop,
                    isExecuting = uiState.isExecuting,
                    isApiConfigured = uiState.isApiConfigured,
                    awaitingApproval = uiState.awaitingApproval,
                    executionMode = uiState.executionMode,
                    onModeChange = viewModel::onModeChange,
                    onFocusChange = { },
                    artifactFileNames = uiState.composerArtifactNames,
                    onPickFile = { attachLauncher.launch(arrayOf("*/*", "text/*", "application/*")) },
                    onAttachArtifact = viewModel::onAttachArtifact,
                    onAddContextBlock = viewModel::onAddContextBlock,
                    onSpeak = startVoiceInput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .onSizeChanged { composerHeightPx = it.height },
                )
            }

            CoworkBottomNav(selected = selectedTab, onSelect = viewModel::selectTab)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(30f),
        )
    }
}

private val IMPORT_MIME_TYPES = arrayOf("*/*", "application/gzip", "application/x-gzip", "application/octet-stream", "application/x-compressed-tar")
