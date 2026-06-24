package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.util.Log
import java.io.File

/** Paints the embedded X11 display so the layer is not black before the user runs a client. */
object TermuxX11Demo {

    private const val TAG = "TermuxX11Demo"

    fun paintBackground(context: Context) {
        val prefix = TermuxBootstrap.prefixDir(context)
        val xsetroot = File(prefix, "bin/xsetroot")
        if (!xsetroot.canExecute()) {
            Log.w(TAG, "xsetroot missing under ${prefix.absolutePath}/bin")
            return
        }

        val env = TermuxShellEnvironment.buildProcessEnvironment(context).toMutableMap()
        env["DISPLAY"] = ":0"

        try {
            ProcessBuilder(xsetroot.absolutePath, "-solid", "#2d2d30")
                .directory(TermuxBootstrap.homeDir(context))
                .apply {
                    environment().clear()
                    environment().putAll(env)
                }
                .start()
            Log.i(TAG, "xsetroot background applied on :0")
        } catch (e: Exception) {
            Log.w(TAG, "xsetroot failed: ${e.message}")
        }
    }
}
