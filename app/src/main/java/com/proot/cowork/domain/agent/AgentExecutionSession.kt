package com.proot.cowork.domain.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class AgentExecutionSnapshot(
    val isRunning: Boolean = false,
    val mode: ExecutionMode? = null,
    val notificationText: String = "",
    val agentStates: List<SwarmAgentState> = SwarmAgentType.entries.map { SwarmAgentState(it) },
    val messages: List<AgentMessage> = emptyList(),
    val swarmTasks: List<SwarmTask> = emptyList(),
    val pendingPlan: TaskPlan? = null,
    val awaitingApproval: Boolean = false,
    val maxAgentPool: Int = DEFAULT_MAX_AGENT_POOL,
    val toolCallCount: Int = 0,
    val maxToolCalls: Int = DEFAULT_MAX_TOOL_CALLS,
    val toolLimitReached: Boolean = false,
    val shellCommandLog: List<ShellCommandLogEntry> = emptyList(),
    val stopRequested: Boolean = false,
    val cancellationMessage: String? = null,
)

/** Shared execution state between [com.proot.cowork.service.AgentExecutionService] and UI. */
object AgentExecutionSession {
    private val _snapshot = MutableStateFlow(AgentExecutionSnapshot())
    val snapshot: StateFlow<AgentExecutionSnapshot> = _snapshot.asStateFlow()

    fun resetForNewRun(mode: ExecutionMode) {
        AgentRunController.beginRun()
        _snapshot.update {
            AgentExecutionSnapshot(
                isRunning = true,
                mode = mode,
                notificationText = "Starting agents…",
                agentStates = SwarmAgentType.entries.map { SwarmAgentState(it) },
                toolCallCount = 0,
                maxToolCalls = DEFAULT_MAX_TOOL_CALLS,
                toolLimitReached = false,
                shellCommandLog = emptyList(),
                stopRequested = false,
                cancellationMessage = null,
            )
        }
    }

    fun setNotification(text: String) {
        _snapshot.update { it.copy(notificationText = text) }
    }

    fun setRunning(running: Boolean) {
        _snapshot.update { it.copy(isRunning = running) }
    }

    fun setAwaitingApproval(plan: TaskPlan) {
        _snapshot.update {
            it.copy(
                pendingPlan = plan,
                awaitingApproval = true,
                swarmTasks = plan.subtasks,
                isRunning = false,
                notificationText = "Swarm plan ready for approval",
            )
        }
    }

    fun clearApproval() {
        _snapshot.update { it.copy(awaitingApproval = false) }
    }

    fun updatePlan(plan: TaskPlan) {
        _snapshot.update { it.copy(pendingPlan = plan, swarmTasks = plan.subtasks) }
    }

    fun updateSwarmTasks(tasks: List<SwarmTask>) {
        _snapshot.update { it.copy(swarmTasks = tasks) }
    }

    fun updateAgentStates(states: List<SwarmAgentState>) {
        _snapshot.update { it.copy(agentStates = states) }
    }

    fun appendMessage(message: AgentMessage) {
        _snapshot.update { it.copy(messages = it.messages + message) }
    }

    fun updateMessage(id: String, transform: (AgentMessage) -> AgentMessage) {
        _snapshot.update { snap ->
            snap.copy(messages = snap.messages.map { if (it.id == id) transform(it) else it })
        }
    }

    fun mergeMessages(messages: List<AgentMessage>) {
        _snapshot.update { it.copy(messages = it.messages + messages) }
    }

    fun newMessageId(): String = UUID.randomUUID().toString()

    fun isToolLimitReached(): Boolean {
        val snap = _snapshot.value
        return snap.toolLimitReached || snap.toolCallCount >= snap.maxToolCalls
    }

    /** Returns false when the global tool-call budget is exhausted. */
    fun tryRecordToolCall(): Boolean {
        var allowed = false
        _snapshot.update { snap ->
            if (snap.toolCallCount >= snap.maxToolCalls) {
                allowed = false
                snap.copy(toolLimitReached = true)
            } else {
                allowed = true
                snap.copy(toolCallCount = snap.toolCallCount + 1)
            }
        }
        return allowed
    }

    fun beginShellCommand(agentName: String, command: String): String {
        val id = newMessageId()
        val entry = ShellCommandLogEntry(
            id = id,
            agentName = agentName,
            command = command.trim(),
            status = ShellCommandStatus.RUNNING,
        )
        _snapshot.update { it.copy(shellCommandLog = it.shellCommandLog + entry) }
        return id
    }

    fun completeShellCommand(
        logId: String,
        status: ShellCommandStatus,
        exitCode: Int? = null,
    ) {
        _snapshot.update { snap ->
            snap.copy(
                shellCommandLog = snap.shellCommandLog.map { entry ->
                    if (entry.id == logId) entry.copy(status = status, exitCode = exitCode) else entry
                },
            )
        }
    }

    fun cancelSubtask(taskId: String) {
        AgentRunController.requestSubtaskCancel(taskId)
        _snapshot.update { snap ->
            snap.copy(
                swarmTasks = snap.swarmTasks.map { task ->
                    if (task.id == taskId && task.status == TaskStatus.RUNNING) {
                        task.copy(status = TaskStatus.CANCELLED)
                    } else {
                        task
                    }
                },
            )
        }
    }

    fun onStopRequested(reason: String = "Stopped by user") {
        AgentRunController.requestStop()
        _snapshot.update { snap ->
            snap.copy(
                isRunning = false,
                stopRequested = true,
                cancellationMessage = reason,
                notificationText = reason,
                swarmTasks = snap.swarmTasks.map { task ->
                    if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.PENDING) {
                        task.copy(status = TaskStatus.CANCELLED)
                    } else {
                        task
                    }
                },
                shellCommandLog = snap.shellCommandLog.map { entry ->
                    if (entry.status == ShellCommandStatus.RUNNING) {
                        entry.copy(status = ShellCommandStatus.CANCELLED)
                    } else {
                        entry
                    }
                },
            )
        }
    }

    fun markRunningShellCommandsCancelled() {
        _snapshot.update { snap ->
            snap.copy(
                shellCommandLog = snap.shellCommandLog.map { entry ->
                    if (entry.status == ShellCommandStatus.RUNNING) {
                        entry.copy(status = ShellCommandStatus.CANCELLED)
                    } else {
                        entry
                    }
                },
            )
        }
    }
}
