package com.proot.cowork.domain.agent

import android.content.Context
import com.proot.cowork.data.llm.LlmEndpoint
import com.proot.cowork.data.llm.LlmToolCall
import com.proot.cowork.data.llm.OpenAiCompatibleLlmClient
import com.proot.cowork.data.prefs.LlmConfig
import com.proot.cowork.domain.agent.tools.AgentToolRegistry
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

class CoworkAgentRunner(private val context: Context) {

    private val tools = AgentToolRegistry(context)

    suspend fun planSwarm(
        config: LlmConfig,
        userTask: String,
        history: List<AgentMessage>,
        isActive: () -> Boolean,
        onPlanDelta: (String) -> Unit,
    ): TaskPlan {
        require(LlmEndpoint.isConfigured(config))
        val system = """
            You are the Cowork Swarm Planner. Output ONLY valid JSON (no markdown fences) with:
            {"summary":"one line","subtasks":[{"id":"1","title":"...","agent":"Researcher|Executor|Coder|Validator|Slack","parallelizable":true}]}
            Assign each step to Planner, Researcher, Executor, Coder, Validator, or Slack.
            Keep 3-6 subtasks. Be actionable for an Ubuntu proot mobile desktop.
        """.trimIndent()
        val messages = OpenAiCompatibleLlmClient.buildMessagesArray(system, history, userTask)
        val result = OpenAiCompatibleLlmClient.complete(config, messages, temperature = 0.3)
        onPlanDelta(result.content)
        return parsePlanJson(result.content, userTask)
    }

    suspend fun runFast(
        config: LlmConfig,
        userTask: String,
        history: List<AgentMessage>,
        isActive: () -> Boolean,
        onAssistantDelta: (String) -> Unit,
        onToolEvent: (AgentMessage) -> Unit,
    ): String {
        val system = """
            You are the Cowork Fast agent with direct access to proot tools.
            Execute the user's task using tools when needed. Be concise in final answers.
        """.trimIndent()
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

        val results = tasks.map { task ->
            async {
                if (!isActive()) return@async task.id to "Cancelled"
                semaphore.withPermit {
                    if (!isActive()) return@withPermit task.id to "Cancelled"
                    tasks = tasks.map { if (it.id == task.id) it.copy(status = TaskStatus.RUNNING) else it }
                    onSubtaskUpdate(tasks)
                    agentStates[task.agent] = agentStates[task.agent]!!.copy(
                        status = TaskStatus.RUNNING,
                        currentTask = task.title,
                    )
                    publishAgents()
                    AgentExecutionSession.setNotification("${task.agent.displayName}: ${task.title.take(40)}")

                    val summary = runToolLoop(
                        config = config,
                        agent = task.agent,
                        systemPrompt = agentSystemPrompt(task.agent),
                        history = history,
                        userMessage = "Swarm step ${task.id}: ${task.title}\nOriginal task: ${plan.userTask}",
                        isActive = isActive,
                        onAssistantDelta = {},
                        onToolEvent = onToolEvent,
                    )

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

        val report = buildString {
            appendLine("Swarm execution complete.")
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
        maxIterations: Int = 12,
    ): String {
        val apiMessages = OpenAiCompatibleLlmClient.buildMessagesArray(systemPrompt, history, userMessage)
        val agentTools = tools.toolsForAgent(agent)
        var lastContent = ""

        var lastEmitted = 0
        repeat(maxIterations) {
            if (!isActive()) throw CancellationException("Agent stopped")
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

    private suspend fun executeToolCall(
        call: LlmToolCall,
        agent: SwarmAgentType,
        onToolEvent: (AgentMessage) -> Unit,
        onResult: (AgentMessage) -> Unit,
    ) {
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
        val done = AgentMessage(
            id = runningId,
            role = MessageRole.TOOL,
            content = output.take(4000),
            toolName = call.name,
            toolCallId = call.id,
            agentName = agent.displayName,
            toolStatus = if (output.startsWith("Error")) ToolCallStatus.FAILED else ToolCallStatus.COMPLETED,
        )
        onToolEvent(done)
        onResult(done)
    }

    private fun agentSystemPrompt(agent: SwarmAgentType): String = when (agent) {
        SwarmAgentType.Planner -> "You are the Planner. Break work into clear steps."
        SwarmAgentType.Researcher -> "You are the Researcher. Use web_fetch and read_file to gather facts."
        SwarmAgentType.Executor -> "You are the Executor. Run shell commands in proot to accomplish tasks."
        SwarmAgentType.Coder -> "You are the Coder. Use edit_and_test_code and shell tools."
        SwarmAgentType.Validator -> "You are the Validator. Verify outputs and report pass/fail."
        SwarmAgentType.Slack -> "You are Slack notifications. Summarize progress via shell echo and short messages."
    }

    fun parsePlanJson(raw: String, userTask: String): TaskPlan {
        val jsonText = extractJsonObject(raw)
        return runCatching {
            val obj = JSONObject(jsonText)
            val summary = obj.optString("summary", "Swarm plan")
            val subtasks = obj.optJSONArray("subtasks")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val item = arr.optJSONObject(i) ?: return@mapNotNull null
                    SwarmTask(
                        id = item.optString("id", "${i + 1}"),
                        title = item.optString("title", "Step ${i + 1}"),
                        agent = SwarmAgentType.fromString(item.optString("agent", "Executor")),
                        parallelizable = item.optBoolean("parallelizable", true),
                    )
                }
            }.orEmpty()
            TaskPlan(
                id = UUID.randomUUID().toString(),
                summary = summary,
                userTask = userTask,
                subtasks = subtasks.ifEmpty { fallbackTasks(userTask) },
            )
        }.getOrElse {
            TaskPlan(
                id = UUID.randomUUID().toString(),
                summary = "Swarm plan",
                userTask = userTask,
                subtasks = fallbackTasks(userTask),
            )
        }
    }

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
        // Simple chat without tools when called directly (fallback).
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
