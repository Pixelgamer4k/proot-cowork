package com.proot.cowork.domain.agent

import android.content.Context
import com.proot.cowork.data.llm.LlmEndpoint
import com.proot.cowork.data.prefs.LlmConfig
import com.proot.cowork.data.llm.LlmToolCall
import com.proot.cowork.data.llm.OpenAiCompatibleLlmClient
import com.proot.cowork.data.skills.SkillRepository
import com.proot.cowork.domain.agent.tools.AgentToolRegistry
import com.proot.cowork.domain.agent.tools.CodeTool
import com.proot.cowork.domain.agent.tools.FileSystemTool
import com.proot.cowork.domain.agent.tools.ProotShellTool
import com.proot.cowork.domain.agent.tools.ToolInvocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ToolLimitReachedException : CancellationException("Tool call limit reached")

class CoworkAgentRunner(private val context: Context) {

    private val tools = AgentToolRegistry(context)
    private val skillRepository = SkillRepository(context)

    suspend fun planSwarm(
        config: LlmConfig,
        userTask: String,
        history: List<AgentMessage>,
        isActive: () -> Boolean,
    ): TaskPlan {
        require(LlmEndpoint.isConfigured(config))
        if (!isActive()) throw CancellationException("Planning cancelled")
        val system = """
            You are the Cowork Swarm Planner. Reply with ONLY one JSON object, no markdown, no prose.
            Schema:
            {"summary":"short one-line plan title","subtasks":[{"id":"1","title":"step description","agent":"Executor","parallelizable":true}]}
            Rules:
            - agent must be one of: Planner, Researcher, Executor, Coder, Validator, Slack
            - 2-5 subtasks, actionable inside Ubuntu proot
            - parallelizable is boolean true or false (no spaces inside the word)
            - valid JSON only: double quotes on keys and string values
        """.trimIndent()
        val messages = OpenAiCompatibleLlmClient.buildMessagesArray(system, history, userTask)
        val result = OpenAiCompatibleLlmClient.complete(config, messages, temperature = 0.2)
        if (!isActive()) throw CancellationException("Planning cancelled")
        return parsePlanJson(result.content, userTask)
    }

    fun formatPlanForChat(plan: TaskPlan): String = buildString {
        appendLine(plan.summary)
        appendLine()
        plan.subtasks.forEach { task ->
            appendLine("${task.id}. ${task.agent.displayName} — ${task.title}")
        }
        appendLine()
        append("Review the plan below and tap Execute to approve.")
    }

    suspend fun runFast(
        config: LlmConfig,
        userTask: String,
        history: List<AgentMessage>,
        isActive: () -> Boolean,
        onAssistantDelta: (String) -> Unit,
        onToolEvent: (AgentMessage) -> Unit,
    ): String {
        val skillsSuffix = skillsPromptSuffix()
        val system = """
            You are the Cowork Fast agent with direct access to proot tools.
            Execute the user's task using tools when needed. Be concise in final answers.
            $skillsSuffix
        """.trimIndent().trim()
        return runToolLoop(
            config = config,
            agent = SwarmAgentType.Executor,
            systemPrompt = system,
            history = history,
            userMessage = userTask,
            isActive = isActive,
            onAssistantDelta = onAssistantDelta,
            onToolEvent = onToolEvent,
        )
    }

