package com.proot.cowork.data.proot

import android.content.Context
import java.io.File

object ProotCommandBuilder {

    fun guestEnvironment(
        context: Context,
        runtime: ProotRuntime,
    ): Map<String, String> = mapOf(
        "DISPLAY" to ":0",
        "XDG_RUNTIME_DIR" to "/tmp",
        "HOME" to "/home/cowork",
        "USER" to "cowork",
        "TMPDIR" to "/tmp",
        "LD_LIBRARY_PATH" to runtime.ldLibraryPath,
        "PROOT_TMP_DIR" to runtime.tmpDir.absolutePath,
    )

    fun buildStartDesktop(
        context: Context,
        runtime: ProotRuntime,
        rootfsDir: File,
    ): List<String> {
        val tmp = runtime.tmpDir
        File(tmp, ".X11-unix").mkdirs()

        val bindings = listOf(
            "/dev:/dev",
            "/proc:/proc",
            "/sys:/sys",
            "${tmp.absolutePath}:/tmp",
            "${context.filesDir.absolutePath}/artifacts:/artifacts",
            "${context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath}/storage:/storage",
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
        prootArgs += "/usr/bin/bash"
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
        prootArgs += "/usr/bin/bash"
        prootArgs += "-lc"
        prootArgs += command
        return runtime.launchCommand(prootArgs)
    }
}
