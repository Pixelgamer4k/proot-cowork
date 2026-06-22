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
import com.proot.cowork.data.proot.ProotProcessLauncher
import com.proot.cowork.data.proot.RuntimeBootstrap
import com.proot.cowork.data.rootfs.RootfsValidator
import com.proot.cowork.data.vnc.VncReadiness
import com.proot.cowork.debug.DebugStatusWriter
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.domain.vnc.VncSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
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
        RootfsValidator.ensureVncStartScript(applicationContext, rootfs)

        if (!RootfsValidator.hasVncStack(rootfs)) {
            DesktopSession.appendLog(
                "Rootfs missing xvfb/x11vnc. Rebuild with: apt install -y xvfb x11vnc xfce4",
            )
            DesktopSession.setState(DesktopState.STOPPED)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        DesktopSession.setState(DesktopState.STARTING)
        DesktopSession.clearLogs()
        DebugStatusWriter.clearProotLog(applicationContext)
        updateNotification("Starting Linux desktop…")

        scope.launch {
            val logLines = Channel<String>(capacity = Channel.UNLIMITED)
            try {
                val runtime = RuntimeBootstrap(applicationContext).ensureRuntime()
                val command = ProotCommandBuilder.buildStartDesktop(
                    context = applicationContext,
                    runtime = runtime,
                    rootfsDir = rootfs,
                )
                DesktopSession.appendLog("Starting proot (VNC desktop)")
                DebugStatusWriter.writeProotCommand(applicationContext, command)

                val env = ProotCommandBuilder.launchEnvironment(applicationContext, runtime)

                val process = ProotProcessLauncher.start(
                    context = applicationContext,
                    argv = command,
                    env = env,
                )

                prootProcess = process
                logJob = launch {
                    streamLogs(process, logLines)
                }

                updateNotification("Waiting for VNC…")
                val ready = VncReadiness.awaitReady(logLines = logLines)

                if (!ready) {
                    val tail = DesktopSession.logLines.value.takeLast(5).joinToString(" | ")
                    DesktopSession.appendLog("Timed out waiting for VNC on 127.0.0.1:5900")
                    if (tail.isNotBlank()) {
                        DesktopSession.appendLog("Last proot output: $tail")
                    }
                    DesktopSession.setState(DesktopState.STOPPED)
                    DebugStatusWriter.refresh(applicationContext)
                    process.destroy()
                    return@launch
                }

                DesktopSession.appendLog("VNC ready on port 5900")
                DesktopSession.setState(DesktopState.RUNNING)
                DebugStatusWriter.refresh(applicationContext)
                updateNotification("Linux desktop running (VNC)")

                val exit = process.waitFor()
                DesktopSession.appendLog("proot exited with code $exit")
                DebugStatusWriter.writeProotExit(applicationContext, exit)
                DesktopSession.setState(DesktopState.STOPPED)
            } catch (e: Exception) {
                DesktopSession.appendLog("Error: ${e.message}")
                DesktopSession.setState(DesktopState.STOPPED)
                DebugStatusWriter.refresh(applicationContext)
            } finally {
                logLines.close()
                if (prootProcess?.isAlive != true) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private suspend fun streamLogs(process: Process, logLines: kotlinx.coroutines.channels.Channel<String>) {
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                DesktopSession.appendLog(line)
                DebugStatusWriter.appendProotLog(applicationContext, line)
                logLines.trySend(line)
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
        VncSession.disconnect()
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
