package com.proot.cowork.data.proot

import android.content.Context
import android.os.Environment
import java.io.File

object ProotCommandBuilder {

    fun buildStartDesktop(
        context: Context,
        runtime: ProotRuntime,
        rootfsDir: File,
    ): List<String> {
        val tmp = runtime.tmpDir
        val x11Unix = File(tmp, ".X11-unix").also { it.mkdirs() }

        val bindings = listOf(
            "/dev:/dev",
            "/proc:/proc",
            "/sys:/sys",
            "${tmp.absolutePath}:/tmp",
            "${context.filesDir.absolutePath}/artifacts:/artifacts",
            "${context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath}/storage:/storage",
        )

        val env = mapOf(
            "DISPLAY" to ":0",
            "XDG_RUNTIME_DIR" to "/tmp",
            "LD_LIBRARY_PATH" to runtime.libraryPath.absolutePath,
            "HOME" to "/home/cowork",
            "USER" to "cowork",
            "TMPDIR" to "/tmp",
        )

        val prootArgs = mutableListOf<String>()
        prootArgs += "-r"
        prootArgs += rootfsDir.absolutePath
        bindings.forEach { bind ->
            prootArgs += "-b"
            prootArgs += bind
        }
        prootArgs += "-w"
        prootArgs += "/"
        env.forEach { (k, v) ->
            prootArgs += "-0"
            prootArgs += "$k=$v"
        }
        prootArgs += "/start-desktop.sh"
        return runtime.launchCommand(prootArgs)
    }

    fun buildShell(
        runtime: ProotRuntime,
        rootfsDir: File,
        command: String,
    ): List<String> {
        val tmp = runtime.tmpDir
        val bindings = listOf(
            "/dev:/dev",
            "/proc:/proc",
            "/sys:/sys",
            "${tmp.absolutePath}:/tmp",
        )
        val prootArgs = mutableListOf<String>()
        prootArgs += "-r"
        prootArgs += rootfsDir.absolutePath
        bindings.forEach { bind ->
            prootArgs += "-b"
            prootArgs += bind
        }
        prootArgs += "-w"
        prootArgs += "/root"
        prootArgs += "-0"
        prootArgs += "LD_LIBRARY_PATH=${runtime.libraryPath.absolutePath}"
        prootArgs += "-0"
        prootArgs += "HOME=/home/cowork"
        prootArgs += "/bin/bash"
        prootArgs += "-lc"
        prootArgs += command
        return runtime.launchCommand(prootArgs)
    }
}
