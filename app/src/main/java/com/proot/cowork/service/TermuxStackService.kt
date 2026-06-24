package com.proot.cowork.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.proot.cowork.R
import com.proot.cowork.domain.desktop.TermuxStackSession
import com.proot.cowork.termux.bootstrap.TermuxBootstrap
import com.proot.cowork.termux.bootstrap.TermuxX11Demo
import com.proot.cowork.termux.x11.X11DisplayConfig
import com.termux.x11.X11EmbedController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Extracts Termux bootstrap and starts the in-process X11 server.
 * UI layers ([com.proot.cowork.ui.desktop.EmbeddedX11Surface] / terminal) connect separately.
 */
class TermuxStackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("Starting Termux stack…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        scope.launch {
            bootStack()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun bootStack() {
        try {
            TermuxStackSession.clearLogs()
            TermuxStackSession.appendLog("Extracting Termux bootstrap…")
            val bootstrapOk = withContext(Dispatchers.IO) {
                TermuxBootstrap.ensureInstalled(applicationContext)
            }
            if (!bootstrapOk) {
                TermuxStackSession.appendLog("Bootstrap extraction failed")
                stopSelf()
                return
            }
            TermuxStackSession.appendLog("Bootstrap ready")
            TermuxStackSession.setBootstrapReady(true)

            val width = X11DisplayConfig.WIDTH
            val height = X11DisplayConfig.HEIGHT

            TermuxStackSession.appendLog("Starting X11 server ${X11DisplayConfig.DISPLAY} (${width}x${height}@${X11DisplayConfig.FPS})…")
            scope.launch(Dispatchers.IO) {
                try {
                    val x11Ok = X11EmbedController.ensureServer(applicationContext, width, height)
                    if (x11Ok) {
                        TermuxX11Demo.paintBackground(applicationContext)
                        TermuxStackSession.setX11Ready(true)
                        TermuxStackSession.appendLog("X11 server ready (black until a GUI app draws)")
                        updateNotification("Termux + X11 running")
                    } else {
                        TermuxStackSession.appendLog("X11 server failed (terminal still works)")
                        updateNotification("Termux running")
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "x11 boot failed", e)
                    TermuxStackSession.appendLog("X11 error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "boot failed", e)
            TermuxStackSession.appendLog("Error: ${e.message}")
            stopSelf()
        }
    }

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Termux stack", NotificationManager.IMPORTANCE_LOW),
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "TermuxStackService"
        const val ACTION_STOP = "com.proot.cowork.action.STOP_TERMUX_STACK"
        private const val CHANNEL_ID = "termux_stack"
        private const val NOTIFICATION_ID = 42
    }
}
