package com.proot.cowork.domain.agent

enum class SwarmPhase {
    PLANNING,
    AWAITING_APPROVAL,
    EXECUTING,
    COMPLETE,
}

enum class SwarmResultType {
    NONE,
    SUMMARY,
    FILE_LISTING,
    TERMINAL,
    MIXED,
}

data class PlanStep(
    val id: String,
    val title: String,
    val agent: String,
)

data class FileListingRow(
    val type: String,
    val name: String,
    val size: String,
    val modified: String,
)

data class SummaryChip(
    val icon: String,
    val label: String,
)

data class TerminalBlock(
    val id: String,
    val agentName: String,
    val toolName: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
)

data class SwarmResponse(
    val messageId: String,
    val phase: SwarmPhase,
    val summary: String,
    val plan: List<PlanStep>,
    val tasks: List<SwarmTask> = emptyList(),
    val thinkingLogs: List<String> = emptyList(),
    val terminalOutputs: List<TerminalBlock> = emptyList(),
    val resultType: SwarmResultType = SwarmResultType.NONE,
    val fileRows: List<FileListingRow> = emptyList(),
    val summaryChips: List<SummaryChip> = emptyList(),
    val narrativeSummary: String? = null,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
) {
    val activeAgentCount: Int
        get() = tasks.count { it.status == TaskStatus.RUNNING }.coerceAtLeast(
            if (phase == SwarmPhase.PLANNING || phase == SwarmPhase.EXECUTING) 1 else 0,
        )
}
