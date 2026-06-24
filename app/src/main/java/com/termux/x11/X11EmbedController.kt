package com.termux.x11

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
import com.proot.cowork.termux.bootstrap.TermuxBootstrap
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Starts in-process Termux:X11 server and connects [LorieView] to the real X display.
 * The surface stays empty/black until a Termux app draws to DISPLAY=:0.
 */
object X11EmbedController {
    private const val TAG = "X11EmbedController"
    /** Avoid referencing [CmdEntryPoint] before its Looper/thread is ready. */
    private const val ACTION_START = "com.termux.x11.CmdEntryPoint.ACTION_START"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceRef = AtomicReference<ICmdEntryInterface?>()
    private var receiverRegistered = false
    private var serverThread: Thread? = null

    fun ensureServer(context: Context, widthPx: Int, heightPx: Int): Boolean {
        if (serverThread?.isAlive == true) return waitForSocket(context, 5_000)

        val appContext = context.applicationContext
        val prefix = TermuxBootstrap.prefixDir(appContext)
        if (!TermuxBootstrap.isInstalled(appContext)) {
            Log.w(TAG, "bootstrap not installed yet; deferring X11 server start")
            return false
        }
        val xkbRoot = resolveXkbRoot(prefix)
        if (xkbRoot == null) {
            Log.e(TAG, "XKB config missing under ${prefix.absolutePath}")
            return false
        }

        val tmp = File(prefix, "tmp")
        val x11Unix = File(tmp, ".X11-unix")
        x11Unix.mkdirs()
        listOf(
            File(x11Unix, "X0"),
            File(tmp, ".X0-lock"),
        ).forEach { stale -> if (stale.exists()) stale.delete() }

        try {
            Os.setenv("TMPDIR", tmp.absolutePath, true)
            Os.setenv("TERMUX_X11_OVERRIDE_PACKAGE", appContext.packageName, true)
            Os.setenv("PREFIX", prefix.absolutePath, true)
            Os.setenv("XKB_CONFIG_ROOT", xkbRoot.absolutePath, true)
            if (widthPx > 0 && heightPx > 0) {
                Os.setenv("XLORIE_WIDTH", widthPx.toString(), true)
                Os.setenv("XLORIE_HEIGHT", heightPx.toString(), true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "env setup failed", e)
            return false
        }

        registerStartReceiver(appContext)

        val started = CountDownLatch(1)
        serverThread = Thread({
            try {
                Looper.prepare()
                // CmdEntryPoint static init must run on a thread with a Looper.
                CmdEntryPoint.ctx = appContext
                val ctor = CmdEntryPoint::class.java.getDeclaredConstructor(Array<String>::class.java)
                ctor.isAccessible = true
                ctor.newInstance(arrayOf(":0"))
                started.countDown()
                Looper.loop()
            } catch (e: Throwable) {
                Log.e(TAG, "X11 server thread failed", e)
                started.countDown()
            }
        }, "CoworkX11Server").also { it.start() }

        started.await(15, TimeUnit.SECONDS)
        return waitForSocket(appContext, 20_000)
    }

    private fun waitForSocket(context: Context, timeoutMs: Long): Boolean {
        val socket = File(TermuxBootstrap.prefixDir(context), "tmp/.X11-unix/X0")
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (socket.exists()) return true
            Thread.sleep(200)
        }
        return socket.exists()
    }

    private fun registerStartReceiver(context: Context) {
        if (receiverRegistered) return
        val filter = IntentFilter(ACTION_START)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ACTION_START) return
                val bundle = intent.getBundleExtra(null) ?: return
                val binder = bundle.getBinder(null) ?: return
                serviceRef.set(ICmdEntryInterface.Stub.asInterface(binder))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    fun connectLorieView(lorieView: LorieView): Boolean {
        if (LorieView.connected()) {
            lorieView.triggerCallback()
            return true
        }
        val service = serviceRef.get()
        if (service == null) {
            LorieView.requestConnection()
            return false
        }
        return try {
            val fd: ParcelFileDescriptor = service.getXConnection() ?: return false
            LorieView.connect(fd.detachFd())
            lorieView.triggerCallback()
            LorieView.connected()
        } catch (e: Exception) {
            Log.e(TAG, "connect failed", e)
            false
        }
    }

    fun pollConnect(lorieView: LorieView, onConnected: () -> Unit) {
        mainHandler.post(object : Runnable {
            override fun run() {
                if (connectLorieView(lorieView)) {
                    onConnected()
                } else {
                    mainHandler.postDelayed(this, 250)
                }
            }
        })
    }

    private fun resolveXkbRoot(prefix: File): File? {
        val candidates = listOf(
            File(prefix, "share/xkeyboard-config-2"),
            File(prefix, "share/X11/xkb"),
        )
        return candidates.firstOrNull { dir ->
            dir.isDirectory && File(dir, "rules").isDirectory
        }
    }
}
