package com.proot.cowork.data.proot

import android.content.Context
import android.os.Build
import java.io.File

object ProotCommandBuilder {

    fun guestEnvironment(
        context: Context,
        runtime: ProotRuntime,
    ): Map<String, String> = buildMap {
        put("HOME", "/home/cowork")
        put("USER", "cowork")
        put("LANG", "C.UTF-8")
        put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        put("TERM", "xterm-256color")
        put("TMPDIR", "/tmp")
        put("VNC_PORT", "5900")
        put("LD_LIBRARY_PATH", runtime.ldLibraryPath)
        put("PROOT_TMP_DIR", runtime.tmpDir.absolutePath)
        put("PROOT_LOADER", runtime.loaderPath.absolutePath)
        put("PROOT_LOADER_32", runtime.loader32Path.absolutePath)
        put("PROOT_NO_SECCOMP", "1")
    }

    fun buildStartDesktop(
        context: Context,
        runtime: ProotRuntime,
        rootfsDir: File,
    ): List<String> {
        val tmp = runtime.tmpDir
        tmp.mkdirs()

        val sysdataDir = File(context.filesDir, "sysdata")
        ProotSysdata.ensure(sysdataDir)

        val bindings = buildAndroidBindings(context, runtime, rootfsDir, tmp, sysdataDir)

        val prootArgs = mutableListOf<String>()
        appendProotExtensions(prootArgs)
        prootArgs += "-i"
        prootArgs += "0:0"
        prootArgs += "-r"
        prootArgs += rootfsDir.absolutePath
        prootArgs += "--cwd=/"
        bindings.forEach { bind ->
            prootArgs += "-b"
            prootArgs += bind
        }
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
        val sysdataDir = File(tmp.parentFile, "sysdata")
        ProotSysdata.ensure(sysdataDir)

        val bindings = buildAndroidBindings(
            context = null,
            runtime = runtime,
            rootfsDir = rootfsDir,
            tmpDir = tmp,
            sysdataDir = sysdataDir,
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

    private fun appendProotExtensions(args: MutableList<String>) {
        args += "--kill-on-exit"
        args += "--link2symlink"
        args += "--sysvipc"
        args += "-L"
        args += "--kernel-release"
        args += ProotSysdata.kernelReleaseArg(
            hostname = Build.MODEL.ifBlank { "cowork" },
            machine = Build.SUPPORTED_ABIS.firstOrNull()?.replace("-", "_") ?: "aarch64",
        )
    }

    private fun buildAndroidBindings(
        context: Context?,
        runtime: ProotRuntime,
        rootfsDir: File,
        tmpDir: File,
        sysdataDir: File,
    ): List<String> {
        val bindings = mutableListOf(
            "/dev",
            "/proc",
            "/sys",
            "/dev/urandom:/dev/random",
            "/proc/self/fd:/dev/fd",
            "${tmpDir.absolutePath}:/tmp",
            "${tmpDir.absolutePath}:/dev/shm",
            "${File(sysdataDir, "sys_empty").absolutePath}:/sys/fs/selinux",
        )

        for (fd in 0..2) {
            val hostFd = "/proc/self/fd/$fd"
            if (File(hostFd).exists()) {
                bindings += "$hostFd:/dev/${arrayOf("stdin", "stdout", "stderr")[fd]}"
            }
        }

        if (File("/apex").isDirectory) {
            bindings += "/apex:/apex"
        }

        ProotSysdata.fakeProcBindArgs(sysdataDir).forEach { bindings += it }

        context?.let {
            val artifacts = File(it.filesDir, "artifacts")
            artifacts.mkdirs()
            bindings += "${artifacts.absolutePath}:/artifacts"

            it.getExternalFilesDir(null)?.let { ext ->
                bindings += "${ext.absolutePath}:/storage"
            }
        }

        return bindings
    }
}
