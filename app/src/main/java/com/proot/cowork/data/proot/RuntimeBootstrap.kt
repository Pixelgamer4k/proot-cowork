package com.proot.cowork.data.proot

import android.content.Context
import android.os.Build
import java.io.File

class RuntimeBootstrap(private val context: Context) {

    fun ensureRuntime(): ProotRuntime {
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val prootBin = File(nativeLibDir, PROOT_LIB_NAME)
        if (!prootBin.isFile) {
            throw IllegalStateException(
                "Missing $PROOT_LIB_NAME in nativeLibraryDir (${nativeLibDir.absolutePath}). " +
                    "Rebuild the APK with jniLibs bundled.",
            )
        }

        return ProotRuntime(
            prootBinary = prootBin,
            libraryPath = nativeLibDir,
            tmpDir = File(context.filesDir, "tmp").also { it.mkdirs() },
            useLinker64 = is64BitAbi(),
        )
    }

    private fun is64BitAbi(): Boolean {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return abi.contains("64") || abi == "arm64-v8a" || abi == "x86_64"
    }

    companion object {
        const val PROOT_LIB_NAME = "libproot_exec.so"
    }
}

data class ProotRuntime(
    val prootBinary: File,
    val libraryPath: File,
    val tmpDir: File,
    val useLinker64: Boolean,
) {
    fun launchCommand(prootArgs: List<String>): List<String> {
        val linker = if (useLinker64) "/system/bin/linker64" else "/system/bin/linker"
        return listOf(linker, prootBinary.absolutePath) + prootArgs
    }
}
