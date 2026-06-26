package com.proot.cowork.domain.agent

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide coordinator for agent run cancellation.
 * Stops LLM/tool loops via [isActive], kills in-flight shell [Process]es, and tracks per-subtask cancel.
 */
object AgentRunController {
    private val stopRequested = AtomicBoolean(false)
    private val cancelledSubtasks = ConcurrentHashMap.newKeySet<String>()
    private val activeProcesses = Collections.newSetFromMap(ConcurrentHashMap<Process, Boolean>())

    fun beginRun() {
        stopRequested.set(false)
        cancelledSubtasks.clear()
        destroyAllProcesses()
    }

    fun requestStop() {
        stopRequested.set(true)
        destroyAllProcesses()
    }

    fun requestSubtaskCancel(taskId: String) {
        cancelledSubtasks.add(taskId)
    }

    fun isActive(): Boolean = !stopRequested.get()

    fun isSubtaskCancelled(subtaskId: String?): Boolean {
        if (subtaskId == null) return !isActive()
        return stopRequested.get() || subtaskId in cancelledSubtasks
    }

    fun registerProcess(process: Process) {
        activeProcesses.add(process)
    }

    fun unregisterProcess(process: Process) {
        activeProcesses.remove(process)
    }

    private fun destroyAllProcesses() {
        activeProcesses.forEach { process ->
            runCatching {
                if (process.isAlive) process.destroyForcibly()
            }
        }
        activeProcesses.clear()
    }
}
