package com.proot.cowork.domain.agent

import android.content.Context
import com.proot.cowork.data.llm.LlmEndpoint
import com.proot.cowork.data.prefs.LlmConfig
import com.proot.cowork.data.llm.LlmToolCall
import com.proot.cowork.data.llm.OpenAiCompatibleLlmClient
import com.proot.cowork.data.files.GuestPaths
import com.proot.cowork.data.proot.ProotGuestShellExecutor
import com.proot.cowork.data.skills.SkillRepository
import com.proot.cowork.domain.agent.orchestration.ExecutionStage
import com.proot.cowork.domain.agent.orchestration.ModerateTaskRunner
import com.proot.cowork.domain.agent.orchestration.PlanWriter
import com.proot.cowork.domain.agent.orchestration.StageValidator
import com.proot.cowork.domain.agent.orchestration.SystemPromptBuilder
import com.proot.cowork.domain.agent.orchestration.TaskClassifier
import com.proot.cowork.domain.agent.orchestration.TaskClassification
import com.proot.cowork.domain.agent.orchestration.TaskComplexity
import com.proot.cowork.domain.agent.orchestration.ToolPolicy
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
        threadId: String?,
        isActive: () -> Boolean,
        onAssistantDelta: (String) -> Unit,
        onToolEvent: (AgentMessage) -> Unit,
        onSystemNotice: (String) -> Unit = {},
    ): String {
        AgentRunContext.reset(threadId)
        val classification = TaskClassifier.classify(userTask)
        val shell = ProotGuestShellExecutor(context)
        val planWriter = PlanWriter(shell)
        planWriter.ensureOutputDir()

        if (classification.complexity.requiresPlan()) {
            planWriter.writePlan(classification)
            onSystemNotice(
                "Execution plan saved to ${GuestPaths.planFilePath()} " +
                    "(${classification.complexity.name}: ${classification.rationale})",
            )
        }

        val system = SystemPromptBuilder.build(
            skillsSuffix = if (classification.complexity.isToolFree()) "" else skillsPromptSuffix(),
            classification = classification,
            planPath = GuestPaths.planFilePath(),
        )

        val toolPolicy = if (classification.complexity.isToolFree()) ToolPolicy.NONE else ToolPolicy.FULL

        return when {
            classification.complexity.requiresStagedExecution() -> runStagedFast(
                config = config,
                classification = classification,
                systemPrompt = system,
                history = history,
                userTask = userTask,
                isActive = isActive,
                onAssistantDelta = onAssistantDelta,
                onToolEvent = onToolEvent,
            )
            classification.complexity == TaskComplexity.MODERATE -> {
                ModerateTaskRunner(shell, tools, moderateToolLoop()).run(
                    config = config,
                    systemPrompt = system,
                    history = history,
                    userTask = userTask,
                    classification = classification,
                    isActive = isActive,
                    onAssistantDelta = onAssistantDelta,
                    onToolEvent = onToolEvent,
                )
            }
            else -> {
                val taskMessage = if (classification.complexity.requiresPlan()) {
                    moderateTaskMessage(userTask)
                } else {
                    userTask
                }
                runToolLoop(
                    config = config,
                    agent = SwarmAgentType.Executor,
                    systemPrompt = system,
                    history = history,
                    userMessage = taskMessage,
                    isActive = isActive,
                    onAssistantDelta = onAssistantDelta,
                    onToolEvent = onToolEvent,
                    toolPolicy = toolPolicy,
                    maxRounds = if (toolPolicy == ToolPolicy.NONE) 1 else null,
                    temperatureOverride = if (toolPolicy == ToolPolicy.NONE) 0.1 else null,
                )
            }
        }
    }

    private fun moderateToolLoop(): ToolLoopRunner = ToolLoopRunner { config, agent, systemPrompt, history, userMessage, isActive, onAssistantDelta, onToolEvent, toolFilter, maxRounds ->
        runToolLoop(
            config = config,
            agent = agent,
            systemPrompt = systemPrompt,
            history = history,
            userMessage = userMessage,
            isActive = isActive,
            onAssistantDelta = onAssistantDelta,
            onToolEvent = onToolEvent,
            toolFilter = toolFilter,
            maxRounds = maxRounds,
        )
    }

    private fun moderateTaskMessage(userTask: String): String = buildString {
        appendLine(userTask)
        appendLine()
        appendLine("Execution constraints:")
        appendLine("- Plan is at ${GuestPaths.planFilePath()}; follow it efficiently.")
        appendLine("- Research with at most **2** web_fetch calls, then write **${GuestPaths.AGENT_OUTPUT_DIR}/summary.md**.")
        appendLine("- Prefer write_file over repeated shell. Stop once summary.md exists.")
        appendLine("- Stay within the tool-call budget; do not loop on validation.")
    }

    private suspend fun runStagedFast(
        config: LlmConfig,
        classification: TaskClassification,
        systemPrompt: String,
        history: List<AgentMessage>,
        userTask: String,
        isActive: () -> Boolean,
        onAssistantDelta: (String) -> Unit,
        onToolEvent: (AgentMessage) -> Unit,
    ): String {
        val validator = StageValidator()
        val stages = classification.suggestedStages
        val completed = mutableListOf<Pair<ExecutionStage, String>>()
        var lastOutput = ""

        for ((stageIndex, stage) in stages.withIndex()) {
            var attempt = 0
            var feedback = ""
            var passed = false
            while (attempt < 2 && isActive()) {
                attempt++
                if (AgentExecutionSession.isToolLimitReached()) break
                val stagesLeft = (stages.size - stageIndex).coerceAtLeast(1)
                val remaining = remainingToolCalls()
                val stageBudget = (remaining / stagesLeft).coerceIn(2, 15)
                val prompt = stagePrompt(
                    userTask = userTask,
                    stage = stage,
                    completed = completed,
                    attempt = attempt,
                    feedback = feedback,
                )
                lastOutput = runToolLoop(
                    config = config,
                    agent = stageAgent(stage),
                    systemPrompt = systemPrompt,
                    history = history,
                    userMessage = prompt,
                    isActive = isActive,
                    onAssistantDelta = onAssistantDelta,
                    onToolEvent = onToolEvent,
                    maxRounds = stageBudget,
                )
                if (AgentExecutionSession.isToolLimitReached()) break
                val validation = if (stage == ExecutionStage.INTEGRATE) {
                    com.proot.cowork.domain.agent.orchestration.ValidationResult(
                        passed = lastOutput.isNotBlank(),
                        feedback = "Integration output required",
                    )
                } else {
                    validator.validate(
                        config = config,
                        stage = stage,
                        stageOutput = lastOutput,
                        userTask = userTask,
                        isActive = isActive,
                    )
                }
                if (validation.passed) {
                    passed = true
                    completed.add(stage to lastOutput)
                    break
                }
                feedback = validation.feedback
            }
            if (!passed) {
                if (AgentExecutionSession.isToolLimitReached()) {
                    return buildString {
                        appendLine("Stopped: tool call limit (${AgentExecutionSession.snapshot.value.maxToolCalls}) reached during **${stage.label}**.")
                        appendLine()
                        append(lastOutput.take(2000).ifBlank { "Partial progress only — check ${GuestPaths.AGENT_OUTPUT_DIR} for files." })
                    }
                }
                return buildString {
                    appendLine("Stage **${stage.label}** failed after 2 attempts.")
                    if (feedback.isNotBlank()) appendLine("Last feedback: $feedback")
                    appendLine()
                    append(lastOutput.take(2000))
                }
            }
        }
        return lastOutput
    }

    private fun remainingToolCalls(): Int {
        val snap = AgentExecutionSession.snapshot.value
        return (snap.maxToolCalls - snap.toolCallCount).coerceAtLeast(0)
    }

    private fun stageAgent(stage: ExecutionStage): SwarmAgentType = when (stage) {
        ExecutionStage.RESEARCH -> SwarmAgentType.Researcher
        ExecutionStage.EXECUTE -> SwarmAgentType.Executor
        ExecutionStage.VALIDATE -> SwarmAgentType.Validator
        ExecutionStage.INTEGRATE -> SwarmAgentType.Executor
    }

    private fun stagePrompt(
        userTask: String,
        stage: ExecutionStage,
        completed: List<Pair<ExecutionStage, String>>,
        attempt: Int,
        feedback: String,
    ): String = buildString {
        appendLine("Original user task: $userTask")
        appendLine("Current stage: ${stage.label} (attempt $attempt/3)")
        appendLine(stage.agentHint)
        if (completed.isNotEmpty()) {
            appendLine()
            appendLine("Completed stages:")
            completed.forEach { (s, out) ->
                appendLine("- ${s.label}: ${out.take(400)}")
            }
        }
        if (feedback.isNotBlank()) {
            appendLine()
            appendLine("Previous attempt failed validation: $feedback")
            appendLine("Fix the issues and complete this stage.")
        }
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
        onAssistantDelta: (String) -> Unit = {},
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
                            onAssistantDelta = onAssistantDelta,
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
        maxRounds: Int? = null,
        toolPolicy: ToolPolicy = ToolPolicy.FULL,
        toolFilter: AgentToolRegistry.ToolFilter = AgentToolRegistry.ToolFilter.FULL,
        temperatureOverride: Double? = null,
    ): String {
        val apiMessages = OpenAiCompatibleLlmClient.buildMessagesArray(systemPrompt, history, userMessage)
        val agentTools = when (toolPolicy) {
            ToolPolicy.NONE -> null
            ToolPolicy.FULL -> tools.toolsForAgent(agent, toolFilter).takeIf { it.length() > 0 }
        }
        var lastContent = ""
        val budgetLeft = remainingToolCalls()
        val roundLimit = when {
            maxRounds != null -> minOf(maxRounds, budgetLeft).coerceAtLeast(1)
            else -> budgetLeft.coerceAtLeast(1)
        }
        val temperature = temperatureOverride ?: if (agent == SwarmAgentType.Planner) 0.3 else 0.4

        repeat(roundLimit) {
            ensureActive(isActive, subtaskId)
            if (AgentExecutionSession.isToolLimitReached()) throw ToolLimitReachedException()

            val result = OpenAiCompatibleLlmClient.completeStreaming(
                config = config,
                messages = apiMessages,
                tools = agentTools,
                temperature = temperature,
                isActive = isActive,
                onDelta = onAssistantDelta,
            )
            lastContent = result.content
            if (result.toolCalls.isEmpty()) {
                if (result.content.isNotBlank()) {
                    apiMessages.put(JSONObject().put("role", "assistant").put("content", result.content))
                }
                return result.content.ifBlank { lastContent }
            }

            if (toolPolicy == ToolPolicy.NONE) {
                return result.content.ifBlank { "Unable to answer without tools disabled." }
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
            FileSystemTool.NAME_EDIT -> "edit ${args.optString("path")}"
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
            SwarmAgentType.Slack -> "You are Slack notifications. Post concise progress updates with slack_notify. Summarize milestones, not every shell line."
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
