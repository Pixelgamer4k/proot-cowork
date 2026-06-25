package com.proot.cowork.domain.agent

enum class ExecutionMode {
    /** Multi-agent swarm orchestration. */
    SWARM,
    /** Single-agent fast execution. */
    FAST,
}

enum class SwarmAgentType(val displayName: String) {
    Planner("Planner"),
    Researcher("Researcher"),
    Executor("Executor"),
    Coder("Coder"),
    Validator("Validator"),
    Slack("Slack"),
    ;

    companion object {
        fun fromString(value: String): SwarmAgentType = entries.firstOrNull {
            it.displayName.equals(value, ignoreCase = true) ||
                it.name.equals(value, ignoreCase = true)
        } ?: Executor
    }
}

enum class ToolCallStatus {
    REQUESTED,
    RUNNING,
    COMPLETED,
    FAILED,
}

data class AgentMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolName: String? = null,
    val toolCallId: String? = null,
    val agentName: String? = null,
    val toolStatus: ToolCallStatus? = null,
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL,
}

data class SwarmTask(
    val id: String,
    val title: String,
    val agent: SwarmAgentType = SwarmAgentType.Executor,
    val status: TaskStatus = TaskStatus.PENDING,
    val parallelizable: Boolean = true,
    val children: List<SwarmTask> = emptyList(),
)

enum class TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class TaskPlan(
    val id: String,
    val summary: String,
    val userTask: String,
    val subtasks: List<SwarmTask>,
)

data class SwarmAgentState(
    val type: SwarmAgentType,
    val status: TaskStatus = TaskStatus.PENDING,
    val currentTask: String? = null,
    val tasksCompleted: Int = 0,
)

const val DEFAULT_MAX_AGENT_POOL = 3
