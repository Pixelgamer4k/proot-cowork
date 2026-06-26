package com.proot.cowork.data.schedule

import android.content.Context
import com.proot.cowork.domain.schedule.ScheduleStatus
import com.proot.cowork.domain.schedule.ScheduledTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ScheduleRepository(context: Context) {

    private val appContext = context.applicationContext
    private val storeFile: File get() = File(appContext.filesDir, "schedules/schedules.json")
    private val _tasks = MutableStateFlow<List<ScheduledTask>>(emptyList())
    val tasks: StateFlow<List<ScheduledTask>> = _tasks.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        _tasks.value = readAll()
    }

    suspend fun create(prompt: String, triggerAtMillis: Long): ScheduledTask = withContext(Dispatchers.IO) {
        val task = ScheduledTask(
            id = UUID.randomUUID().toString(),
            prompt = prompt.trim(),
            triggerAtMillis = triggerAtMillis,
        )
        val updated = readAll() + task
        writeAll(updated)
        _tasks.value = updated.sortedBy { it.triggerAtMillis }
        task
    }

    suspend fun getById(id: String): ScheduledTask? = withContext(Dispatchers.IO) {
        readAll().firstOrNull { it.id == id }
    }

    suspend fun update(task: ScheduledTask) = withContext(Dispatchers.IO) {
        val updated = readAll().map { if (it.id == task.id) task else it }
        writeAll(updated)
        _tasks.value = updated.sortedBy { it.triggerAtMillis }
    }

    suspend fun markRunning(id: String) {
        val task = getById(id) ?: return
        update(
            task.copy(
                status = ScheduleStatus.RUNNING,
                lastRunAtMillis = System.currentTimeMillis(),
                lastError = null,
            ),
        )
    }

    suspend fun markDone(id: String) {
        val task = getById(id) ?: return
        update(
            task.copy(
                status = ScheduleStatus.DONE,
                lastRunAtMillis = System.currentTimeMillis(),
                lastError = null,
            ),
        )
    }

    suspend fun markFailed(id: String, error: String) {
        val task = getById(id) ?: return
        update(
            task.copy(
                status = ScheduleStatus.FAILED,
                lastRunAtMillis = System.currentTimeMillis(),
                lastError = error,
            ),
        )
    }

    suspend fun cancel(id: String) = withContext(Dispatchers.IO) {
        val task = getById(id) ?: return@withContext
        if (task.status == ScheduleStatus.DONE) return@withContext
        update(task.copy(status = ScheduleStatus.CANCELLED))
        ScheduleWorkScheduler.cancel(appContext, id)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        ScheduleWorkScheduler.cancel(appContext, id)
        val updated = readAll().filterNot { it.id == id }
        writeAll(updated)
        _tasks.value = updated.sortedBy { it.triggerAtMillis }
    }

    suspend fun reschedulePending() = withContext(Dispatchers.IO) {
        val all = readAll()
        _tasks.value = all.sortedBy { it.triggerAtMillis }
        all.filter { it.status == ScheduleStatus.PENDING }.forEach { task ->
            ScheduleWorkScheduler.enqueue(appContext, task)
        }
    }

    private fun readAll(): List<ScheduledTask> {
        storeFile.parentFile?.mkdirs()
        if (!storeFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(storeFile.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ScheduledTask(
                    id = o.getString("id"),
                    prompt = o.getString("prompt"),
                    triggerAtMillis = o.getLong("triggerAtMillis"),
                    createdAtMillis = o.optLong("createdAtMillis", o.getLong("triggerAtMillis")),
                    status = ScheduleStatus.valueOf(o.optString("status", ScheduleStatus.PENDING.name)),
                    lastRunAtMillis = o.optLong("lastRunAtMillis").takeIf { o.has("lastRunAtMillis") && !o.isNull("lastRunAtMillis") },
                    lastError = o.optString("lastError").takeIf { it.isNotBlank() },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAll(tasks: List<ScheduledTask>) {
        storeFile.parentFile?.mkdirs()
        val arr = JSONArray()
        tasks.forEach { task ->
            arr.put(
                JSONObject()
                    .put("id", task.id)
                    .put("prompt", task.prompt)
                    .put("triggerAtMillis", task.triggerAtMillis)
                    .put("createdAtMillis", task.createdAtMillis)
                    .put("status", task.status.name)
                    .put("lastRunAtMillis", task.lastRunAtMillis)
                    .put("lastError", task.lastError),
            )
        }
        storeFile.writeText(arr.toString(2))
    }
}
