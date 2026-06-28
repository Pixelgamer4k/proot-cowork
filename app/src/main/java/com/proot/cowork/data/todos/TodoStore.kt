package com.proot.cowork.data.todos

import android.content.Context
import com.proot.cowork.domain.agent.AgentRunContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class TodoStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    ;

    companion object {
        fun fromString(raw: String): TodoStatus = runCatching {
            valueOf(raw.uppercase())
        }.getOrDefault(PENDING)
    }
}

data class TodoItem(
    val id: String,
    val content: String,
    val status: TodoStatus,
    val priority: String = "medium",
)

class TodoStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "chat/todos").also { it.mkdirs() }

    suspend fun load(threadId: String): List<TodoItem> = withContext(Dispatchers.IO) {
        val file = fileFor(threadId)
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                TodoItem(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    content = obj.optString("content", ""),
                    status = TodoStatus.fromString(obj.optString("status", "pending")),
                    priority = obj.optString("priority", "medium"),
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun save(threadId: String, items: List<TodoItem>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("content", item.content)
                    .put("status", item.status.name.lowercase())
                    .put("priority", item.priority),
            )
        }
        fileFor(threadId).writeText(arr.toString())
    }

    suspend fun writeTodos(threadId: String, incoming: List<TodoItem>): String {
        AgentRunContext.todosInitialized = true
        val existing = load(threadId)
        val merged = mergeTodos(existing, incoming)
        val normalized = normalizeStatuses(merged)
        save(threadId, normalized)
        return formatTodos(normalized)
    }

    private fun mergeTodos(existing: List<TodoItem>, incoming: List<TodoItem>): List<TodoItem> {
        if (existing.isEmpty() || incoming.size > 1) return incoming
        val single = incoming.first()
        val byId = existing.associateBy { it.id }.toMutableMap()
        if (single.id in byId) {
            byId[single.id] = single
            return existing.map { byId[it.id] ?: it }
        }
        return existing + single
    }

    suspend fun readTodos(threadId: String): String {
        if (!AgentRunContext.todosInitialized) {
            return "Error: call todo_write before todo_read"
        }
        val items = load(threadId)
        return if (items.isEmpty()) {
            "No todos yet. Use todo_write to create them."
        } else {
            formatTodos(items)
        }
    }

    private fun normalizeStatuses(items: List<TodoItem>): List<TodoItem> {
        if (items.isEmpty()) return items
        val inProgressCount = items.count { it.status == TodoStatus.IN_PROGRESS }
        if (inProgressCount <= 1) return items
        var kept = false
        return items.map { item ->
            if (item.status == TodoStatus.IN_PROGRESS) {
                if (!kept) {
                    kept = true
                    item
                } else {
                    item.copy(status = TodoStatus.PENDING)
                }
            } else {
                item
            }
        }
    }

    private fun formatTodos(items: List<TodoItem>): String = buildString {
        appendLine("Todos (${items.size}):")
        items.forEach { item ->
            val mark = when (item.status) {
                TodoStatus.PENDING -> "[ ]"
                TodoStatus.IN_PROGRESS -> "[→]"
                TodoStatus.COMPLETED -> "[x]"
            }
            appendLine("$mark ${item.id}: ${item.content} (${item.status.name.lowercase()})")
        }
    }.trim()

    private fun fileFor(threadId: String) = File(dir, "$threadId.json")

    companion object {
        fun parseTodosFromJson(args: org.json.JSONObject): List<TodoItem> {
            val arr = args.optJSONArray("todos") ?: run {
                val raw = args.optString("todos")
                if (raw.startsWith("[")) runCatching { JSONArray(raw) }.getOrNull() else null
            }
            if (arr != null && arr.length() > 0) {
                return (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    TodoItem(
                        id = obj.optString("id").ifBlank { obj.optString("todo_id").ifBlank { "${i + 1}" } },
                        content = obj.optString("content").ifBlank { obj.optString("description", "Task ${i + 1}") },
                        status = TodoStatus.fromString(obj.optString("status", "pending")),
                        priority = obj.optString("priority", "medium"),
                    )
                }
            }
            if (args.has("description") || args.has("content") || args.has("todo_id") || args.has("id")) {
                return listOf(
                    TodoItem(
                        id = args.optString("id").ifBlank { args.optString("todo_id", "1") },
                        content = args.optString("content").ifBlank { args.optString("description", "Task 1") },
                        status = TodoStatus.fromString(args.optString("status", "pending")),
                        priority = args.optString("priority", "medium"),
                    ),
                )
            }
            return emptyList()
        }
    }
}
