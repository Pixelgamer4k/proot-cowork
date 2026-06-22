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

        val supportLibDir = File(context.filesDir, "exec_libs").also { it.mkdirs() }
        ensureVersionedTalloc(nativeLibDir, supportLibDir)
        ensureProotLoaders(nativeLibDir, supportLibDir)

        val ldLibraryPath = listOf(nativeLibDir, supportLibDir)
            .joinToString(":") { it.absolutePath }

        val nativeLoader = File(nativeLibDir, LOADER_JNI_NAME)
        val nativeLoader32 = File(nativeLibDir, LOADER32_JNI_NAME)

        return ProotRuntime(
            prootBinary = prootBin,
            ldLibraryPath = ldLibraryPath,
            tmpDir = File(context.filesDir, "tmp").also { it.mkdirs() },
            loaderPath = nativeLoader,
            loader32Path = nativeLoader32,
            useLinker64 = is64BitAbi(),
        )
    }

    /**
     * Termux proot requires companion loader binaries beside the main proot executable.
     * Without PROOT_LOADER set, guest ELF exec fails with Permission denied on Android.
     */
    private fun ensureProotLoaders(nativeLibDir: File, supportLibDir: File): File {
        val loaderDir = File(supportLibDir, "proot").also { it.mkdirs() }
        val loader = File(loaderDir, LOADER_NAME)
        val loader32 = File(loaderDir, LOADER32_NAME)

        if (loader.isFile && loader32.isFile && loader.length() > 0L) {
            return loaderDir
        }

        val sources = listOf(
            File(nativeLibDir, LOADER_JNI_NAME) to loader,
            File(nativeLibDir, LOADER32_JNI_NAME) to loader32,
        )
        for ((source, dest) in sources) {
            if (!source.isFile) {
                throw IllegalStateException(
                    "Missing ${source.name} in nativeLibraryDir. " +
                        "Run scripts/fetch-proot-runtime.sh and rebuild.",
                )
            }
            source.copyTo(dest, overwrite = true)
            dest.setReadable(true, false)
            dest.setExecutable(true, false)
        }
        return loaderDir
    }

    /**
     * proot links against libtalloc.so.2, but AGP only packages lib*.so (not lib*.so.N)
     * into the APK. Copy the soname beside the extracted native libs at runtime.
     */
    private fun ensureVersionedTalloc(nativeLibDir: File, supportLibDir: File) {
        val tallocV2 = File(supportLibDir, "libtalloc.so.2")
        if (tallocV2.isFile) return

        val sources = listOf(
            File(nativeLibDir, "libtalloc.so"),
            File(nativeLibDir, "libtalloc.so.2"),
        )
        val source = sources.firstOrNull { it.isFile }
            ?: throw IllegalStateException("Missing libtalloc.so in ${nativeLibDir.absolutePath}")

        source.copyTo(tallocV2, overwrite = true)
        tallocV2.setReadable(true, false)
    }

    private fun is64BitAbi(): Boolean {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return abi.contains("64") || abi == "arm64-v8a" || abi == "x86_64"
    }

    companion object {
        const val PROOT_LIB_NAME = "libproot_exec.so"
        const val LOADER_JNI_NAME = "libproot_loader.so"
        const val LOADER32_JNI_NAME = "libproot_loader32.so"
        private const val LOADER_NAME = "loader"
        private const val LOADER32_NAME = "loader32"
    }
}

data class ProotRuntime(
    val prootBinary: File,
    val ldLibraryPath: String,
    val tmpDir: File,
    val loaderPath: File,
    val loader32Path: File,
    val useLinker64: Boolean,
) {
    fun launchCommand(prootArgs: List<String>): List<String> {
        val linker = if (useLinker64) "/system/bin/linker64" else "/system/bin/linker"
        return listOf(linker, prootBinary.absolutePath) + prootArgs
    }
}
