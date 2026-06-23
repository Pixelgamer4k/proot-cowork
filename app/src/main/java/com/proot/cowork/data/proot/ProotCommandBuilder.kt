package com.proot.cowork.data.proot

import android.content.Context
import android.os.Build
import java.io.File

object ProotCommandBuilder {

    /**
     * Environment for the host-side proot process. Uses a shell launcher script so guest
     * paths (HOME, PATH, …) do not leak into the Android process before proot starts.
     */
    fun launchEnvironment(
        context: Context,
        runtime: ProotRuntime,
    ): Map<String, String> = buildMap {
        val dataDir = context.applicationInfo.dataDir
        val legacyDataDir = "/data/data/${context.packageName}"

        put("LD_LIBRARY_PATH", runtime.ldLibraryPath)
        put("PROOT_TMP_DIR", runtime.tmpDir.absolutePath)
        put("PROOT_LOADER", runtime.loaderPath.absolutePath)
        put("PROOT_LOADER_32", runtime.loader32Path.absolutePath)
        put("PROOT_NO_SECCOMP", "1")

        // Termux-compatible hints for proot's Android guest exec path.
        put("TERMUX_APP__DATA_DIR", dataDir)
        put("TERMUX_APP__LEGACY_DATA_DIR", legacyDataDir)
        put("TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE", "enable")

        // Guest paths forwarded through proot into the rootfs.
        put("HOME", "/home/cowork")
        put("USER", "cowork")
        put("LANG", "C.UTF-8")
        put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        put("TERM", "xterm-256color")
        put("TMPDIR", "/tmp")
        put("XDG_RUNTIME_DIR", "/tmp")
        put("VNC_PORT", "5900")
    }

    fun buildStartDesktop(
        context: Context,
        runtime: ProotRuntime,
        rootfsDir: File,
    ): List<String> {
        val tmp = runtime.tmpDir
        ensureSharedTmp(tmp)

        val sysdataDir = File(context.filesDir, "sysdata")
        ProotSysdata.ensure(sysdataDir)

        val bindings = buildAndroidBindings(context, runtime, rootfsDir, tmp, sysdataDir)

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

        val sysdataDir = File(tmp.parentFile, "sysdata")
        ProotSysdata.ensure(sysdataDir)

        val bindings = buildAndroidBindings(
            context = context,
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

    fun ensureSharedTmp(tmpDir: File) {
        tmpDir.mkdirs()
        File(tmpDir, ".X11-unix").mkdirs()
    }

    fun guestPreloadLibraries(context: Context): List<String> {
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        return listOf(
            File(nativeLibDir, "libcowork_linkshim.so"),
            File(nativeLibDir, "libandroid-shmem.so"),
        ).filter { it.isFile }.map { it.absolutePath }
    }

    private fun appendProotExtensions(args: MutableList<String>) {
        args += "--kill-on-exit"
        args += "--link2symlink"
        args += "--sysvipc"
        args += "-L"
        val hostname = Build.MODEL.ifBlank { "cowork" }
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
        val machine = Build.SUPPORTED_ABIS.firstOrNull()?.replace("-", "_") ?: "aarch64"
        args += "--kernel-release=${ProotSysdata.kernelReleaseArg(hostname, machine)}"
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
            // Termux --shared-tmp: host tmp (with link2symlink .l2s) backs guest /tmp.
            "${tmpDir.absolutePath}:/tmp",
            "${tmpDir.absolutePath}:/dev/shm",
            "${File(sysdataDir, "sys_empty").absolutePath}:/sys/fs/selinux",
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
