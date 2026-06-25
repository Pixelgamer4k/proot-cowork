package com.proot.cowork.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.data.prootcontainer.ProotContainerRepository
import com.proot.cowork.data.rootfs.ImportResult
import com.proot.cowork.data.rootfs.RootfsRepository
import com.proot.cowork.domain.agent.AgentMessage
import com.proot.cowork.domain.agent.ExecutionMode
import com.proot.cowork.domain.agent.MessageRole
import com.proot.cowork.domain.agent.SwarmTask
import com.proot.cowork.domain.desktop.TERMUX_STACK_DESKTOP
import com.proot.cowork.domain.importing.ImportPhase
import com.proot.cowork.domain.importing.ImportSession
import com.proot.cowork.domain.importing.ImportUiState
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.domain.vnc.VncSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val executionMode: ExecutionMode = ExecutionMode.PLAN,
    val inputText: String = "",
    val isExecuting: Boolean = false,
    val importError: String? = null,
    val isImportBusy: Boolean = false,
)

class HomeViewModel(
    private val settingsRepository: SettingsRepository,
    private val rootfsRepository: RootfsRepository,
    private val prootContainerRepository: ProotContainerRepository,
) : ViewModel() {

    private val localState = MutableStateFlow(HomeUiState())

    val uiState: StateFlow<HomeUiState> = combine(
        settingsRepository.rootfsState,
        ImportSession.state,
        DesktopSession.state,
        DesktopSession.logLines,
        localState,
    ) { rootfs, import, desktop, logs, local ->
        local.copy(
            desktopState = resolveDesktopState(rootfs.isInstalled, rootfs.isImporting, import, desktop),
            importUiState = import,
            distroName = rootfs.distroName,
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
        localState.update { it.copy(isExecuting = false) }
    }

    fun onSend() {
        val text = localState.value.inputText.trim()
        if (text.isEmpty() || localState.value.isExecuting) return

        viewModelScope.launch {
            val userMsg = AgentMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.USER,
                content = text,
            )
            localState.update {
                it.copy(
                    inputText = "",
                    isExecuting = true,
                    messages = it.messages + userMsg,
                )
            }

            val mode = localState.value.executionMode
            val response = when (mode) {
                ExecutionMode.PLAN -> buildPlanResponse(text)
                ExecutionMode.DIRECT -> "Executing directly: \"$text\"\n\n(Phase 3: Koog agent will run tools in proot)"
                ExecutionMode.SCHEDULE -> "Scheduled for later. (Phase 5: date/time picker + WorkManager)"
            }

            localState.update {
                it.copy(
                    isExecuting = false,
                    messages = it.messages + AgentMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.ASSISTANT,
                        content = response,
                    ),
                    swarmTasks = if (mode == ExecutionMode.PLAN) demoSwarmTasks(text) else emptyList(),
                )
            }
        }
    }

    private fun buildPlanResponse(task: String): String {
        return """
            |## Plan for: $task
            |
            |1. **Analyze** — understand requirements and constraints
            |2. **Decompose** — split into parallel subtasks for swarm agents
            |3. **Execute** — run in proot desktop (shell, files, browser tools)
            |4. **Verify** — check outputs and capture screenshot
            |5. **Learn** — offer to save workflow as SKILL.md
            |
            |Tap **Execute** to approve this plan, or edit your request.
            |(Phase 3: real LLM planning via Koog + OpenRouter)
        """.trimMargin()
    }

    private fun demoSwarmTasks(task: String): List<SwarmTask> = listOf(
        SwarmTask("1", "Analyze: $task"),
        SwarmTask("2", "Prepare proot environment"),
        SwarmTask("3", "Execute subtasks in parallel", children = listOf(
            SwarmTask("3a", "Shell commands"),
            SwarmTask("3b", "File operations"),
        )),
        SwarmTask("4", "Verify and report"),
    )

    fun onQuickPrompt(prompt: String) {
        onInputChange(prompt)
    }

    fun onScheduleDraft(text: String) {
        onModeChange(ExecutionMode.SCHEDULE)
        onInputChange(text)
        localState.update {
            it.copy(
                messages = it.messages + AgentMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.SYSTEM,
                    content = "Scheduled (preview): \"$text\" — WorkManager integration coming in a future build.",
                ),
            )
        }
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
