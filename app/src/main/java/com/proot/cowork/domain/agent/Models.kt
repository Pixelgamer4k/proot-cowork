package com.proot.cowork.domain.agent

enum class ExecutionMode {
    /** Multi-agent swarm orchestration. */
    SWARM,
    /** Single-agent fast execution. */
    FAST,
}

data class AgentMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
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
    val status: TaskStatus = TaskStatus.PENDING,
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
    val subtasks: List<SwarmTask>,
)
