package com.proot.cowork.domain.agent

import com.proot.cowork.data.llm.LlmEndpoint
import com.proot.cowork.data.llm.OpenAiCompatibleLlmClient
import com.proot.cowork.data.prefs.LlmConfig

/**
 * Cowork chat runner with Swarm (multi-agent plan) and Fast (single-agent) modes.
 * Uses the saved OpenAI-compatible endpoint (OpenRouter by default) with SSE streaming.
 */
object CoworkKoogAgentRunner {

    suspend fun streamChat(
        config: LlmConfig,
        mode: ExecutionMode,
        history: List<AgentMessage>,
        userMessage: String,
        isActive: () -> Boolean,
        onDelta: (String) -> Unit,
    ): String {
        require(LlmEndpoint.isConfigured(config)) { "Configure API key, base URL, and model in Settings" }
        val systemPrompt = systemPromptFor(mode)
        val temperature = if (mode == ExecutionMode.FAST) 0.3 else 0.5

        return OpenAiCompatibleLlmClient.streamChat(
            config = config,
            systemPrompt = systemPrompt,
            history = history,
            userMessage = userMessage,
            temperature = temperature,
            isActive = isActive,
            onDelta = onDelta,
        )
    }

    suspend fun testConnection(config: LlmConfig): Result<String> {
        return OpenAiCompatibleLlmClient.testConnection(config)
    }

    private fun systemPromptFor(mode: ExecutionMode): String = when (mode) {
        ExecutionMode.SWARM -> """
            You are the Cowork Swarm orchestrator for a mobile Ubuntu proot desktop environment.
            Decompose the user's task into a clear numbered plan for specialized agents (Planner, Researcher, Executor, Coder, Validator).
            Be concise, actionable, and mention which agent handles each step.
            Do not claim you already executed commands unless tool results are provided.
        """.trimIndent()
        ExecutionMode.FAST -> """
            You are the Cowork Fast agent for a mobile Ubuntu proot desktop environment.
            Respond directly and concisely. Prefer immediate, practical steps the user can run.
            Do not produce a multi-agent plan unless the user explicitly asks for one.
        """.trimIndent()
    }

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
