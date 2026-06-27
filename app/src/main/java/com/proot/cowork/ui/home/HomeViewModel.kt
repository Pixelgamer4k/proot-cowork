package com.proot.cowork.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proot.cowork.data.chat.ChatHistoryStore
import com.proot.cowork.data.chat.ChatTranscriptExporter
import com.proot.cowork.data.files.GuestFileEntry
import com.proot.cowork.data.files.GuestFileRepository
import com.proot.cowork.data.files.GuestPaths
import com.proot.cowork.data.skills.SkillRepository
import com.proot.cowork.data.llm.LlmEndpoint
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.data.prootcontainer.ProotContainerRepository
import com.proot.cowork.data.rootfs.ImportResult
import com.proot.cowork.data.rootfs.RootfsRepository
import com.proot.cowork.domain.agent.AgentExecutionSession
import com.proot.cowork.domain.agent.AgentExecutionSnapshot
import com.proot.cowork.domain.agent.AgentMessage
import com.proot.cowork.domain.agent.CoworkAgentRunner
import com.proot.cowork.domain.agent.DEFAULT_MAX_AGENT_POOL
import com.proot.cowork.domain.agent.ExecutionMode
import com.proot.cowork.domain.agent.MessageRole
import com.proot.cowork.domain.agent.AgentRunController
import com.proot.cowork.domain.agent.DEFAULT_MAX_TOOL_CALLS
import com.proot.cowork.domain.agent.ShellCommandLogEntry
import com.proot.cowork.domain.agent.SwarmAgentState
import com.proot.cowork.domain.agent.SwarmAgentType
import com.proot.cowork.domain.agent.PlanStep
import com.proot.cowork.domain.agent.SwarmOutputParser
import com.proot.cowork.domain.agent.SwarmPhase
import com.proot.cowork.domain.agent.SwarmResponse
import com.proot.cowork.domain.agent.SwarmTask
import com.proot.cowork.domain.agent.SwarmResultType
import com.proot.cowork.domain.agent.TaskPlan
import com.proot.cowork.domain.agent.TaskStatus
import com.proot.cowork.domain.desktop.TERMUX_STACK_DESKTOP
import com.proot.cowork.domain.importing.ImportPhase
import com.proot.cowork.domain.importing.ImportSession
import com.proot.cowork.domain.importing.ImportUiState
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.domain.skills.PendingSkillWrite
import com.proot.cowork.domain.skills.SkillApprovalSession
import com.proot.cowork.domain.skills.SkillDefinition
import com.proot.cowork.domain.skills.SkillSaveOffer
import com.proot.cowork.termux.x11.DesktopScreenshot
import com.proot.cowork.data.proot.ProotGuestShellExecutor
import com.proot.cowork.service.AgentExecutionService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.proot.cowork.util.AttachmentReader
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

data class HomeUiState(
    val desktopState: DesktopState = DesktopState.NO_ROOTFS,
    val importUiState: ImportUiState = ImportUiState(),
    val distroName: String = "",
    val desktopLogHint: String? = null,
    val messages: List<AgentMessage> = emptyList(),
    val swarmTasks: List<SwarmTask> = emptyList(),
    val agentStates: List<SwarmAgentState> = SwarmAgentType.entries.map { SwarmAgentState(it) },
    val executionMode: ExecutionMode = ExecutionMode.SWARM,
    val inputText: String = "",
    val isExecuting: Boolean = false,
    val awaitingApproval: Boolean = false,
    val pendingPlan: TaskPlan? = null,
    val maxAgentPool: Int = DEFAULT_MAX_AGENT_POOL,
    val importError: String? = null,
    val isImportBusy: Boolean = false,
    val isApiConfigured: Boolean = false,
    val chatError: String? = null,
    val swarmResponse: SwarmResponse? = null,
    val toolCallCount: Int = 0,
    val maxToolCalls: Int = DEFAULT_MAX_TOOL_CALLS,
    val toolLimitReached: Boolean = false,
    val shellCommandLog: List<ShellCommandLogEntry> = emptyList(),
    val cancellationMessage: String? = null,
    val chatSnackbar: String? = null,
    val shareTranscriptUri: Uri? = null,
    val skills: List<SkillDefinition> = emptyList(),
    val pendingSkillWrite: PendingSkillWrite? = null,
    val skillSaveOffer: SkillSaveOffer? = null,
    val guestFiles: List<GuestFileEntry> = emptyList(),
    val filesPath: String = GuestPaths.ARTIFACTS_DIR,
    val filesLoading: Boolean = false,
    val filesError: String? = null,
    val shareArtifactUri: Uri? = null,
    val containerInstalled: Boolean = false,
    val selectedTab: CoworkTab = CoworkTab.Chat,
    val composerArtifactNames: List<String> = emptyList(),
)

