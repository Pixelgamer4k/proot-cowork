package com.proot.cowork.domain.schedule

enum class ScheduleStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    CANCELLED,
}

data class ScheduledTask(
    val id: String,
    val prompt: String,
    val triggerAtMillis: Long,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val status: ScheduleStatus = ScheduleStatus.PENDING,
    val lastRunAtMillis: Long? = null,
    val lastError: String? = null,
)
