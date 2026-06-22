package com.termux.x11

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
import com.proot.cowork.data.rootfs.RootfsValidator
import java.io.File

object X11ServerManager {

    private const val TAG = "X11ServerManager"
    private var serverThread: Thread? = null
    private var service: ICmdEntryInterface? = null
    private var receiverRegistered = false
    private var receiver: BroadcastReceiver? = null
    @Volatile
    private var startFailed = false

    @Synchronized
    fun ensureStarted(context: Context, rootfsDir: File? = null): Boolean {
        if (startFailed) return false
        if (serverThread?.isAlive == true) return service != null || !startFailed

        val appContext = context.applicationContext
        val rootfs = rootfsDir ?: File(appContext.filesDir, "rootfs")
        registerReceiver(appContext)
        startFailed = false

        try {
            System.loadLibrary("Xlorie")
        } catch (e: UnsatisfiedLinkError) {
            startFailed = true
            Log.e(TAG, "Failed to load libXlorie", e)
            return false
        }

        if (!configureEnvironment(appContext, rootfs)) {
            startFailed = true
            return false
        }

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

        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            if (startFailed) return false
            if (service != null) return true
            Thread.sleep(100)
        }
        return service != null && !startFailed
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

    private fun configureEnvironment(context: Context, rootfs: File): Boolean {
        try {
            Os.setenv("TERMUX_X11_OVERRIDE_PACKAGE", context.packageName, true)
            Os.setenv("TMPDIR", File(context.filesDir, "tmp").absolutePath, true)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set X11 environment", e)
        }
        System.setProperty("TERMUX_X11_OVERRIDE_PACKAGE", context.packageName)

        val xkbRoot = RootfsValidator.resolveXkbConfigRoot(rootfs)
        if (xkbRoot == null) {
            Log.e(TAG, "No xkb data found under ${rootfs.absolutePath}")
            return false
        }
        try {
            Os.setenv("XKB_CONFIG_ROOT", xkbRoot.absolutePath, true)
            Log.i(TAG, "XKB_CONFIG_ROOT=${xkbRoot.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Could not set XKB_CONFIG_ROOT", e)
            return false
        }
        return true
    }
}
