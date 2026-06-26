package com.proot.cowork.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.proot.cowork.data.llm.LlmEndpoint
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.data.schedule.ScheduleRepository
import com.proot.cowork.service.AgentExecutionService

class ScheduledAgentWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val prompt = inputData.getString(KEY_PROMPT) ?: return Result.failure()
        val repo = ScheduleRepository(applicationContext)
        val config = SettingsRepository(applicationContext).getLlmConfigSnapshot()

        if (!LlmEndpoint.isConfigured(config)) {
            repo.markFailed(taskId, "API not configured")
            return Result.failure()
        }

        repo.markRunning(taskId)
        AgentExecutionService.startFastScheduled(applicationContext, prompt, taskId)
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "schedule_task_id"
        const val KEY_PROMPT = "schedule_prompt"
    }
}