    suspend fun executeSwarmPlan(
        config: LlmConfig,
        plan: TaskPlan,
        history: List<AgentMessage>,
        maxPool: Int,
        isActive: () -> Boolean,
        onSubtaskUpdate: (List<SwarmTask>) -> Unit,
        onAgentStates: (List<SwarmAgentState>) -> Unit,
        onAssistantMessage: (String) -> Unit,
        onToolEvent: (AgentMessage) -> Unit,
    ): String = coroutineScope {
        val semaphore = Semaphore(maxPool.coerceIn(1, 6))
        var tasks = plan.subtasks
        val agentStates = SwarmAgentType.entries.associateWith { SwarmAgentState(it) }.toMutableMap()

        fun publishAgents() = onAgentStates(agentStates.values.toList())

        fun markTaskCancelled(taskId: String) {
            tasks = tasks.map { task ->
                if (task.id == taskId && task.status != TaskStatus.COMPLETED) {
                    task.copy(status = TaskStatus.CANCELLED)
                } else {
                    task
                }
            }
            onSubtaskUpdate(tasks)
        }

        val results = tasks.map { task ->
            async {
                if (!isActive() || AgentRunController.isSubtaskCancelled(task.id)) {
                    markTaskCancelled(task.id)
                    return@async task.id to "Cancelled"
                }
                semaphore.withPermit {
                    if (!isActive() || AgentRunController.isSubtaskCancelled(task.id)) {
                        markTaskCancelled(task.id)
                        return@withPermit task.id to "Cancelled"
                    }
                    tasks = tasks.map { if (it.id == task.id) it.copy(status = TaskStatus.RUNNING) else it }
                    onSubtaskUpdate(tasks)
                    agentStates[task.agent] = agentStates[task.agent]!!.copy(
                        status = TaskStatus.RUNNING,
                        currentTask = task.title,
                    )
                    publishAgents()
                    AgentExecutionSession.setNotification("${task.agent.displayName}: ${task.title.take(40)}")

                    val summary = try {
                        runToolLoop(
                            config = config,
                            agent = task.agent,
                            systemPrompt = agentSystemPrompt(task.agent, skillsPromptSuffix()),
                            history = history,
                            userMessage = "Swarm step ${task.id}: ${task.title}\nOriginal task: ${plan.userTask}",
                            isActive = isActive,
                            subtaskId = task.id,
                            onAssistantDelta = {},
                            onToolEvent = onToolEvent,
                        )
                    } catch (e: CancellationException) {
                        markTaskCancelled(task.id)
                        agentStates[task.agent] = agentStates[task.agent]!!.copy(
                            status = TaskStatus.CANCELLED,
                            currentTask = null,
                        )
                        publishAgents()
                        return@withPermit task.id to "Cancelled"
                    }

                    if (AgentRunController.isSubtaskCancelled(task.id)) {
                        markTaskCancelled(task.id)
                        return@withPermit task.id to "Cancelled"
                    }

                    tasks = tasks.map {
                        if (it.id == task.id) it.copy(status = TaskStatus.COMPLETED) else it
                    }
                    onSubtaskUpdate(tasks)
                    val completed = (agentStates[task.agent]?.tasksCompleted ?: 0) + 1
                    agentStates[task.agent] = agentStates[task.agent]!!.copy(
                        status = TaskStatus.COMPLETED,
                        currentTask = null,
                        tasksCompleted = completed,
                    )
                    publishAgents()
                    task.id to summary
                }
            }
        }.awaitAll()

        val cancelled = tasks.any { it.status == TaskStatus.CANCELLED }
        val limitHit = AgentExecutionSession.isToolLimitReached()
        val report = buildString {
            when {
                limitHit -> appendLine("Swarm stopped: tool call limit (${AgentExecutionSession.snapshot.value.maxToolCalls}) reached.")
                cancelled -> appendLine("Swarm run cancelled.")
                else -> appendLine("Swarm execution complete.")
            }
            appendLine(plan.summary)
            results.forEach { (id, text) ->
                appendLine()
                appendLine("## Step $id")
                appendLine(text.take(1500))
            }
        }
        onAssistantMessage(report)
        report
    }

    private suspend fun runToolLoop(
        config: LlmConfig,
        agent: SwarmAgentType,
        systemPrompt: String,
        history: List<AgentMessage>,
        userMessage: String,
        isActive: () -> Boolean,
        onAssistantDelta: (String) -> Unit,
        onToolEvent: (AgentMessage) -> Unit,
        subtaskId: String? = null,
    ): String {
        val apiMessages = OpenAiCompatibleLlmClient.buildMessagesArray(systemPrompt, history, userMessage)
        val agentTools = tools.toolsForAgent(agent)
        var lastContent = ""
        var lastEmitted = 0
        val maxRounds = AgentExecutionSession.snapshot.value.maxToolCalls.coerceAtLeast(1)

        repeat(maxRounds) {
            ensureActive(isActive, subtaskId)
            if (AgentExecutionSession.isToolLimitReached()) throw ToolLimitReachedException()

            val result = OpenAiCompatibleLlmClient.complete(
                config = config,
                messages = apiMessages,
                tools = if (agentTools.length() > 0) agentTools else null,
                temperature = if (agent == SwarmAgentType.Planner) 0.3 else 0.4,
            )
            lastContent = result.content
            if (result.content.length > lastEmitted) {
                val delta = result.content.substring(lastEmitted)
                lastEmitted = result.content.length
                onAssistantDelta(delta)
            }
            if (result.toolCalls.isEmpty()) {
                if (result.content.isNotBlank()) {
                    apiMessages.put(JSONObject().put("role", "assistant").put("content", result.content))
                }
                return result.content.ifBlank { lastContent }
            }

            apiMessages.put(OpenAiCompatibleLlmClient.assistantToolCallMessage(result.toolCalls))
            result.toolCalls.forEach { call ->
                ensureActive(isActive, subtaskId)
                if (!AgentExecutionSession.tryRecordToolCall()) {
                    AgentRunController.requestStop()
                    throw ToolLimitReachedException()
                }
                executeToolCall(call, agent, onToolEvent) { toolMsg ->
                    apiMessages.put(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", toolMsg.toolCallId)
                            .put("content", toolMsg.content),
                    )
                }
            }
        }
        return lastContent.ifBlank { "Agent reached max tool iterations." }
    }

