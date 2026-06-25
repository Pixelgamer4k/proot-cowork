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
)

/** Shared execution state between [com.proot.cowork.service.AgentExecutionService] and UI. */
object AgentExecutionSession {
    private val _snapshot = MutableStateFlow(AgentExecutionSnapshot())
    val snapshot: StateFlow<AgentExecutionSnapshot> = _snapshot.asStateFlow()

    fun resetForNewRun(mode: ExecutionMode) {
        _snapshot.update {
            AgentExecutionSnapshot(
                isRunning = true,
                mode = mode,
                notificationText = "Starting agents…",
                agentStates = SwarmAgentType.entries.map { SwarmAgentState(it) },
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
}
