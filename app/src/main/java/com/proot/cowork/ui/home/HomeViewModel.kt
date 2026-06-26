package com.proot.cowork.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proot.cowork.data.llm.LlmEndpoint
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.data.prootcontainer.ProotContainerRepository
import com.proot.cowork.data.rootfs.ImportResult
import com.proot.cowork.data.rootfs.RootfsRepository
import com.proot.cowork.domain.agent.AgentExecutionSession
import com.proot.cowork.domain.agent.AgentMessage
import com.proot.cowork.domain.agent.CoworkAgentRunner
import com.proot.cowork.domain.agent.DEFAULT_MAX_AGENT_POOL
import com.proot.cowork.domain.agent.ExecutionMode
import com.proot.cowork.domain.agent.MessageRole
import com.proot.cowork.domain.agent.AgentRunController
import com.proot.cowork.domain.agent.DEFAULT_MAX_TOOL_CALLS
import com.proot.cowork.domain.agent.ShellCommandLogEntry
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
import com.proot.cowork.domain.vnc.VncSession
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
)

class HomeViewModel(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val rootfsRepository: RootfsRepository,
    private val prootContainerRepository: ProotContainerRepository,
) : ViewModel() {

    private val agentRunner = CoworkAgentRunner(application)
    private val localState = MutableStateFlow(HomeUiState())
    private var chatJob: Job? = null

    init {
        viewModelScope.launch {
            AgentExecutionSession.snapshot.collect { snap ->
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
            desktopLogHint = if (desktop == DesktopState.STOPPED) {
                logs.lastOrNull(::looksLikeDesktopError) ?: logs.lastOrNull()
            } else {
                null
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

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
            val frame = VncSession.currentFrame()
            val path = settingsRepository.getArtifactsDir().resolve("screenshot_${System.currentTimeMillis()}.png")
            val message = if (frame != null) {
                java.io.FileOutputStream(path).use { out ->
                    frame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                "Screenshot saved: ${path.name}"
            } else {
                "No VNC frame available yet"
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
        if (text.isEmpty() || localState.value.isExecuting || localState.value.awaitingApproval) return

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
                    inputText = "",
                    isExecuting = true,
                    chatError = null,
                    awaitingApproval = false,
                    pendingPlan = null,
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
        onInputChange(prompt)
    }

    fun onScheduleDraft(text: String) {
        DesktopSession.appendLog("Scheduled (preview): $text")
    }

    fun onOpenFilePath(path: String) {
        localState.update {
            it.copy(
                messages = it.messages + AgentMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.SYSTEM,
                    content = "File: $path",
                ),
            )
        }
        DesktopSession.appendLog("Opened path: $path")
    }

    fun onTerminalCommand(command: String) {
        DesktopSession.appendLog("$ $command")
        DesktopSession.appendLog("(Terminal execution — embed coming in a future build)")
    }

    fun onOpenTerminal() {
        localState.update {
            it.copy(
                messages = it.messages + AgentMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.SYSTEM,
                    content = "Shell access will open in a future update.",
                ),
            )
        }
    }

    fun onOpenBrowser() {
        localState.update {
            it.copy(
                messages = it.messages + AgentMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.SYSTEM,
                    content = "File browser (Phase 5): artifacts at ${settingsRepository.getArtifactsDir()}",
                ),
            )
        }
    }

    fun onOpenSkills() {
        localState.update {
            it.copy(
                messages = it.messages + AgentMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.SYSTEM,
                    content = "Skills manager (Phase 4): agentskills.io SKILL.md at ${settingsRepository.getSkillsDir()}",
                ),
            )
        }
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
