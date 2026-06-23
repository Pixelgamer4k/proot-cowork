package com.proot.cowork.data.x11

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import com.termux.x11.CmdEntryPoint
import com.termux.x11.ICmdEntryInterface
import com.termux.x11.LorieView
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Embeds Termux:X11 in-process (Phoshdroid-style): start native X server, connect [LorieView].
 */
object X11ConnectionManager {
    private const val TAG = "X11Connection"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serverStarted = AtomicBoolean(false)
  private var service: ICmdEntryInterface? = null
    private var receiverRegistered = false

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != CmdEntryPoint.ACTION_START) return
            val bundle = intent.getBundleExtra(null) ?: return
            val binder = bundle.getBinder(null) ?: return
            onBinder(binder)
        }
    }

    fun ensureServer(context: Context) {
        val appContext = context.applicationContext
        System.setProperty("TERMUX_X11_OVERRIDE_PACKAGE", appContext.packageName)
        registerReceiver(appContext)

        if (!serverStarted.compareAndSet(false, true)) return

        Thread({
            try {
                Log.i(TAG, "Starting embedded Termux:X11 server")
                CmdEntryPoint.main(emptyArray())
            } catch (e: Throwable) {
                Log.e(TAG, "X11 server thread failed", e)
                serverStarted.set(false)
            }
        }, "termux-x11-server").start()
    }

    fun attachLorieView(context: Context, view: LorieView) {
        ensureServer(context)
        tryConnect(view)
    }

    fun detach(context: Context) {
        if (receiverRegistered) {
            runCatching { context.applicationContext.unregisterReceiver(connectionReceiver) }
            receiverRegistered = false
        }
    }

    fun reset() {
        service = null
        serverStarted.set(false)
        mainHandler.post { LorieView.connect(-1) }
    }

    private fun registerReceiver(context: Context) {
        if (receiverRegistered) return
        val filter = IntentFilter(CmdEntryPoint.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(connectionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(connectionReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun onBinder(binder: IBinder) {
        service = ICmdEntryInterface.Stub.asInterface(binder)
        mainHandler.post { pendingView?.let { tryConnect(it) } }
    }

    private var pendingView: LorieView? = null

    private fun tryConnect(view: LorieView) {
        pendingView = view
        if (LorieView.connected()) return

        val svc = service
        if (svc == null) {
            LorieView.requestConnection()
            mainHandler.postDelayed({ tryConnect(view) }, 250)
            return
        }

        try {
            val fd: ParcelFileDescriptor? = svc.getXConnection()
            if (fd != null) {
                Log.i(TAG, "LorieView connected to X11 socket")
                LorieView.connect(fd.detachFd())
                view.triggerCallback()
                pendingView = null
            } else {
                mainHandler.postDelayed({ tryConnect(view) }, 250)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect LorieView", e)
            service = null
            mainHandler.postDelayed({ tryConnect(view) }, 500)
        }
    }
}
