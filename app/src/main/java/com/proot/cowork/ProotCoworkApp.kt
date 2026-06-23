package com.proot.cowork

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.data.rootfs.RootfsRepository
import com.proot.cowork.debug.DebugStatusWriter
import com.proot.cowork.userland.UserlandFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ProotCoworkApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var rootfsRepository: RootfsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        rootfsRepository = RootfsRepository(this, settingsRepository)
        DebugStatusWriter.init(this)
        createNotificationChannels()
        appScope.launch {
            UserlandFiles(this@ProotCoworkApp, applicationInfo.nativeLibraryDir)
            rootfsRepository.repairStateOnStartup()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DESKTOP,
                "Linux Desktop",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_AGENT,
                "Agent Tasks",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val CHANNEL_DESKTOP = "proot_desktop"
        const val CHANNEL_AGENT = "agent_execution"
    }
}
