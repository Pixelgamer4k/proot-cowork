package com.proot.cowork.data.llm

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val LONGCAT_TAG_START = "<longcat_tool_call>"

/**
 * Parses LongCat / Kimi-style tool calls embedded in assistant text:
 * <longcat_tool_call>todo_write
 * <longcat_arg_key>todos</longcat_arg_key>
 * <longcat_arg_value>[...]</longcat_arg_value>
 * </longcat_tool_call>
 */
object LongcatToolCallParser {

    private val blockRegex = Regex(
        """<longcat_tool_call>(.*?)</longcat_tool_call>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val partialBlockRegex = Regex(
        """<longcat_tool_call>(.*)$""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val kvRegex = Regex(
        """<longcat_arg_key>(.*?)</longcat_arg_key>\s*<longcat_arg_value>(.*?)</longcat_arg_value>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val tagStart = LONGCAT_TAG_START

    fun parse(content: String): ParsedLongcatContent {
        if (!content.contains(tagStart, ignoreCase = true)) {
            return ParsedLongcatContent(content.trim(), emptyList())
        }

        val toolCalls = mutableListOf<LlmToolCall>()
        blockRegex.findAll(content).forEach { match ->
            parseBlock(match.groupValues[1])?.let { toolCalls.add(it) }
        }

        var cleaned = blockRegex.replace(content, "")
        cleaned = partialBlockRegex.replace(cleaned, "")
        cleaned = cleaned.trim()

        return ParsedLongcatContent(cleaned, toolCalls)
    }

    private fun parseBlock(rawBlock: String): LlmToolCall? {
        val block = rawBlock.trim()
        if (block.isBlank()) return null

        val jsonObject = runCatching { JSONObject(block) }.getOrNull()
        if (jsonObject != null && jsonObject.has("name")) {
            val name = normalizeToolName(jsonObject.optString("name"))
            val args = jsonObject.opt("arguments")
            val arguments = when (args) {
                is JSONObject -> normalizeArguments(name, args).toString()
                is String -> args
                else -> "{}"
            }
            return LlmToolCall(
                id = "longcat_${UUID.randomUUID()}",
                name = name,
                arguments = arguments.ifBlank { "{}" },
            )
        }

        val firstTag = block.indexOf('<')
        val header = if (firstTag < 0) block else block.substring(0, firstTag).trim()
        val kvSection = if (firstTag < 0) "" else block.substring(firstTag)
        val argsJson = JSONObject()
        kvRegex.findAll(kvSection).forEach { match ->
            val key = match.groupValues[1].trim()
            val value = match.groupValues[2].trim()
            if (key.isNotBlank()) argsJson.put(key, coerceArgValue(value))
        }

        val name = normalizeToolName(header.lines().firstOrNull().orEmpty().trim())
            .ifBlank { inferToolName(argsJson) }
        if (name.isBlank()) return null

        return LlmToolCall(
            id = "longcat_${UUID.randomUUID()}",
            name = name,
            arguments = normalizeArguments(name, argsJson).toString(),
        )
    }

    private fun normalizeToolName(raw: String): String = when (raw.lowercase()) {
        "write" -> "todo_write"
        else -> raw
    }

    private fun inferToolName(args: JSONObject): String = when {
        args.has("todos") || args.has("todo_id") || args.has("description") -> "todo_write"
        args.has("path") && args.has("content") -> "write_file"
        args.has("path") && (args.has("old_string") || args.has("new_string")) -> "edit_file"
        args.has("path") -> "read_file"
        args.has("url") -> "web_fetch"
        args.has("command") -> "proot_shell"
        else -> ""
    }

    private fun normalizeArguments(name: String, args: JSONObject): JSONObject = when (name) {
        "todo_write" -> normalizeTodoWriteArgs(args)
        else -> args
    }

    private fun normalizeTodoWriteArgs(args: JSONObject): JSONObject {
        args.optJSONArray("todos")?.takeIf { it.length() > 0 }?.let { return args }

        val items = JSONArray()

        args.optJSONArray("items")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { items.put(normalizeTodoItem(it, i)) }
            }
            if (items.length() > 0) return JSONObject().put("todos", items)
        }

        if (args.has("description") || args.has("content") || args.has("todo_id") || args.has("id")) {
            items.put(normalizeTodoItem(args, 0))
            return JSONObject().put("todos", items)
        }

        return args
    }

    private fun normalizeTodoItem(obj: JSONObject, index: Int): JSONObject = JSONObject().apply {
        put("id", obj.optString("id").ifBlank { obj.optString("todo_id").ifBlank { "${index + 1}" } })
        put(
            "content",
            obj.optString("content").ifBlank {
                obj.optString("description").ifBlank { "Task ${index + 1}" }
            },
        )
        put("status", obj.optString("status", "pending"))
        put("priority", obj.optString("priority", "medium"))
    }

    private fun coerceArgValue(value: String): Any {
        val trimmed = value.trim()
        if (trimmed.startsWith("[")) {
            return runCatching { JSONArray(trimmed) }.getOrDefault(trimmed)
        }
        if (trimmed.startsWith("{")) {
            return runCatching { JSONObject(trimmed) }.getOrDefault(trimmed)
        }
        return trimmed
    }
}

data class ParsedLongcatContent(
    val content: String,
    val toolCalls: List<LlmToolCall>,
)

/**
 * Suppresses LongCat tool XML from streamed assistant text while preserving prose.
 */
class LongcatStreamDisplayFilter(
    private val onDelta: (String) -> Unit,
) {
    private val pending = StringBuilder()
    private var emittedLength = 0
    private val holdBack = LONGCAT_TAG_START.length

    fun onChunk(chunk: String) {
        if (chunk.isEmpty()) return
        pending.append(chunk)
        flushSafePrefix()
    }

    fun finish() {
        val text = pending.toString()
        val tagAt = text.indexOf(LONGCAT_TAG_START, ignoreCase = true)
        val end = if (tagAt >= 0) tagAt else text.length
        emitRange(end)
    }

    private fun flushSafePrefix() {
        val text = pending.toString()
        val tagAt = text.indexOf(LONGCAT_TAG_START, ignoreCase = true)
        if (tagAt >= 0) {
            emitRange(tagAt)
            return
        }
        if (text.length > holdBack) {
            emitRange(text.length - holdBack)
        }
    }

    private fun emitRange(endExclusive: Int) {
        if (endExclusive <= emittedLength) return
        onDelta(pending.substring(emittedLength, endExclusive))
        emittedLength = endExclusive
    }
}
