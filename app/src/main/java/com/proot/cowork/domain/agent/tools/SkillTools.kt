package com.proot.cowork.domain.agent.tools

import com.proot.cowork.data.skills.SkillRepository
import com.proot.cowork.domain.skills.SkillApprovalSession
import com.proot.cowork.domain.skills.SkillWriteAction
import org.json.JSONObject

object SkillTools {
    const val NAME_LIST = "skills_list"
    const val NAME_VIEW = "skill_view"
    const val NAME_MANAGE = "skill_manage"

    fun listDefinition(): JSONObject = toolDef(
        NAME_LIST,
        "List installed Cowork skills (agentskills.io SKILL.md modules).",
        JSONObject().apply {
            put("include_disabled", JSONObject().put("type", "boolean").put("description", "Include disabled skills"))
        },
        emptyList(),
    )

    fun viewDefinition(): JSONObject = toolDef(
        NAME_VIEW,
        "Read the full SKILL.md instructions for a skill. Increments usage stats.",
        JSONObject().apply {
            put("skill_id", JSONObject().put("type", "string").put("description", "Skill folder id"))
        },
        listOf("skill_id"),
    )

    fun manageDefinition(): JSONObject = toolDef(
        NAME_MANAGE,
        "Create, update, or delete a SKILL.md skill. Writes require explicit user approval in the app.",
        JSONObject().apply {
            put("action", JSONObject().put("type", "string").put("description", "create | update | delete"))
            put("skill_id", JSONObject().put("type", "string"))
            put("content", JSONObject().put("type", "string").put("description", "Full SKILL.md with YAML frontmatter"))
            put("reason", JSONObject().put("type", "string").put("description", "Why this skill change is needed"))
        },
        listOf("action", "skill_id", "reason"),
    )

    suspend fun list(repo: SkillRepository, args: JSONObject): String {
        val includeDisabled = args.optBoolean("include_disabled", false)
        return repo.listForAgent(includeDisabled)
    }

    suspend fun view(repo: SkillRepository, args: JSONObject): String {
        val skillId = args.optString("skill_id").trim()
        if (skillId.isBlank()) return "Error: skill_id is required"
        return repo.viewSkill(skillId)
    }

    suspend fun manage(repo: SkillRepository, args: JSONObject): String {
        val actionRaw = args.optString("action").trim().lowercase()
        val action = when (actionRaw) {
            "create" -> SkillWriteAction.CREATE
            "update" -> SkillWriteAction.UPDATE
            "delete" -> SkillWriteAction.DELETE
            else -> return "Error: action must be create, update, or delete"
        }
        val skillId = args.optString("skill_id").trim()
        if (skillId.isBlank()) return "Error: skill_id is required"
        val reason = args.optString("reason").trim().ifBlank { "Agent requested skill change" }
        val content = args.optString("content").takeIf { it.isNotBlank() }
        val pending = com.proot.cowork.domain.skills.PendingSkillWrite(
            id = java.util.UUID.randomUUID().toString(),
            action = action,
            skillId = skillId,
            content = content,
            reason = reason,
        )
        SkillApprovalSession.request(pending)
        return repo.requestManage(pending) +
            " The user must approve this change in the Cowork app before it is written to disk."
    }

    private fun toolDef(name: String, desc: String, props: JSONObject, required: List<String>): JSONObject =
        JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", desc)
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", props)
                    if (required.isNotEmpty()) {
                        put("required", org.json.JSONArray(required))
                    }
                })
            })
        }
}
