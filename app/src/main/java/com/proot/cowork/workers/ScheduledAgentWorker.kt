package com.proot.cowork.workers

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.proot.cowork.ProotCoworkApp
import com.proot.cowork.R
import com.proot.cowork.data.llm.LlmEndpoint
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.data.schedule.ScheduleRepository
import com.proot.cowork.service.AgentExecutionService
import com.proot.cowork.termux.bootstrap.TermuxBootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

        setForeground(buildForegroundInfo(applicationContext))

        val bootstrapOk = withContext(Dispatchers.IO) {
            TermuxBootstrap.ensureInstalled(applicationContext)
        }
        if (!bootstrapOk) {
            repo.markFailed(taskId, "Termux bootstrap not ready")
            return Result.failure()
        }

        repo.markRunning(taskId)
        return try {
            AgentExecutionService.startFastScheduled(applicationContext, prompt, taskId)
            Result.success()
        } catch (e: Exception) {
            repo.markFailed(taskId, e.message ?: "Failed to start scheduled agent")
            Result.failure()
        }
    }

    private fun buildForegroundInfo(context: Context): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, ProotCoworkApp.CHANNEL_AGENT)
            .setContentTitle(context.getString(R.string.agent_notification_title))
            .setContentText(context.getString(R.string.schedule_running))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_TASK_ID = "schedule_task_id"
        const val KEY_PROMPT = "schedule_prompt"
        private const val NOTIFICATION_ID = 78
    }
}