class HomeViewModel(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val rootfsRepository: RootfsRepository,
    private val prootContainerRepository: ProotContainerRepository,
) : ViewModel() {

    private val agentRunner = CoworkAgentRunner(application)
    private val chatHistoryStore = ChatHistoryStore(application)
    private val transcriptExporter = ChatTranscriptExporter(application)
    private val skillRepository = SkillRepository(application)
    private val guestFileRepository = GuestFileRepository(application)
    private val guestShell = ProotGuestShellExecutor(application)
    private val localState = MutableStateFlow(HomeUiState())
    private var chatJob: Job? = null
    private var agentWasRunning = false
    private var lastUserTask: String? = null

    init {
        viewModelScope.launch {
            skillRepository.ensureSkillsDir()
            refreshSkills()
            guestFileRepository.migrateHostArtifactsIfNeeded(application)
            guestFileRepository.ensureArtifactsDir()
            val names = guestFileRepository.listArtifactNames()
            localState.update { it.copy(composerArtifactNames = names) }
            refreshGuestFiles()
        }

        viewModelScope.launch {
            SkillApprovalSession.pending.collect { pending ->
                localState.update { it.copy(pendingSkillWrite = pending) }
            }
        }

        viewModelScope.launch {
            val saved = chatHistoryStore.load()
            if (saved.messages.isNotEmpty()) {
                localState.update {
                    it.copy(messages = saved.messages, executionMode = saved.executionMode)
                }
            }
        }

        viewModelScope.launch {
            localState
                .map { it.messages to it.executionMode }
                .distinctUntilChanged()
                .debounce(400)
                .collect { (messages, mode) ->
                    if (messages.isNotEmpty()) {
                        chatHistoryStore.save(messages, mode)
                    }
                }
        }

        viewModelScope.launch {
            AgentExecutionSession.snapshot.collect { snap ->
                if (agentWasRunning && !snap.isRunning) {
                    maybeOfferSkillSave(snap)
                }
                agentWasRunning = snap.isRunning
                localState.update { local ->
                    val mergedMessages = if (snap.messages.isNotEmpty()) {
                        mergeMessages(local.messages, snap.messages)
                    } else {
                        local.messages
                    }
                    val tasks = snap.swarmTasks.ifEmpty { local.swarmTasks }
                    val executing = snap.isRunning || (chatJob?.isActive == true)
                    local.copy(
                        messages = mergedMessages,
                        swarmTasks = tasks,
                        agentStates = snap.agentStates,
                        isExecuting = executing,
                        toolCallCount = snap.toolCallCount,
                        maxToolCalls = snap.maxToolCalls,
                        toolLimitReached = snap.toolLimitReached,
                        shellCommandLog = snap.shellCommandLog,
                        cancellationMessage = snap.cancellationMessage,
                        swarmResponse = refreshSwarmResponse(
                            existing = local.swarmResponse,
                            messages = mergedMessages,
                            tasks = tasks,
                            isExecuting = executing,
                            awaitingApproval = local.awaitingApproval,
                            toolCallCount = snap.toolCallCount,
                            maxToolCalls = snap.maxToolCalls,
                            toolLimitReached = snap.toolLimitReached,
                            shellCommandLog = snap.shellCommandLog,
                            cancellationMessage = snap.cancellationMessage,
                        ),
                    )
                }
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            settingsRepository.rootfsState,
            settingsRepository.llmConfig,
            ImportSession.state,
        ) { rootfs, llm, import -> Triple(rootfs, llm, import) },
        combine(
            DesktopSession.state,
            DesktopSession.logLines,
            localState,
        ) { desktop, logs, local -> Triple(desktop, logs, local) },
    ) { (rootfs, llm, import), (desktop, logs, local) ->
        local.copy(
            desktopState = resolveDesktopState(rootfs.isInstalled, rootfs.isImporting, import, desktop),
            importUiState = import,
            distroName = rootfs.distroName,
            isApiConfigured = LlmEndpoint.isConfigured(llm),
            containerInstalled = rootfs.isInstalled,
            desktopLogHint = if (desktop == DesktopState.STOPPED) {
                logs.lastOrNull(::looksLikeDesktopError) ?: logs.lastOrNull()
            } else {
                null
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun selectTab(tab: CoworkTab) {
        localState.update { it.copy(selectedTab = tab) }
        if (tab == CoworkTab.Files) {
            viewModelScope.launch { refreshGuestFiles() }
        }
    }

    fun clearShareArtifactUri() {
        localState.update { it.copy(shareArtifactUri = null) }
    }

    private suspend fun refreshGuestFiles() {
        val path = localState.value.filesPath
        localState.update { it.copy(filesLoading = true, filesError = null) }
        guestFileRepository.ensureArtifactsDir()
        val result = guestFileRepository.listDirectory(path)
        localState.update { state ->
            result.fold(
                onSuccess = { entries ->
                    state.copy(
                        guestFiles = entries,
                        filesLoading = false,
                        filesError = null,
                        composerArtifactNames = if (path == GuestPaths.ARTIFACTS_DIR) {
                            entries.filter { !it.isDirectory }.map { it.name }
                        } else {
                            state.composerArtifactNames
                        },
                    )
                },
                onFailure = { err ->
                    state.copy(guestFiles = emptyList(), filesLoading = false, filesError = err.message)
                },
            )
        }
    }

    fun onFilesRefresh() {
        viewModelScope.launch { refreshGuestFiles() }
    }

    fun onFilesNavigateUp() {
        val parent = GuestFileRepository.parentPath(localState.value.filesPath) ?: return
        localState.update { it.copy(filesPath = parent) }
        viewModelScope.launch { refreshGuestFiles() }
    }

    fun onFilesGoHome() {
        localState.update { it.copy(filesPath = GuestPaths.ARTIFACTS_DIR) }
        viewModelScope.launch { refreshGuestFiles() }
    }

    fun onFilesOpenEntry(entry: GuestFileEntry) {
        if (entry.isDirectory) {
            localState.update { it.copy(filesPath = entry.guestPath) }
            viewModelScope.launch { refreshGuestFiles() }
        } else {
            viewModelScope.launch {
                val (name, snippet) = guestFileRepository.readTextSnippet(entry.guestPath)
                appendAttachmentToInput(name, snippet)
                selectTab(CoworkTab.Chat)
                localState.update {
                    it.copy(chatSnackbar = application.getString(com.proot.cowork.R.string.files_attached_to_chat, name))
                }
            }
        }
    }

    fun onFilesNewFolder() {
        viewModelScope.launch {
            val name = "folder_${System.currentTimeMillis()}"
            if (guestFileRepository.createDirectory(localState.value.filesPath, name)) {
                refreshGuestFiles()
            }
        }
    }

    fun onShareArtifact(guestPath: String) {
        viewModelScope.launch {
            val file = guestFileRepository.pullToCache(guestPath) ?: return@launch
            val uri = androidx.core.content.FileProvider.getUriForFile(
                application,
                "${application.packageName}.fileprovider",
                file,
            )
            localState.update { it.copy(shareArtifactUri = uri) }
        }
    }

    fun onDeleteArtifact(guestPath: String) {
        viewModelScope.launch {
            if (guestFileRepository.delete(guestPath)) {
                refreshGuestFiles()
                localState.update {
                    it.copy(chatSnackbar = application.getString(com.proot.cowork.R.string.files_deleted))
                }
            }
        }
    }

    fun onUploadArtifact(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            val path = localState.value.filesPath
            val saved = guestFileRepository.uploadFromUri(application, uri, displayName, path)
            refreshGuestFiles()
            localState.update {
                it.copy(
                    chatSnackbar = if (saved != null) {
                        application.getString(com.proot.cowork.R.string.files_uploaded)
                    } else {
                        application.getString(com.proot.cowork.R.string.files_upload_failed)
                    },
                )
            }
        }
    }

    private suspend fun refreshSkills() {
        val discovered = skillRepository.discover()
        localState.update { it.copy(skills = discovered) }
    }

    private fun maybeOfferSkillSave(snap: AgentExecutionSnapshot) {
        if (snap.cancellationMessage != null || snap.toolLimitReached) return
        if (localState.value.skillSaveOffer != null || localState.value.pendingSkillWrite != null) return
        val userTask = lastUserTask
            ?: snap.messages.lastOrNull { it.role == MessageRole.USER }?.content
            ?: return
        val offer = skillRepository.buildSaveOffer(
            userTask = userTask,
            toolCallCount = snap.toolCallCount,
            shellLog = snap.shellCommandLog,
        ) ?: return
        localState.update { it.copy(skillSaveOffer = offer) }
    }

    fun onToggleSkill(skillId: String, enabled: Boolean) {
        viewModelScope.launch {
            skillRepository.setEnabled(skillId, enabled)
            refreshSkills()
        }
    }

    fun onApproveSkillWrite() {
        val pending = localState.value.pendingSkillWrite ?: return
        viewModelScope.launch {
            val result = skillRepository.applyApprovedWrite(pending)
            SkillApprovalSession.clear()
            refreshSkills()
            localState.update {
                it.copy(
                    chatSnackbar = result,
                    messages = it.messages + AgentMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.SYSTEM,
                        content = result,
                    ),
                )
            }
        }
    }

    fun onRejectSkillWrite() {
        SkillApprovalSession.clear()
        localState.update {
            it.copy(
                chatSnackbar = application.getString(com.proot.cowork.R.string.skill_rejected),
            )
        }
    }

    fun onAcceptSkillSaveOffer() {
        val offer = localState.value.skillSaveOffer ?: return
        viewModelScope.launch {
            val result = skillRepository.saveSkillDirect(offer.skillId, offer.skillMdContent)
            refreshSkills()
            localState.update {
                it.copy(
                    skillSaveOffer = null,
                    chatSnackbar = result,
                    messages = it.messages + AgentMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.SYSTEM,
                        content = result,
                    ),
                )
            }
        }
    }

    fun onDismissSkillSaveOffer() {
        localState.update { it.copy(skillSaveOffer = null) }
    }

    private fun mergeMessages(existing: List<AgentMessage>, incoming: List<AgentMessage>): List<AgentMessage> {
        if (incoming.isEmpty()) return existing
        val byId = existing.associateBy { it.id }.toMutableMap()
        incoming.forEach { byId[it.id] = it }
        val existingIds = existing.map { it.id }.toMutableSet()
        val merged = existing.map { byId[it.id] ?: it }.toMutableList()
        incoming.forEach { msg ->
            if (msg.id !in existingIds) {
                merged.add(msg)
                existingIds.add(msg.id)
            }
        }
        return merged
    }

    private fun buildPlanSteps(tasks: List<SwarmTask>): List<PlanStep> =
        tasks.map { PlanStep(it.id, it.title, it.agent.displayName) }

    private fun toolMessagesForSwarmTurn(messages: List<AgentMessage>, swarmMessageId: String): List<AgentMessage> {
        val idx = messages.indexOfFirst { it.id == swarmMessageId }
        if (idx < 0) return emptyList()
        return messages
            .drop(idx + 1)
            .takeWhile { it.role != MessageRole.USER }
            .filter { it.role == MessageRole.TOOL }
    }

    private fun refreshSwarmResponse(
        existing: SwarmResponse?,
        messages: List<AgentMessage>,
        tasks: List<SwarmTask>,
        isExecuting: Boolean,
        awaitingApproval: Boolean,
        toolCallCount: Int,
        maxToolCalls: Int,
        toolLimitReached: Boolean,
        shellCommandLog: List<ShellCommandLogEntry>,
        cancellationMessage: String?,
    ): SwarmResponse? {
        val base = existing ?: return null
        val activeTasks = tasks.ifEmpty { base.tasks }
        val toolMsgs = toolMessagesForSwarmTurn(messages, base.messageId)
        val parsed = SwarmOutputParser.parse(toolMsgs)
        val thinking = when {
            base.phase == SwarmPhase.PLANNING && toolMsgs.isEmpty() -> listOf("Planning swarm…")
            toolMsgs.isNotEmpty() -> toolMsgs.map { SwarmOutputParser.thinkingLine(it) }
            isExecuting -> listOf("Agents working…")
            else -> base.thinkingLogs
        }
        val completedCount = activeTasks.count { it.status == TaskStatus.COMPLETED }
        val runningCount = activeTasks.count { it.status == TaskStatus.RUNNING }
        val cancelledCount = activeTasks.count { it.status == TaskStatus.CANCELLED }
        val phase = when {
            awaitingApproval -> SwarmPhase.AWAITING_APPROVAL
            isExecuting -> SwarmPhase.EXECUTING
            base.phase == SwarmPhase.PLANNING -> SwarmPhase.PLANNING
            toolLimitReached || cancellationMessage != null -> SwarmPhase.COMPLETE
            completedCount > 0 || toolMsgs.isNotEmpty() || parsed.resultType != SwarmResultType.NONE ->
                SwarmPhase.COMPLETE
            cancelledCount > 0 -> SwarmPhase.COMPLETE
            else -> base.phase
        }
        val showResults = phase == SwarmPhase.COMPLETE
        return base.copy(
            phase = phase,
            tasks = activeTasks,
            plan = buildPlanSteps(activeTasks).ifEmpty { base.plan },
            thinkingLogs = thinking,
            terminalOutputs = parsed.terminals,
            fileRows = if (showResults) parsed.fileRows else emptyList(),
            summaryChips = if (showResults) parsed.chips else emptyList(),
            narrativeSummary = when {
                toolLimitReached -> "Tool call limit ($maxToolCalls) reached."
                cancellationMessage != null -> cancellationMessage
                showResults -> parsed.narrative
                else -> null
            },
            resultType = if (showResults) parsed.resultType else SwarmResultType.NONE,
            currentStep = (completedCount + if (runningCount > 0) 1 else 0).coerceAtLeast(
                if (phase == SwarmPhase.EXECUTING) 1 else 0,
            ),
            totalSteps = activeTasks.size.coerceAtLeast(base.totalSteps),
            toolCallCount = toolCallCount,
            maxToolCalls = maxToolCalls,
            toolLimitReached = toolLimitReached,
            shellCommandLog = shellCommandLog,
        )
    }

    private fun resolveDesktopState(
        installed: Boolean,
        legacyImporting: Boolean,
        import: ImportUiState,
        desktop: DesktopState,
    ): DesktopState = when {
        import.active && import.phase != ImportPhase.STARTING_DESKTOP -> DesktopState.IMPORTING
        legacyImporting -> DesktopState.IMPORTING
        import.active && import.phase == ImportPhase.STARTING_DESKTOP -> DesktopState.STARTING
        !installed -> DesktopState.NO_ROOTFS
        desktop == DesktopState.STARTING -> DesktopState.STARTING
        else -> desktop
    }

    fun clearImportError() {
        localState.update { it.copy(importError = null) }
    }

    fun clearChatError() {
        localState.update { it.copy(chatError = null) }
    }

    fun clearChatSnackbar() {
        localState.update { it.copy(chatSnackbar = null) }
    }

    fun clearShareTranscriptUri() {
        localState.update { it.copy(shareTranscriptUri = null) }
    }

    fun onClearConversation() {
        if (localState.value.isExecuting) onStop()
        SkillApprovalSession.clear()
        viewModelScope.launch {
            chatHistoryStore.clear()
        }
        lastUserTask = null
        localState.update {
            it.copy(
                messages = emptyList(),
                swarmResponse = null,
                swarmTasks = emptyList(),
                awaitingApproval = false,
                pendingPlan = null,
                skillSaveOffer = null,
                pendingSkillWrite = null,
                inputText = "",
                isExecuting = false,
                chatSnackbar = application.getString(com.proot.cowork.R.string.chat_cleared),
            )
        }
    }

    fun onExportTranscript() {
        viewModelScope.launch {
            val uri = transcriptExporter.export(localState.value.messages)
            localState.update {
                it.copy(
                    shareTranscriptUri = uri,
                    chatSnackbar = if (uri != null) {
                        application.getString(com.proot.cowork.R.string.chat_export_ready)
                    } else {
                        application.getString(com.proot.cowork.R.string.chat_nothing_to_export)
                    },
                )
            }
        }
    }

    fun onMessageCopied() {
        localState.update {
            it.copy(chatSnackbar = application.getString(com.proot.cowork.R.string.chat_copied))
        }
    }

    fun onEditUserMessage(messageId: String, newContent: String) {
        val trimmed = newContent.trim()
        if (trimmed.isEmpty() || localState.value.isExecuting || localState.value.awaitingApproval) return
        val idx = localState.value.messages.indexOfFirst { it.id == messageId }
        if (idx < 0 || localState.value.messages[idx].role != MessageRole.USER) return
        truncateMessagesFrom(idx)
        sendUserMessage(trimmed)
    }

    fun onRegenerateFrom(messageId: String) {
        if (localState.value.isExecuting || localState.value.awaitingApproval) return
        val messages = localState.value.messages
        val idx = messages.indexOfFirst { it.id == messageId }
        if (idx < 0) return
        when (messages[idx].role) {
            MessageRole.USER -> {
                val text = messages[idx].content
                truncateMessagesFrom(idx)
                sendUserMessage(text)
            }
            MessageRole.ASSISTANT -> {
                val userIdx = messages.take(idx).indexOfLast { it.role == MessageRole.USER }
                if (userIdx < 0) return
                val text = messages[userIdx].content
                truncateMessagesFrom(userIdx)
                sendUserMessage(text)
            }
            else -> Unit
        }
    }

    fun onAttachFile(uri: Uri) {
        viewModelScope.launch {
            val (name, snippet) = AttachmentReader.readTextSnippet(application, uri)
            appendAttachmentToInput(name, snippet)
        }
    }

    fun onAttachArtifact(relativeName: String) {
        viewModelScope.launch {
            val guestPath = GuestFileRepository.joinPath(GuestPaths.ARTIFACTS_DIR, relativeName)
            val (name, snippet) = guestFileRepository.readTextSnippet(guestPath)
            appendAttachmentToInput(name, snippet)
        }
    }

    fun onAddContextBlock() {
        localState.update { state ->
            val prefix = if (state.inputText.isBlank()) "" else state.inputText + "\n\n"
            state.copy(inputText = prefix + application.getString(com.proot.cowork.R.string.composer_context_template))
        }
    }

    private fun appendAttachmentToInput(name: String, snippet: String) {
        localState.update { state ->
            val prefix = if (state.inputText.isBlank()) "" else state.inputText + "\n\n"
            state.copy(inputText = prefix + "[Attached: $name]\n$snippet")
        }
    }

    private fun truncateMessagesFrom(fromIndex: Int) {
        localState.update {
            it.copy(
                messages = it.messages.take(fromIndex),
                swarmResponse = null,
                awaitingApproval = false,
                pendingPlan = null,
                swarmTasks = emptyList(),
            )
        }
    }

    fun importFromUri(uri: Uri) {
        if (localState.value.isImportBusy) return
        viewModelScope.launch {
            localState.update { it.copy(isImportBusy = true, importError = null) }
            val result = if (TERMUX_STACK_DESKTOP) {
                prootContainerRepository.importFromUri(uri)
            } else {
                rootfsRepository.importFromUri(uri)
            }
            handleImportResult(result)
        }
    }

    fun importAutoDiscover() {
        if (localState.value.isImportBusy) return
        viewModelScope.launch {
            localState.update { it.copy(isImportBusy = true, importError = null) }
            val result = if (TERMUX_STACK_DESKTOP) {
                prootContainerRepository.importAutoDiscover()
            } else {
                rootfsRepository.importAutoDiscover()
            }
            handleImportResult(result)
        }
    }

    private fun handleImportResult(result: ImportResult) {
        localState.update { it.copy(isImportBusy = false) }
        if (result is ImportResult.Error) {
            localState.update { it.copy(importError = result.message) }
            DesktopSession.appendLog(result.message)
        }
    }

    fun onPowerOff() {
        if (TERMUX_STACK_DESKTOP) {
            prootContainerRepository.stopDesktop()
        } else {
            rootfsRepository.stopDesktopService()
            DesktopSession.setState(DesktopState.STOPPED)
        }
    }

    fun onReboot() {
        viewModelScope.launch {
            if (TERMUX_STACK_DESKTOP) {
                prootContainerRepository.rebootDesktop()
            } else {
                rootfsRepository.rebootDesktopService()
            }
        }
    }

    fun onScreenshot() {
        viewModelScope.launch {
            val message = if (TERMUX_STACK_DESKTOP) {
                DesktopScreenshot.captureToArtifacts(guestShell).fold(
                    onSuccess = { guestPath ->
                        refreshGuestFiles()
                        application.getString(com.proot.cowork.R.string.screenshot_saved_guest, guestPath)
                    },
                    onFailure = { err -> err.message ?: "Screenshot failed" },
                )
            } else {
                val frame = com.proot.cowork.domain.vnc.VncSession.currentFrame()
                val path = settingsRepository.getArtifactsDir().resolve("screenshot_${System.currentTimeMillis()}.png")
                if (frame != null) {
                    java.io.FileOutputStream(path).use { out ->
                        frame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                    "Screenshot saved: ${path.name}"
                } else {
                    "No VNC frame available yet"
                }
            }
            localState.update {
                it.copy(
                    messages = it.messages + AgentMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.SYSTEM,
                        content = message,
                    ),
                )
            }
        }
    }

    fun onModeChange(mode: ExecutionMode) {
        localState.update { it.copy(executionMode = mode) }
    }

    fun onInputChange(text: String) {
        localState.update { it.copy(inputText = text) }
    }

    fun onStop() {
        AgentRunController.requestStop()
        chatJob?.cancel()
        chatJob = null
        AgentExecutionService.stop(application)
        AgentExecutionSession.onStopRequested()
        AgentExecutionSession.markRunningShellCommandsCancelled()
        localState.update { state ->
            val swarmId = state.swarmResponse?.messageId
            state.copy(
                isExecuting = false,
                awaitingApproval = false,
                pendingPlan = null,
                swarmTasks = state.swarmTasks.map { task ->
                    if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.PENDING) {
                        task.copy(status = TaskStatus.CANCELLED)
                    } else {
                        task
                    }
                },
                swarmResponse = state.swarmResponse?.copy(
                    phase = SwarmPhase.COMPLETE,
                    tasks = state.swarmResponse.tasks.map { task ->
                        if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.PENDING) {
                            task.copy(status = TaskStatus.CANCELLED)
                        } else {
                            task
                        }
                    },
                    narrativeSummary = "Run stopped.",
                ),
                messages = if (swarmId != null && state.isExecuting) {
                    state.messages + AgentMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.SYSTEM,
                        content = "Agent run stopped.",
                    )
                } else {
                    state.messages
                },
            )
        }
    }

    fun onCancelSubtask(taskId: String) {
        AgentExecutionService.cancelSubtask(application, taskId)
        localState.update { state ->
            state.copy(
                swarmTasks = state.swarmTasks.map { task ->
                    if (task.id == taskId) task.copy(status = TaskStatus.CANCELLED) else task
                },
                swarmResponse = state.swarmResponse?.copy(
                    tasks = state.swarmResponse.tasks.map { task ->
                        if (task.id == taskId) task.copy(status = TaskStatus.CANCELLED) else task
                    },
                ),
            )
        }
    }

    fun onSend() {
        val text = localState.value.inputText.trim()
        if (text.isEmpty()) return
        sendUserMessage(text, clearInput = true)
    }

    private fun sendUserMessage(text: String, clearInput: Boolean = false) {
        if (text.isEmpty() || localState.value.isExecuting || localState.value.awaitingApproval) return

        lastUserTask = text
        chatJob?.cancel()
        AgentRunController.beginRun()
        chatJob = viewModelScope.launch {
            val config = settingsRepository.llmConfig.first()
            if (!LlmEndpoint.isConfigured(config)) {
                localState.update {
                    it.copy(chatError = "Add your API key, base URL, and model in Settings before chatting.")
                }
                return@launch
            }

            val userMsg = AgentMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.USER,
                content = text,
            )
            val assistantId = UUID.randomUUID().toString()
            val history = localState.value.messages

            localState.update {
                it.copy(
                    inputText = if (clearInput) "" else it.inputText,
                    isExecuting = true,
                    chatError = null,
                    awaitingApproval = false,
                    pendingPlan = null,
                    skillSaveOffer = null,
                    swarmResponse = SwarmResponse(
                        messageId = assistantId,
                        phase = SwarmPhase.PLANNING,
                        summary = "",
                        plan = emptyList(),
                        thinkingLogs = listOf("Planning swarm…"),
                    ),
                    messages = it.messages + userMsg + AgentMessage(
                        assistantId,
                        MessageRole.ASSISTANT,
                        "",
                    ),
                    swarmTasks = emptyList(),
                )
            }

            val mode = localState.value.executionMode
            try {
                when (mode) {
                    ExecutionMode.SWARM -> {
                        val plan = agentRunner.planSwarm(
                            config = config,
                            userTask = text,
                            history = history + userMsg,
                            isActive = { chatJob?.isActive == true && AgentRunController.isActive() },
                        )
                        AgentExecutionSession.setAwaitingApproval(plan)
                        localState.update {
                            it.copy(
                                isExecuting = false,
                                awaitingApproval = true,
                                pendingPlan = plan,
                                swarmTasks = plan.subtasks,
                                swarmResponse = SwarmResponse(
                                    messageId = assistantId,
                                    phase = SwarmPhase.AWAITING_APPROVAL,
                                    summary = plan.summary,
                                    plan = buildPlanSteps(plan.subtasks),
                                    tasks = plan.subtasks,
                                    totalSteps = plan.subtasks.size,
                                ),
                            )
                        }
                    }
                    ExecutionMode.FAST -> {
                        val historyForRun = history + userMsg
                        AgentExecutionService.startFast(application, text, historyForRun)
                        localState.update { it.copy(isExecuting = true) }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                localState.update { state ->
                    state.copy(
                        isExecuting = false,
                        swarmResponse = null,
                        messages = state.messages.filterNot { it.id == assistantId },
                    )
                }
            } catch (e: Exception) {
                localState.update { state ->
                    state.copy(
                        isExecuting = false,
                        chatError = e.message ?: "Chat request failed",
                        swarmResponse = null,
                        messages = state.messages.filterNot { it.id == assistantId },
                    )
                }
            }
        }
    }

    fun onApprovePlan() {
        val plan = localState.value.pendingPlan?.copy(subtasks = localState.value.swarmTasks) ?: return
        val history = localState.value.messages
        localState.update {
            it.copy(
                awaitingApproval = false,
                pendingPlan = null,
                isExecuting = true,
                swarmResponse = it.swarmResponse?.copy(
                    phase = SwarmPhase.EXECUTING,
                    currentStep = 1,
                ),
            )
        }
        AgentExecutionSession.clearApproval()
        AgentExecutionService.startSwarm(application, plan, history, localState.value.maxAgentPool)
    }

    fun onRejectPlan() {
        localState.update { state ->
            val swarmId = state.swarmResponse?.messageId
            state.copy(
                awaitingApproval = false,
                pendingPlan = null,
                swarmTasks = emptyList(),
                swarmResponse = null,
                messages = if (swarmId != null) {
                    state.messages.map { msg ->
                        if (msg.id == swarmId) msg.copy(content = "Plan cancelled.") else msg
                    }
                } else {
                    state.messages
                },
            )
        }
        AgentExecutionSession.clearApproval()
    }

    fun onUpdateSwarmTask(taskId: String, title: String) {
        localState.update { state ->
            val tasks = state.swarmTasks.map { if (it.id == taskId) it.copy(title = title) else it }
            val plan = state.pendingPlan?.copy(subtasks = tasks)
            if (plan != null) AgentExecutionSession.updatePlan(plan)
            state.copy(
                swarmTasks = tasks,
                pendingPlan = plan,
                swarmResponse = state.swarmResponse?.copy(
                    tasks = tasks,
                    plan = buildPlanSteps(tasks),
                ),
            )
        }
    }

    fun onQuickPrompt(prompt: String) {
        sendUserMessage(prompt.trim(), clearInput = false)
    }

    fun onOpenFilePath(path: String) {
        viewModelScope.launch {
            val (name, snippet) = guestFileRepository.readTextSnippet(path)
            appendAttachmentToInput(name, snippet)
            localState.update {
                it.copy(
                    chatSnackbar = application.getString(com.proot.cowork.R.string.files_attached_to_chat, name),
                )
            }
        }
    }

    fun onOpenTerminal() {
        selectTab(CoworkTab.Terminal)
    }

    fun onOpenBrowser() {
        localState.update { it.copy(filesPath = GuestPaths.ARTIFACTS_DIR) }
        selectTab(CoworkTab.Files)
    }

    fun onOpenSkills() {
        selectTab(CoworkTab.Skills)
    }

    fun onVoiceResult(text: String) {
        localState.update { state ->
            val merged = if (state.inputText.isBlank()) text else "${state.inputText.trimEnd()} $text"
            state.copy(inputText = merged.trim())
        }
    }

    fun onVoiceError(message: String) {
        localState.update { it.copy(chatSnackbar = message) }
    }

    companion object {
        private fun looksLikeDesktopError(line: String): Boolean {
            val lower = line.lowercase()
            return lower.contains("error") ||
                lower.contains("missing") ||
                lower.contains("failed") ||
                lower.contains("timed out") ||
                lower.contains("exit 1")
        }

        fun factory(
            application: Application,
            settingsRepository: SettingsRepository,
            rootfsRepository: RootfsRepository,
            prootContainerRepository: ProotContainerRepository,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    application,
                    settingsRepository,
                    rootfsRepository,
                    prootContainerRepository,
                ) as T
            }
        }
    }
}
