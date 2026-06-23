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
import com.proot.cowork.data.rootfs.RootfsValidator
import com.proot.cowork.data.vnc.VncConfig
import com.proot.cowork.data.vnc.VncPortProbe
import com.proot.cowork.debug.DebugStatusWriter
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.domain.vnc.VncSession
import com.proot.cowork.userland.BusyboxExecutor
import com.proot.cowork.userland.CoworkSession
import com.proot.cowork.userland.LocalServerManager
import com.proot.cowork.userland.ProotDebugLogger
import com.proot.cowork.userland.UserlandConfig
import com.proot.cowork.userland.UserlandFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProotDesktopService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var desktopJob: Job? = null
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var userlandFiles: UserlandFiles
    private lateinit var serverManager: LocalServerManager
    private var session: CoworkSession? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = (application as ProotCoworkApp).settingsRepository
        userlandFiles = UserlandFiles(this, applicationInfo.nativeLibraryDir)
        val executor = BusyboxExecutor(
            userlandFiles,
            ProotDebugLogger(getSharedPreferences("userland", MODE_PRIVATE), userlandFiles),
        )
        serverManager = LocalServerManager(filesDir.path, executor)
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
        if (desktopJob?.isActive == true) return

        val rootfs = settingsRepository.getRootfsDir()
        if (!RootfsValidator.isValid(rootfs)) {
            DesktopSession.setState(DesktopState.NO_ROOTFS)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        RootfsValidator.repairLayout(rootfs)

        if (!RootfsValidator.hasVncStack(rootfs)) {
            DesktopSession.appendLog(
                "Rootfs missing tightvncserver. Install: apt install -y tightvncserver expect xfce4",
            )
            DesktopSession.setState(DesktopState.STOPPED)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        if (!RootfsValidator.hasXfceStack(rootfs)) {
            DesktopSession.appendLog("Rootfs missing startxfce4. Install: apt install -y xfce4")
            DesktopSession.setState(DesktopState.STOPPED)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        if (!userlandFiles.busybox.isFile || !userlandFiles.proot.isFile) {
            DesktopSession.appendLog(
                "UserLAnd runtime missing. Reinstall APK built with fetch-userland-runtime.sh",
            )
            DesktopSession.setState(DesktopState.STOPPED)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        DesktopSession.setState(DesktopState.STARTING)
        DesktopSession.clearLogs()
        DebugStatusWriter.clearProotLog(applicationContext)
        updateNotification("Starting UserLAnd VNC desktop…")

        desktopJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    RootfsValidator.prepareUserlandGuest(applicationContext, rootfs)
                }

                val coworkSession = CoworkSession()
                session = coworkSession

                DesktopSession.appendLog("Starting UserLAnd backend (proot + tightvnc :${UserlandConfig.VNC_DISPLAY})")

                val pid = serverManager.startServer(coworkSession) { line ->
                    DesktopSession.appendLog(line)
                    DebugStatusWriter.appendProotLog(applicationContext, line)
                }
                if (pid <= 0) {
                    DesktopSession.appendLog("Failed to start VNC server (UserLAnd backend)")
                    DesktopSession.setState(DesktopState.STOPPED)
                    return@launch
                }
                coworkSession.pid = pid
                DesktopSession.appendLog("proot session pid=$pid")

                updateNotification("Waiting for VNC on :${UserlandConfig.VNC_PORT}…")
                val deadline = System.currentTimeMillis() + VncConfig.BOOT_TIMEOUT_MS
                var running = false
                while (isActive && System.currentTimeMillis() < deadline) {
                    if (serverManager.isServerRunning(coworkSession) && VncPortProbe.isOpen()) {
                        running = true
                        break
                    }
                    delay(VncConfig.POLL_INTERVAL_MS)
                }

                if (!running) {
                    DesktopSession.appendLog(
                        "Timed out waiting for VNC on ${VncConfig.HOST}:${VncConfig.PORT}",
                    )
                    DesktopSession.setState(DesktopState.STOPPED)
                    DebugStatusWriter.refresh(applicationContext)
                    stopDesktopInternal()
                    return@launch
                }

                DesktopSession.appendLog("VNC ready on port ${VncConfig.PORT}")
                DesktopSession.setState(DesktopState.RUNNING)
                DebugStatusWriter.refresh(applicationContext)
                updateNotification("Linux desktop running (UserLAnd VNC)")

                while (isActive && serverManager.isServerRunning(coworkSession)) {
                    delay(2000)
                }

                DesktopSession.appendLog("VNC session ended")
                DesktopSession.setState(DesktopState.STOPPED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DesktopSession.appendLog("Error: ${e.message}")
                DesktopSession.setState(DesktopState.STOPPED)
                DebugStatusWriter.refresh(applicationContext)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopDesktop() {
        stopDesktopInternal()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopDesktopInternal() {
        desktopJob?.cancel()
        desktopJob = null
        session?.let { serverManager.stopService(it) }
        session = null
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
