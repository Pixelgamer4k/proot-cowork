package com.proot.cowork.domain.skills

enum class SkillWriteAction {
    CREATE,
    UPDATE,
    DELETE,
}

data class SkillDefinition(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String>,
    val enabled: Boolean,
    val useCount: Int,
    val hasSkillFile: Boolean,
)

data class PendingSkillWrite(
    val id: String,
    val action: SkillWriteAction,
    val skillId: String,
    val content: String?,
    val reason: String,
    val requestedAt: Long = System.currentTimeMillis(),
)

data class SkillSaveOffer(
    val skillId: String,
    val title: String,
    val description: String,
    val skillMdContent: String,
    val toolCallCount: Int,
    val userTask: String,
)

const val SKILL_SAVE_MIN_TOOL_CALLS = 5
