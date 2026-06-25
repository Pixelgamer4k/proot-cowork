package com.proot.cowork.data.llm

import com.proot.cowork.data.prefs.LlmConfig
import com.proot.cowork.domain.agent.AgentMessage
import com.proot.cowork.domain.agent.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class LlmCompletionResult(
    val content: String,
    val toolCalls: List<LlmToolCall>,
    val finishReason: String?,
)

object OpenAiCompatibleLlmClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection(config: LlmConfig): Result<String> = withContext(Dispatchers.IO) {
        if (!LlmEndpoint.isConfigured(config)) {
            return@withContext Result.failure(IllegalStateException("API key, base URL, and model are required"))
        }
        runCatching {
            val endpoint = LlmEndpoint.from(config)
            val payload = JSONObject().apply {
                put("model", config.model.trim())
                put("max_tokens", 8)
                put("stream", false)
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))
            }
            val request = buildRequest(endpoint, config.apiKey, payload.toString())
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: ${body.take(240)}")
                }
                "Connected to ${endpoint.baseUrl} (${response.code})"
            }
        }
    }

    suspend fun streamChat(
        config: LlmConfig,
        systemPrompt: String,
        history: List<AgentMessage>,
        userMessage: String,
        temperature: Double,
        isActive: () -> Boolean,
        onDelta: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val endpoint = LlmEndpoint.from(config)
        val payload = JSONObject().apply {
            put("model", config.model.trim())
            put("stream", true)
            put("temperature", temperature)
            put("messages", buildMessagesArray(systemPrompt, history, userMessage))
        }
        val request = buildRequest(endpoint, config.apiKey, payload.toString())
        val buffer = StringBuilder()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string().orEmpty()
                error("HTTP ${response.code}: ${err.take(300)}")
            }
            val source = response.body?.source() ?: error("Empty response body")
            while (!source.exhausted()) {
                if (!isActive()) break
                val line = source.readUtf8Line() ?: continue
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                val delta = runCatching {
                    JSONObject(data)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?.optString("content")
                        .orEmpty()
                }.getOrDefault("")
                if (delta.isNotEmpty()) {
                    buffer.append(delta)
                    onDelta(delta)
                }
            }
        }
        buffer.toString()
    }

    suspend fun complete(
        config: LlmConfig,
        messages: JSONArray,
        tools: JSONArray? = null,
        temperature: Double = 0.4,
        maxTokens: Int = 4096,
    ): LlmCompletionResult = withContext(Dispatchers.IO) {
        val endpoint = LlmEndpoint.from(config)
        val payload = JSONObject().apply {
            put("model", config.model.trim())
            put("stream", false)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("messages", messages)
            if (tools != null && tools.length() > 0) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
        }
        val request = buildRequest(endpoint, config.apiKey, payload.toString())
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${body.take(400)}")
            }
            parseCompletion(JSONObject(body))
        }
    }

    fun buildMessagesArray(
        systemPrompt: String,
        history: List<AgentMessage>,
        userMessage: String? = null,
    ): JSONArray {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        history.takeLast(24).forEach { msg -> appendMessage(messages, msg) }
        if (!userMessage.isNullOrBlank()) {
            messages.put(JSONObject().put("role", "user").put("content", userMessage))
        }
        return messages
    }

    fun appendMessage(messages: JSONArray, msg: AgentMessage) {
        when (msg.role) {
            MessageRole.USER -> {
                if (msg.content.isNotBlank()) {
                    messages.put(JSONObject().put("role", "user").put("content", msg.content))
                }
            }
            MessageRole.ASSISTANT -> {
                if (msg.content.isNotBlank()) {
                    messages.put(JSONObject().put("role", "assistant").put("content", msg.content))
                }
            }
            MessageRole.SYSTEM -> {
                if (msg.content.isNotBlank()) {
                    messages.put(JSONObject().put("role", "system").put("content", msg.content))
                }
            }
            MessageRole.TOOL -> {
                if (msg.toolCallId != null && msg.content.isNotBlank()) {
                    messages.put(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", msg.toolCallId)
                            .put("content", msg.content),
                    )
                }
            }
        }
    }

    fun assistantToolCallMessage(toolCalls: List<LlmToolCall>): JSONObject {
        val msg = JSONObject().put("role", "assistant")
        val calls = JSONArray()
        toolCalls.forEach { call ->
            calls.put(
                JSONObject()
                    .put("id", call.id)
                    .put("type", "function")
                    .put("function", JSONObject().put("name", call.name).put("arguments", call.arguments)),
            )
        }
        msg.put("tool_calls", calls)
        return msg
    }

    private fun parseCompletion(json: JSONObject): LlmCompletionResult {
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
            ?: return LlmCompletionResult("", emptyList(), null)
        val message = choice.optJSONObject("message") ?: JSONObject()
        val content = message.optString("content").orEmpty()
        val finish = choice.optString("finish_reason").ifBlank { null }
        val toolCalls = message.optJSONArray("tool_calls")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val tc = arr.optJSONObject(i) ?: return@mapNotNull null
                val fn = tc.optJSONObject("function") ?: return@mapNotNull null
                LlmToolCall(
                    id = tc.optString("id", "call_$i"),
                    name = fn.optString("name"),
                    arguments = fn.optString("arguments", "{}"),
                )
            }
        }.orEmpty()
        return LlmCompletionResult(content, toolCalls, finish)
    }

    private fun buildRequest(endpoint: ResolvedLlmEndpoint, apiKey: String, payload: String): Request {
        return Request.Builder()
            .url(endpoint.chatCompletionsUrl)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/Pixelgamer4k/Proot-Cowork")
            .addHeader("X-Title", "Proot Cowork")
            .post(payload.toRequestBody(jsonMediaType))
            .build()
    }
}
