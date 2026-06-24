package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.util.Log
import java.io.File

object TermuxBootstrapRunner {

    private const val TAG = "TermuxBootstrapRunner"

    fun runSecondStageIfNeeded(context: Context): Boolean {
        val prefix = TermuxBootstrap.prefixDir(context)
        val marker = File(prefix, ".termux_second_stage_v1")
        if (marker.isFile) return true

        val script = File(
            prefix,
            "etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh",
        )
        if (!script.isFile) {
            marker.createNewFile()
            return true
        }

        val bash = TermuxBootstrap.shellExecutable(context) ?: return false
        Log.i(TAG, "Running Termux bootstrap second stage")

        val env = TermuxShellEnvironment.build(context).associate {
            val eq = it.indexOf('=')
            it.substring(0, eq) to it.substring(eq + 1)
        }.toMutableMap()

        val pb = ProcessBuilder(bash.absolutePath, script.absolutePath)
        pb.directory(TermuxBootstrap.homeDir(context))
        pb.environment().clear()
        pb.environment().putAll(env)

        return try {
            val code = pb.start().waitFor()
            if (code != 0) {
                Log.e(TAG, "bootstrap second stage exited with $code")
                false
            } else {
                marker.createNewFile()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "bootstrap second stage failed", e)
            false
        }
    }
}
