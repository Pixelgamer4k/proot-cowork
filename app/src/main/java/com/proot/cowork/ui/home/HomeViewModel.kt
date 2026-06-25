package com.proot.cowork.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proot.cowork.data.llm.LlmEndpoint
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.data.prootcontainer.ProotContainerRepository
import com.proot.cowork.data.rootfs.ImportResult
import com.proot.cowork.data.rootfs.RootfsRepository
import com.proot.cowork.domain.agent.AgentMessage
import com.proot.cowork.domain.agent.CoworkKoogAgentRunner
import com.proot.cowork.domain.agent.ExecutionMode
import com.proot.cowork.domain.agent.MessageRole
import com.proot.cowork.domain.desktop.TERMUX_STACK_DESKTOP
import com.proot.cowork.domain.importing.ImportPhase
import com.proot.cowork.domain.importing.ImportSession
import com.proot.cowork.domain.importing.ImportUiState
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.domain.vnc.VncSession
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
    val swarmTasks: List<com.proot.cowork.domain.agent.SwarmTask> = emptyList(),
    val executionMode: ExecutionMode = ExecutionMode.SWARM,
    val inputText: String = "",
    val isExecuting: Boolean = false,
    val importError: String? = null,
    val isImportBusy: Boolean = false,
    val isApiConfigured: Boolean = false,
    val chatError: String? = null,
)

class HomeViewModel(
    private val settingsRepository: SettingsRepository,
    private val rootfsRepository: RootfsRepository,
    private val prootContainerRepository: ProotContainerRepository,
) : ViewModel() {

    private val localState = MutableStateFlow(HomeUiState())
    private var chatJob: Job? = null

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
        chatJob?.cancel()
        chatJob = null
        localState.update { it.copy(isExecuting = false) }
    }

    fun onSend() {
        val text = localState.value.inputText.trim()
        if (text.isEmpty() || localState.value.isExecuting) return

        chatJob?.cancel()
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
            val assistantPlaceholder = AgentMessage(
                id = assistantId,
                role = MessageRole.ASSISTANT,
                content = "",
            )

            localState.update {
                it.copy(
                    inputText = "",
                    isExecuting = true,
                    chatError = null,
                    messages = it.messages + userMsg + assistantPlaceholder,
                    swarmTasks = emptyList(),
                )
            }

            val mode = localState.value.executionMode
            val historyBeforeAssistant = localState.value.messages.dropLast(1)

            try {
                val response = CoworkKoogAgentRunner.streamChat(
                    config = config,
                    mode = mode,
                    history = historyBeforeAssistant,
                    userMessage = text,
                    isActive = { chatJob?.isActive == true },
                ) { delta ->
                    localState.update { state ->
                        state.copy(
                            messages = state.messages.map { msg ->
                                if (msg.id == assistantId) msg.copy(content = msg.content + delta) else msg
                            },
                        )
                    }
                }

                val finalText = response.ifBlank {
                    localState.value.messages.lastOrNull { it.id == assistantId }?.content.orEmpty()
                }

                localState.update { state ->
                    state.copy(
                        isExecuting = false,
                        messages = state.messages.map { msg ->
                            if (msg.id == assistantId) msg.copy(content = finalText.ifBlank { "No response from model." }) else msg
                        },
                        swarmTasks = if (mode == ExecutionMode.SWARM) {
                            CoworkKoogAgentRunner.parseSwarmTasks(finalText, text)
                        } else {
                            emptyList()
                        },
                    )
                }
            } catch (e: Exception) {
                localState.update { state ->
                    state.copy(
                        isExecuting = false,
                        chatError = e.message ?: "Chat request failed",
                        messages = state.messages.filterNot { it.id == assistantId && it.content.isBlank() },
                    )
                }
            }
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
            settingsRepository: SettingsRepository,
            rootfsRepository: RootfsRepository,
            prootContainerRepository: ProotContainerRepository,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    settingsRepository,
                    rootfsRepository,
                    prootContainerRepository,
                ) as T
            }
        }
    }
}
