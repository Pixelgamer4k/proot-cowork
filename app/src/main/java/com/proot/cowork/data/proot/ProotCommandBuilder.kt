package com.proot.cowork.data.proot

import android.content.Context
import java.io.File

object ProotCommandBuilder {

    fun launchEnvironment(
        context: Context,
        runtime: ProotRuntime,
    ): Map<String, String> = buildMap {
        put("LD_LIBRARY_PATH", runtime.ldLibraryPath)
        put("PROOT_TMP_DIR", runtime.tmpDir.absolutePath)
        put("PROOT_LOADER", runtime.loaderPath.absolutePath)
        put("PROOT_LOADER_32", runtime.loader32Path.absolutePath)
        put("PROOT_NO_SECCOMP", "1")

        put("HOME", "/home/cowork")
        put("USER", "cowork")
        put("LANG", "C.UTF-8")
        put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        put("TERM", "xterm-256color")
        put("TMPDIR", "/tmp")
        put("DISPLAY", ":99")
        put("VNC_PORT", "5900")
    }

    fun buildStartDesktop(
        context: Context,
        runtime: ProotRuntime,
        rootfsDir: File,
    ): List<String> {
        val tmp = runtime.tmpDir
        ensureSharedTmp(tmp)

        val bindings = buildAndroidBindings(context, runtime, rootfsDir, tmp)

        val prootArgs = mutableListOf<String>()
        appendProotExtensions(prootArgs)
        prootArgs += "-i"
        prootArgs += "0:0"
        prootArgs += "-r"
        prootArgs += rootfsDir.absolutePath
        prootArgs += "--cwd=/root"
        bindings.forEach { bind ->
            prootArgs += "-b"
            prootArgs += bind
        }
        prootArgs += "/usr/bin/bash"
        prootArgs += "/start-desktop.sh"
        return runtime.launchCommand(prootArgs)
    }

    fun buildShell(
        context: Context,
        runtime: ProotRuntime,
        rootfsDir: File,
        command: String,
    ): List<String> {
        val tmp = runtime.tmpDir
        ensureSharedTmp(tmp)

        val bindings = buildAndroidBindings(
            context = context,
            runtime = runtime,
            rootfsDir = rootfsDir,
            tmpDir = tmp,
        )

        val prootArgs = mutableListOf<String>()
        appendProotExtensions(prootArgs)
        prootArgs += "-i"
        prootArgs += "0:0"
        prootArgs += "-r"
        prootArgs += rootfsDir.absolutePath
        prootArgs += "--cwd=/root"
        bindings.forEach { bind ->
            prootArgs += "-b"
            prootArgs += bind
        }
        prootArgs += "/usr/bin/bash"
        prootArgs += "-lc"
        prootArgs += command
        return runtime.launchCommand(prootArgs)
    }

    fun ensureSharedTmp(tmpDir: File) {
        tmpDir.mkdirs()
        File(tmpDir, ".X11-unix").mkdirs()
    }

    private fun appendProotExtensions(args: MutableList<String>) {
        args += "--kill-on-exit"
        args += "--link2symlink"
        args += "--sysvipc"
    }

    private fun buildAndroidBindings(
        context: Context?,
        runtime: ProotRuntime,
        rootfsDir: File,
        tmpDir: File,
    ): List<String> {
        val bindings = mutableListOf(
            "/dev",
            "/proc",
            "/sys",
            "/dev/urandom:/dev/random",
            "${tmpDir.absolutePath}:/tmp",
            "${tmpDir.absolutePath}:/dev/shm",
        )

        context?.let { ctx ->
            val nativeLibDir = File(ctx.applicationInfo.nativeLibraryDir)
            val guestLibs = listOf(
                "libcowork_linkshim.so",
                "libandroid-shmem.so",
            )
            guestLibs.forEach { name ->
                val lib = File(nativeLibDir, name)
                if (lib.isFile) {
                    bindings += "${lib.absolutePath}:/usr/lib/$name"
                }
            }
        }

        if (File("/apex").isDirectory) {
            bindings += "/apex:/apex"
        }

        context?.let {
            it.getExternalFilesDir(null)?.let { ext ->
                bindings += "${ext.absolutePath}:/storage"
            }
        }

        return bindings
    }
}