    private fun ensureActive(isActive: () -> Boolean, subtaskId: String?) {
        if (!isActive() || AgentRunController.isSubtaskCancelled(subtaskId)) {
            throw CancellationException("Agent stopped")
        }
    }

    private suspend fun executeToolCall(
        call: LlmToolCall,
        agent: SwarmAgentType,
        onToolEvent: (AgentMessage) -> Unit,
        onResult: (AgentMessage) -> Unit,
    ) {
        val shellCommand = extractShellCommand(call.name, call.arguments)
        val shellLogId = shellCommand?.let {
            AgentExecutionSession.beginShellCommand(agent.displayName, it)
        }

        val runningId = AgentExecutionSession.newMessageId()
        onToolEvent(
            AgentMessage(
                id = runningId,
                role = MessageRole.TOOL,
                content = call.arguments,
                toolName = call.name,
                toolCallId = call.id,
                agentName = agent.displayName,
                toolStatus = ToolCallStatus.RUNNING,
            ),
        )
        val output = runCatching {
            tools.execute(ToolInvocation(call.name, JSONObject(call.arguments)))
        }.getOrElse { "Tool error: ${it.message}" }

        val cancelled = output.contains("Cancelled by user", ignoreCase = true)
        val failed = output.startsWith("Error") || output.startsWith("Tool error")
        shellLogId?.let { logId ->
            val status = when {
                cancelled -> ShellCommandStatus.CANCELLED
                failed -> ShellCommandStatus.FAILED
                else -> ShellCommandStatus.COMPLETED
            }
            val exitCode = Regex("""exit\s+(-?\d+)""").find(output)?.groupValues?.get(1)?.toIntOrNull()
            AgentExecutionSession.completeShellCommand(logId, status, exitCode)
        }

        val done = AgentMessage(
            id = runningId,
            role = MessageRole.TOOL,
            content = output.take(4000),
            toolName = call.name,
            toolCallId = call.id,
            agentName = agent.displayName,
            toolStatus = when {
                cancelled -> ToolCallStatus.FAILED
                failed -> ToolCallStatus.FAILED
                else -> ToolCallStatus.COMPLETED
            },
        )
        onToolEvent(done)
        onResult(done)
    }

    private fun extractShellCommand(toolName: String, argsJson: String): String? {
        val args = runCatching { JSONObject(argsJson) }.getOrNull() ?: return null
        return when (toolName) {
            ProotShellTool.NAME -> args.optString("command").trim().takeIf { it.isNotEmpty() }
            CodeTool.NAME -> {
                val test = args.optString("test_command").trim()
                if (test.isNotEmpty()) test else "edit ${args.optString("path")}"
            }
            FileSystemTool.NAME_READ -> "cat ${args.optString("path")}"
            FileSystemTool.NAME_WRITE -> "write ${args.optString("path")}"
            else -> null
        }
    }

    private fun agentSystemPrompt(agent: SwarmAgentType, skillsSuffix: String): String {
        val base = when (agent) {
            SwarmAgentType.Planner -> "You are the Planner. Break work into clear steps."
            SwarmAgentType.Researcher -> "You are the Researcher. Use web_fetch and read_file to gather facts."
            SwarmAgentType.Executor -> "You are the Executor. Run shell commands in proot to accomplish tasks."
            SwarmAgentType.Coder -> "You are the Coder. Use edit_and_test_code and shell tools."
            SwarmAgentType.Validator -> "You are the Validator. Verify outputs and report pass/fail."
            SwarmAgentType.Slack -> "You are Slack notifications. Summarize progress via shell echo and short messages."
        }
        return if (skillsSuffix.isBlank()) base else "$base\n$skillsSuffix"
    }

    private suspend fun skillsPromptSuffix(): String = runCatching {
        val discovered = skillRepository.discover()
        val summary = skillRepository.activeSkillSummaries(discovered)
        if (summary.isBlank()) {
            ""
        } else {
            "Enabled skills: $summary. Use skills_list to browse and skill_view before applying a skill workflow. Use skill_manage only to propose new skills (requires user approval)."
        }
    }.getOrDefault("")

