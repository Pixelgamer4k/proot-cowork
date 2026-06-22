package com.proot.cowork.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.proot.cowork.MainActivity
import com.proot.cowork.ProotCoworkApp
import com.proot.cowork.R
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.data.proot.ProotCommandBuilder
import com.proot.cowork.data.proot.RuntimeBootstrap
import com.proot.cowork.data.rootfs.RootfsValidator
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.domain.proot.DesktopState
import com.termux.x11.X11ServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class ProotDesktopService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prootProcess: Process? = null
    private var logJob: Job? = null
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        settingsRepository = (application as ProotCoworkApp).settingsRepository
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must promote within ~5s of startForegroundService(); do this before any validation/work.
        promoteToForeground("Starting Linux desktop…")

        when (intent?.action) {
            ACTION_STOP -> stopDesktop()
            ACTION_REBOOT -> {
                stopDesktopInternal()
                startDesktop()
            }
            else -> startDesktop()
        }
        return START_STICKY
    }

    private fun startDesktop() {
        val rootfs = settingsRepository.getRootfsDir()
        if (!RootfsValidator.isValid(rootfs)) {
            DesktopSession.setState(DesktopState.NO_ROOTFS)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        RootfsValidator.repairLayout(rootfs)

        DesktopSession.setState(DesktopState.STARTING)
        DesktopSession.clearLogs()
        updateNotification("Starting Linux desktop…")

        scope.launch {
            try {
                if (!X11ServerManager.ensureStarted(applicationContext, rootfs)) {
                    DesktopSession.appendLog("Failed to start embedded X11 server")
                    DesktopSession.setState(DesktopState.STOPPED)
                    return@launch
                }
                val runtime = RuntimeBootstrap(applicationContext).ensureRuntime()
                val command = ProotCommandBuilder.buildStartDesktop(
                    context = applicationContext,
                    runtime = runtime,
                    rootfsDir = rootfs,
                )
                DesktopSession.appendLog("Starting proot: ${command.joinToString(" ")}")

                val env = hashMapOf<String, String>()
                env.putAll(System.getenv().filterValues { it != null }.mapValues { it.value!! })
                env["LD_LIBRARY_PATH"] = runtime.ldLibraryPath

                val process = ProcessBuilder(command)
                    .directory(rootfs)
                    .redirectErrorStream(true)
                    .apply { environment().putAll(env) }
                    .start()

                prootProcess = process
                logJob = launch { streamLogs(process) }

                DesktopSession.setState(DesktopState.RUNNING)
                updateNotification("Linux desktop running")

                val exit = process.waitFor()
                DesktopSession.appendLog("proot exited with code $exit")
                DesktopSession.setState(DesktopState.STOPPED)
            } catch (e: Exception) {
                DesktopSession.appendLog("Error: ${e.message}")
                DesktopSession.setState(DesktopState.STOPPED)
            } finally {
                if (prootProcess?.isAlive != true) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private suspend fun streamLogs(process: Process) {
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                DesktopSession.appendLog(line)
                line = reader.readLine()
            }
        }
    }

    private fun stopDesktop() {
        stopDesktopInternal()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopDesktopInternal() {
        logJob?.cancel()
        prootProcess?.destroy()
        prootProcess = null
        X11ServerManager.stop()
        DesktopSession.setState(DesktopState.STOPPED)
    }

    private fun promoteToForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, ProotCoworkApp.CHANNEL_DESKTOP)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopDesktopInternal()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.proot.cowork.action.STOP_DESKTOP"
        const val ACTION_REBOOT = "com.proot.cowork.action.REBOOT_DESKTOP"
        private const val NOTIFICATION_ID = 1001
    }
}
