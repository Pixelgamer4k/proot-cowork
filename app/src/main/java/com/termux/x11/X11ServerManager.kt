package com.termux.x11

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log

object X11ServerManager {

    private const val TAG = "X11ServerManager"
    private var serverThread: Thread? = null
    private var service: ICmdEntryInterface? = null
    private var receiverRegistered = false
    private var receiver: BroadcastReceiver? = null
    @Volatile
    private var startFailed = false

    @Synchronized
    fun ensureStarted(context: Context): Boolean {
        if (startFailed) return false
        if (serverThread?.isAlive == true) return service != null || !startFailed

        val appContext = context.applicationContext
        registerReceiver(appContext)
        startFailed = false

        serverThread = Thread({
            try {
                // CmdEntryPoint static init creates a Handler — needs a prepared Looper on this thread.
                Looper.prepare()
                CmdEntryPoint.ctx = appContext
                System.setProperty("TERMUX_X11_OVERRIDE_PACKAGE", appContext.packageName)
                CmdEntryPoint.main(arrayOf(":0"))
            } catch (e: Throwable) {
                startFailed = true
                Log.e(TAG, "X11 server thread failed", e)
            }
        }, "x11-server").apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
                startFailed = true
                Log.e(TAG, "Uncaught X11 server error", e)
            }
            start()
        }

        Thread.sleep(800)
        return !startFailed
    }

    @Synchronized
    fun stop() {
        service = null
        serverThread?.interrupt()
        serverThread = null
        startFailed = false
    }

    fun connectLorieView(lorieView: LorieView): Boolean {
        if (LorieView.connected()) return true
        val svc = service ?: return false
        return try {
            val fd: ParcelFileDescriptor? = svc.xConnection
            if (fd != null) {
                LorieView.connect(fd.detachFd())
                lorieView.triggerCallback()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect LorieView", e)
            false
        }
    }

    private fun registerReceiver(context: Context) {
        if (receiverRegistered) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != CmdEntryPoint.ACTION_START) return
                val bundle = intent.getBundleExtra(null) ?: return
                val binder: IBinder = bundle.getBinder(null) ?: return
                service = ICmdEntryInterface.Stub.asInterface(binder)
                Log.i(TAG, "X11 CmdEntryPoint connected")
            }
        }
        val filter = IntentFilter(CmdEntryPoint.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }
}
