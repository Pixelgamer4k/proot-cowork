package com.proot.cowork.data.skills

import android.content.Context
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.domain.agent.ShellCommandLogEntry
import com.proot.cowork.domain.skills.PendingSkillWrite
import com.proot.cowork.domain.skills.SKILL_SAVE_MIN_TOOL_CALLS
import com.proot.cowork.domain.skills.SkillDefinition
import com.proot.cowork.domain.skills.SkillSaveOffer
import com.proot.cowork.domain.skills.SkillWriteAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

class SkillRepository(context: Context) {

    private val appContext = context.applicationContext
    private val skillsDir: File get() = SettingsRepository(appContext).getSkillsDir()
    private val metaFile: File get() = File(skillsDir, "_meta.json")

    suspend fun ensureSkillsDir() = withContext(Dispatchers.IO) {
        skillsDir.mkdirs()
        if (!metaFile.exists()) {
            metaFile.writeText("{}")
        }
        seedStarterSkillIfEmpty()
    }

    suspend fun discover(): List<SkillDefinition> = withContext(Dispatchers.IO) {
        ensureSkillsDirOnIo()
        val meta = loadMeta()
        val discovered = mutableListOf<SkillDefinition>()
        skillsDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val skillFile = File(dir, SKILL_FILE_NAME)
            if (!skillFile.exists()) return@forEach
            val parsed = parseSkillFile(skillFile.readText())
            val id = dir.name
            val entry = meta.optJSONObject(id)
            discovered += SkillDefinition(
                id = id,
                name = parsed.name.ifBlank { id },
                description = parsed.description,
                tags = parsed.tags,
                enabled = entry?.optBoolean("enabled", true) ?: true,
                useCount = entry?.optInt("useCount", 0) ?: 0,
                hasSkillFile = true,
            )
        }
        discovered.sortedBy { it.name.lowercase(Locale.US) }
    }

    suspend fun listForAgent(includeDisabled: Boolean = false): String = withContext(Dispatchers.IO) {
        val skills = discover().filter { includeDisabled || it.enabled }
        if (skills.isEmpty()) return@withContext "No skills installed. Skills live in ${skillsDir.absolutePath}/<id>/SKILL.md"
        buildString {
            appendLine("Installed skills (${skills.size}):")
            skills.forEach { skill ->
                val status = if (skill.enabled) "enabled" else "disabled"
                appendLine("- ${skill.id} [$status]: ${skill.description} (uses: ${skill.useCount})")
            }
        }.trim()
    }

    suspend fun viewSkill(skillId: String): String = withContext(Dispatchers.IO) {
        val file = skillFile(skillId) ?: return@withContext "Error: skill '$skillId' not found"
        incrementUseCount(skillId)
        file.readText().take(MAX_SKILL_VIEW_CHARS)
    }

    suspend fun requestManage(pending: PendingSkillWrite): String = withContext(Dispatchers.IO) {
        when (pending.action) {
            SkillWriteAction.CREATE, SkillWriteAction.UPDATE -> {
                if (pending.content.isNullOrBlank()) {
                    return@withContext "Error: content is required for ${pending.action.name.lowercase()}"
                }
                if (!pending.content.trimStart().startsWith("---")) {
                    return@withContext "Error: SKILL.md must start with YAML frontmatter (---)"
                }
            }
            SkillWriteAction.DELETE -> Unit
        }
        "Skill ${pending.action.name.lowercase()} for '${pending.skillId}' queued for user approval."
    }

    suspend fun applyApprovedWrite(pending: PendingSkillWrite): String = withContext(Dispatchers.IO) {
        when (pending.action) {
            SkillWriteAction.CREATE, SkillWriteAction.UPDATE -> {
                val content = pending.content ?: return@withContext "Error: missing skill content"
                val dir = File(skillsDir, sanitizeId(pending.skillId))
                dir.mkdirs()
                File(dir, SKILL_FILE_NAME).writeText(content)
                setEnabled(pending.skillId, true)
                "Skill '${pending.skillId}' saved."
            }
            SkillWriteAction.DELETE -> {
                val dir = File(skillsDir, sanitizeId(pending.skillId))
                dir.deleteRecursively()
                removeMeta(pending.skillId)
                "Skill '${pending.skillId}' deleted."
            }
        }
    }

    suspend fun saveSkillDirect(skillId: String, content: String): String = withContext(Dispatchers.IO) {
        val id = sanitizeId(skillId)
        val dir = File(skillsDir, id)
        dir.mkdirs()
        File(dir, SKILL_FILE_NAME).writeText(content)
        setEnabled(id, true)
        "Skill '$id' saved."
    }

    suspend fun setEnabled(skillId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val meta = loadMeta()
        val entry = meta.optJSONObject(skillId) ?: JSONObject()
        entry.put("enabled", enabled)
        if (!entry.has("useCount")) entry.put("useCount", 0)
        meta.put(skillId, entry)
        saveMeta(meta)
    }

    fun activeSkillSummaries(skills: List<SkillDefinition>): String {
        val active = skills.filter { it.enabled }
        if (active.isEmpty()) return ""
        return active.joinToString("; ") { "${it.id}: ${it.description}" }
    }

    fun buildSaveOffer(
        userTask: String,
        toolCallCount: Int,
        shellLog: List<ShellCommandLogEntry>,
    ): SkillSaveOffer? {
        if (toolCallCount < SKILL_SAVE_MIN_TOOL_CALLS || userTask.isBlank()) return null
        val slug = slugify(userTask)
        val steps = shellLog
            .filter { it.command.isNotBlank() }
            .takeLast(12)
            .mapIndexed { index, entry -> "${index + 1}. `${entry.command}`" }
            .ifEmpty { listOf("1. Review the conversation transcript and reproduce the workflow.") }

        val content = """
            |---
            |name: $slug
            |description: ${userTask.take(160).replace('\n', ' ')}
            |tags: [workflow, auto-generated]
            |---
            |
            |# ${userTask.lines().first().take(80)}
            |
            |## When to use
            |Apply this skill when the user asks for tasks similar to: "${userTask.take(120)}"
            |
            |## Procedure
            |${steps.joinToString("\n")}
            |
            |## Notes
            |- Generated after a successful run with $toolCallCount tool calls.
            |- Refine steps before relying on this skill in production.
        """.trimMargin()

        return SkillSaveOffer(
            skillId = slug,
            title = userTask.lines().first().take(80),
            description = userTask.take(160),
            skillMdContent = content,
            toolCallCount = toolCallCount,
            userTask = userTask,
        )
    }

    private suspend fun incrementUseCount(skillId: String) {
        val meta = loadMeta()
        val entry = meta.optJSONObject(skillId) ?: JSONObject()
        entry.put("useCount", (entry.optInt("useCount", 0)) + 1)
        if (!entry.has("enabled")) entry.put("enabled", true)
        meta.put(skillId, entry)
        saveMeta(meta)
    }

    private fun removeMeta(skillId: String) {
        val meta = loadMeta()
        meta.remove(skillId)
        saveMeta(meta)
    }

    private fun skillFile(skillId: String): File? {
        val file = File(File(skillsDir, sanitizeId(skillId)), SKILL_FILE_NAME)
        return file.takeIf { it.exists() }
    }

    private fun ensureSkillsDirOnIo() {
        skillsDir.mkdirs()
        if (!metaFile.exists()) metaFile.writeText("{}")
    }

    private fun loadMeta(): JSONObject = runCatching {
        if (!metaFile.exists()) JSONObject() else JSONObject(metaFile.readText())
    }.getOrDefault(JSONObject())

    private fun saveMeta(meta: JSONObject) {
        metaFile.writeText(meta.toString(2))
    }

    private fun seedStarterSkillIfEmpty() {
        val hasSkill = skillsDir.listFiles()?.any { it.isDirectory && File(it, SKILL_FILE_NAME).exists() } == true
        if (hasSkill) return
        val dir = File(skillsDir, "proot-shell-basics")
        dir.mkdirs()
        File(dir, SKILL_FILE_NAME).writeText(
            """
            |---
            |name: proot-shell-basics
            |description: Common Ubuntu proot shell workflows for package install, files, and service checks.
            |tags: [proot, shell, ubuntu]
            |---
            |
            |# Proot shell basics
            |
            |## When to use
            |Use for package management, file inspection, and quick service checks inside the Ubuntu proot guest.
            |
            |## Procedure
            |1. Run `apt update` before installing packages.
            |2. Use `ls -la` and `df -h` to inspect directories and disk usage.
            |3. Verify services with `systemctl status <service>` or `curl localhost`.
            """.trimMargin(),
        )
        val meta = loadMeta()
        meta.put(
            "proot-shell-basics",
            JSONObject().put("enabled", true).put("useCount", 0),
        )
        saveMeta(meta)
    }

    private data class ParsedSkill(
        val name: String,
        val description: String,
        val tags: List<String>,
    )

    private fun parseSkillFile(content: String): ParsedSkill {
        val frontmatter = parseFrontmatter(content)
        val tagsRaw = frontmatter["tags"].orEmpty()
        val tags = Regex("""[\w-]+""").findAll(tagsRaw.replace("[", " ").replace("]", " "))
            .map { it.value }
            .filter { it.length > 1 }
            .toList()
        return ParsedSkill(
            name = frontmatter["name"].orEmpty(),
            description = frontmatter["description"].orEmpty().ifBlank { "Custom skill" },
            tags = tags.ifEmpty { listOf("skill") },
        )
    }

    private fun parseFrontmatter(content: String): Map<String, String> {
        if (!content.startsWith("---")) return emptyMap()
        val end = content.indexOf("---", 3)
        if (end < 0) return emptyMap()
        val block = content.substring(3, end).trim()
        return block.lines().mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) return@mapNotNull null
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim().removeSurrounding("\"")
            key to value
        }.toMap()
    }

    private fun slugify(text: String): String {
        val base = text.lowercase(Locale.US)
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
            .take(48)
        return base.ifBlank { "workflow-skill" }
    }

    private fun sanitizeId(id: String): String =
        id.lowercase(Locale.US).replace(Regex("""[^a-z0-9-]+"""), "-").trim('-').ifBlank { "skill" }

    companion object {
        const val SKILL_FILE_NAME = "SKILL.md"
        private const val MAX_SKILL_VIEW_CHARS = 24_000
    }
}
