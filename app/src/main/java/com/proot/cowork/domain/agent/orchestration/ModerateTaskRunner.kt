package com.proot.cowork.domain.agent.orchestration

import com.proot.cowork.data.files.GuestPaths
import com.proot.cowork.data.llm.LlmEndpoint
import com.proot.cowork.data.llm.OpenAiCompatibleLlmClient
import com.proot.cowork.data.prefs.LlmConfig
import com.proot.cowork.data.proot.ProotGuestShellExecutor
import com.proot.cowork.domain.agent.AgentExecutionSession
import com.proot.cowork.domain.agent.AgentMessage
import com.proot.cowork.domain.agent.AgentRunContext
import com.proot.cowork.domain.agent.MessageRole
import com.proot.cowork.domain.agent.SwarmAgentType
import com.proot.cowork.domain.agent.tools.FileSystemTool
import com.proot.cowork.domain.agent.tools.ToolInvocation
import com.proot.cowork.domain.agent.tools.AgentToolRegistry
import org.json.JSONObject

/**
 * Two-phase moderate execution: brief research (hard-capped) then write deliverable.
 * Avoids open-ended tool loops that burn the global budget.
 */
class ModerateTaskRunner(
    private val shell: ProotGuestShellExecutor,
    private val tools: AgentToolRegistry,
    private val toolLoop: ToolLoopRunner,
) {

    suspend fun run(
        config: LlmConfig,
        systemPrompt: String,
        history: List<AgentMessage>,
        userTask: String,
        classification: TaskClassification,
        isActive: () -> Boolean,
        onAssistantDelta: (String) -> Unit,
        onToolEvent: (AgentMessage) -> Unit,
    ): String {
        val deliverable = deliverablePath(userTask)
        AgentRunContext.targetDeliverablePath = deliverable

        val researchPrompt = """
            |Research phase for: $userTask
            |Use at most 2 web_fetch calls. Gather nginx best-practice bullets only.
            |Do NOT write files yet. End with a concise markdown outline.
        """.trimMargin()

        val researchOutput = toolLoop.run(
            config = config,
            agent = SwarmAgentType.Researcher,
            systemPrompt = systemPrompt,
            history = history,
            userMessage = researchPrompt,
            isActive = isActive,
            onAssistantDelta = onAssistantDelta,
            onToolEvent = onToolEvent,
            toolFilter = AgentToolRegistry.ToolFilter.RESEARCH,
            maxRounds = 5,
        )
        AgentRunContext.researchNotes = researchOutput

        if (!isActive()) return researchOutput

        val writePrompt = """
            |Write phase for: $userTask
            |Deliverable path (required): $deliverable
            |Use write_file ONCE to save a complete markdown summary.
            |Research notes:
            |${researchOutput.take(6000)}
            |After writing, confirm the path in your reply and stop.
        """.trimMargin()

        val writeOutput = toolLoop.run(
            config = config,
            agent = SwarmAgentType.Executor,
            systemPrompt = systemPrompt,
            history = history,
            userMessage = writePrompt,
            isActive = isActive,
            onAssistantDelta = onAssistantDelta,
            onToolEvent = onToolEvent,
            toolFilter = AgentToolRegistry.ToolFilter.WRITE,
            maxRounds = 4,
        )

        if (!guestFileExists(deliverable)) {
            val body = synthesizeMarkdown(config, userTask, researchOutput, isActive)
            tools.execute(
                ToolInvocation(
                    FileSystemTool.NAME_WRITE,
                    JSONObject().put("path", deliverable).put("content", body),
                ),
            )
            onSystemToolNotice(onToolEvent, "Wrote deliverable via fallback: $deliverable")
            return buildString {
                appendLine(writeOutput.trim())
                appendLine()
                appendLine("**Deliverable saved:** `$deliverable`")
                appendLine()
                append(body.take(1200))
            }
        }

        return buildString {
            appendLine(writeOutput.trim())
            appendLine()
            appendLine("**Deliverable:** `$deliverable`")
        }
    }

    private suspend fun synthesizeMarkdown(
        config: LlmConfig,
        userTask: String,
        research: String,
        isActive: () -> Boolean,
    ): String {
        if (!LlmEndpoint.isConfigured(config) || !isActive()) {
            return fallbackMarkdown(userTask, research)
        }
        val system = "You write markdown file bodies only. No tool calls. No preamble."
        val user = """
            Task: $userTask
            Research notes:
            ${research.take(8000)}
            Write a concise nginx best-practices summary markdown document.
        """.trimIndent()
        val messages = OpenAiCompatibleLlmClient.buildMessagesArray(system, emptyList(), user)
        return runCatching {
            OpenAiCompatibleLlmClient.complete(config, messages, tools = null, temperature = 0.3).content
                .ifBlank { fallbackMarkdown(userTask, research) }
        }.getOrElse { fallbackMarkdown(userTask, research) }
    }

    private fun fallbackMarkdown(userTask: String, research: String): String = buildString {
        appendLine("# Summary")
        appendLine()
        appendLine("Task: $userTask")
        appendLine()
        if (research.isNotBlank()) {
            appendLine(research.take(4000))
        } else {
            appendLine("- Enable gzip and sensible worker limits")
            appendLine("- Use TLS and keep config in sites-available")
            appendLine("- Test with `nginx -t` before reload")
        }
    }

    private suspend fun guestFileExists(path: String): Boolean {
        val quoted = "'" + path.replace("'", "'\\''") + "'"
        val result = shell.run("test -f $quoted && echo EXISTS || echo MISSING")
        return result.output.contains("EXISTS")
    }

    private fun deliverablePath(userTask: String): String {
        val lower = userTask.lowercase()
        val mdMatch = Regex("""write\s+([\w.-]+\.md)""", RegexOption.IGNORE_CASE).find(userTask)
        val name = mdMatch?.groupValues?.get(1) ?: when {
            lower.contains("summary.md") -> "summary.md"
            else -> "summary.md"
        }
        return "${GuestPaths.AGENT_OUTPUT_DIR}/$name"
    }

    private fun onSystemToolNotice(onToolEvent: (AgentMessage) -> Unit, text: String) {
        onToolEvent(
            AgentMessage(
                id = AgentExecutionSession.newMessageId(),
                role = MessageRole.TOOL,
                content = text,
                toolName = FileSystemTool.NAME_WRITE,
                agentName = SwarmAgentType.Executor.displayName,
            ),
        )
    }
}

/** Callback surface so [ModerateTaskRunner] does not depend on [CoworkAgentRunner] directly. */
fun interface ToolLoopRunner {
    suspend fun run(
        config: LlmConfig,
        agent: SwarmAgentType,
        systemPrompt: String,
        history: List<AgentMessage>,
        userMessage: String,
        isActive: () -> Boolean,
        onAssistantDelta: (String) -> Unit,
        onToolEvent: (AgentMessage) -> Unit,
        toolFilter: AgentToolRegistry.ToolFilter,
        maxRounds: Int,
    ): String
}
