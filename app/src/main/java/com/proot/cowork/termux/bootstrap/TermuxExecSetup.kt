package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.util.Log
import java.io.File

/** Runs termux-exec setup so ELFs under the prefix can execute on Android 10+. */
object TermuxExecSetup {

    private const val TAG = "TermuxExecSetup"

    fun ldPreloadPath(prefix: File): String? {
        val modern = File(prefix, "lib/libtermux-exec-ld-preload.so")
        if (modern.isFile) return modern.absolutePath
        val legacy = File(prefix, "lib/libtermux-exec.so")
        if (legacy.isFile) return legacy.absolutePath
        return null
    }

    fun applyIfNeeded(context: Context, prefix: File): Boolean {
        val marker = File(prefix, ".termux_exec_setup_v1")
        if (marker.isFile) return true

        val setup = File(prefix, "bin/termux-exec-ld-preload-lib")
        if (!setup.canExecute()) {
            Log.w(TAG, "termux-exec-ld-preload-lib missing; LD_PRELOAD may not work")
            marker.createNewFile()
            return true
        }

        val bash = TermuxBootstrap.shellExecutable(context) ?: return false
        val env = TermuxShellEnvironment.buildProcessEnvironment(context)

        val pb = ProcessBuilder(
            bash.absolutePath,
            "-c",
            "${setup.absolutePath} setup",
        )
        pb.directory(TermuxBootstrap.homeDir(context))
        pb.environment().clear()
        pb.environment().putAll(env)

        return try {
            val code = pb.start().waitFor()
            if (code != 0) {
                Log.w(TAG, "termux-exec setup exited with $code (continuing)")
            } else {
                Log.i(TAG, "termux-exec ld-preload setup complete")
            }
            marker.createNewFile()
            true
        } catch (e: Exception) {
            Log.e(TAG, "termux-exec setup failed", e)
            false
        }
    }
}
