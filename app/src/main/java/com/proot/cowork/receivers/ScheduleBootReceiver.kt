package com.proot.cowork.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.proot.cowork.data.schedule.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Re-enqueue pending scheduled agent runs after device reboot. */
class ScheduleBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        scope.launch {
            try {
                ScheduleRepository(context.applicationContext).reschedulePending()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
