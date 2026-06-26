package com.proot.cowork.domain.skills

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/** Queues skill file writes until the user explicitly approves them. */
object SkillApprovalSession {
    private val _pending = MutableStateFlow<PendingSkillWrite?>(null)
    val pending: StateFlow<PendingSkillWrite?> = _pending.asStateFlow()

    fun request(write: PendingSkillWrite) {
        _pending.value = write
    }

    fun request(
        action: SkillWriteAction,
        skillId: String,
        content: String?,
        reason: String,
    ) {
        request(
            PendingSkillWrite(
                id = UUID.randomUUID().toString(),
                action = action,
                skillId = skillId,
                content = content,
                reason = reason,
            ),
        )
    }

    fun clear() {
        _pending.value = null
    }

    fun consume(): PendingSkillWrite? {
        val current = _pending.value
        _pending.value = null
        return current
    }
}
