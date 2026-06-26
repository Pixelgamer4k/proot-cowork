package com.proot.cowork.data.schedule

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.proot.cowork.domain.schedule.ScheduledTask
import com.proot.cowork.workers.ScheduledAgentWorker
import java.util.concurrent.TimeUnit
import kotlin.math.max

object ScheduleWorkScheduler {

    fun enqueue(context: Context, task: ScheduledTask) {
        val delayMs = max(0L, task.triggerAtMillis - System.currentTimeMillis())
        val request = OneTimeWorkRequestBuilder<ScheduledAgentWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ScheduledAgentWorker.KEY_TASK_ID to task.id,
                    ScheduledAgentWorker.KEY_PROMPT to task.prompt,
                ),
            )
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(task.id, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, taskId: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(taskId)
    }

    private const val WORK_TAG = "cowork_scheduled_agent"
}