    fun parsePlanJson(raw: String, userTask: String): TaskPlan {
        val jsonText = normalizePlanJson(extractJsonObject(raw))
        return runCatching {
            val obj = JSONObject(jsonText)
            val summary = obj.optString("summary", "Swarm plan")
            val subtasks = parseSubtasksFromJson(obj.optJSONArray("subtasks"))
            TaskPlan(
                id = UUID.randomUUID().toString(),
                summary = summary,
                userTask = userTask,
                subtasks = subtasks.ifEmpty { parseSubtasksWithRegex(raw).ifEmpty { fallbackTasks(userTask) } },
            )
        }.getOrElse {
            val regexTasks = parseSubtasksWithRegex(raw)
            TaskPlan(
                id = UUID.randomUUID().toString(),
                summary = extractSummaryWithRegex(raw) ?: "Swarm plan",
                userTask = userTask,
                subtasks = regexTasks.ifEmpty { fallbackTasks(userTask) },
            )
        }
    }

    private fun parseSubtasksFromJson(arr: JSONArray?): List<SwarmTask> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val item = arr.optJSONObject(i) ?: return@mapNotNull null
            SwarmTask(
                id = item.optString("id", "${i + 1}"),
                title = item.optString("title", "Step ${i + 1}"),
                agent = SwarmAgentType.fromString(item.optString("agent", "Executor")),
                parallelizable = item.optBoolean("parallelizable", true),
            )
        }
    }

    private fun parseSubtasksWithRegex(raw: String): List<SwarmTask> {
        val tasks = mutableListOf<SwarmTask>()
        val pattern = Regex(
            """\{\s*"id"\s*:\s*"([^"]+)"\s*,\s*"title"\s*:\s*"([^"]+)"\s*,\s*"agent"\s*:\s*"([^"]+)"""",
            RegexOption.IGNORE_CASE,
        )
        pattern.findAll(raw).forEach { match ->
            tasks.add(
                SwarmTask(
                    id = match.groupValues[1],
                    title = match.groupValues[2],
                    agent = SwarmAgentType.fromString(match.groupValues[3]),
                ),
            )
        }
        return tasks
    }

    private fun extractSummaryWithRegex(raw: String): String? {
        return Regex(""""summary"\s*:\s*"([^"]+)"""").find(raw)?.groupValues?.get(1)
    }

    private fun normalizePlanJson(text: String): String = text
        .replace(Regex("""\s+"""), " ")
        .replace(Regex("""fa\s+lse""", RegexOption.IGNORE_CASE), "false")
        .replace(Regex("""tr\s+ue""", RegexOption.IGNORE_CASE), "true")
        .replace(Regex(""""\s*title"""), "\"title\"")
        .replace(Regex("""\{\s*"""), "{")
        .trim()

    private fun fallbackTasks(userTask: String): List<SwarmTask> = listOf(
        SwarmTask("1", "Research: $userTask", SwarmAgentType.Researcher),
        SwarmTask("2", "Execute in proot", SwarmAgentType.Executor),
        SwarmTask("3", "Validate results", SwarmAgentType.Validator),
    )

    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) return text.substring(start, end + 1)
        return text
    }

    suspend fun testConnection(config: LlmConfig) = OpenAiCompatibleLlmClient.testConnection(config)
}

/** Legacy entry point — delegates to [CoworkAgentRunner]. */
object CoworkKoogAgentRunner {
    suspend fun streamChat(
        config: LlmConfig,
        mode: ExecutionMode,
        history: List<AgentMessage>,
        userMessage: String,
        isActive: () -> Boolean,
        onDelta: (String) -> Unit,
    ): String {
        val system = when (mode) {
            ExecutionMode.SWARM -> "You are Cowork Swarm planner. Produce a numbered plan."
            ExecutionMode.FAST -> "You are Cowork Fast agent. Respond concisely."
        }
        return OpenAiCompatibleLlmClient.streamChat(
            config, system, history, userMessage,
            temperature = if (mode == ExecutionMode.FAST) 0.3 else 0.5,
            isActive = isActive,
            onDelta = onDelta,
        )
    }

    suspend fun testConnection(config: LlmConfig) = OpenAiCompatibleLlmClient.testConnection(config)

    fun parseSwarmTasks(response: String, userTask: String): List<SwarmTask> {
        val lines = response.lines()
        val numbered = lines.mapNotNull { line ->
            val trimmed = line.trim()
            val match = Regex("^(\\d+)[.)]\\s+(.+)").find(trimmed)
            match?.let { it.groupValues[1] to it.groupValues[2].trim() }
        }
        if (numbered.isEmpty()) {
            return listOf(
                SwarmTask("1", "Analyze: $userTask"),
                SwarmTask("2", "Plan execution in proot"),
                SwarmTask("3", "Execute and verify"),
            )
        }
        return numbered.take(6).map { (id, title) -> SwarmTask(id, title) }
    }
}
