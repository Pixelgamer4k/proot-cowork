package com.proot.cowork.userland

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

/**
 * Vendored from UserLAnd BusyboxExecutor (BSD-2-Clause).
 */
class BusyboxExecutor(
    private val ulaFiles: UserlandFiles,
    private val prootDebugLogger: ProotDebugLogger,
    private val busyboxWrapper: BusyboxWrapper = BusyboxWrapper(ulaFiles),
) {
    private val discardOutput: (String) -> Any = { Log.d("userland", it) }

    fun executeScript(
        scriptCall: String,
        listener: (String) -> Any = discardOutput,
    ): ExecutionResult {
        val updatedCommand = busyboxWrapper.wrapScript(scriptCall)
        return runCommand(updatedCommand, listener)
    }

    fun executeProotCommand(
        command: String,
        filesystemDirName: String,
        commandShouldTerminate: Boolean,
        env: HashMap<String, String> = hashMapOf(),
        listener: (String) -> Any = discardOutput,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    ): ExecutionResult {
        when {
            !busyboxWrapper.busyboxIsPresent() ->
                return MissingExecutionAsset("busybox")
            !busyboxWrapper.prootIsPresent() ->
                return MissingExecutionAsset("proot")
            !busyboxWrapper.executionScriptIsPresent() ->
                return MissingExecutionAsset("execution script")
        }

        val prootDebugLevel =
            if (prootDebugLogger.isEnabled) prootDebugLogger.verbosityLevel else "-1"

        val updatedCommand = busyboxWrapper.addBusyboxAndProot(command)
        val filesystemDir = File("${ulaFiles.filesDir.absolutePath}/$filesystemDirName")
        env.putAll(busyboxWrapper.getProotEnv(filesystemDir, prootDebugLevel))

        val processBuilder = ProcessBuilder(updatedCommand)
        processBuilder.directory(ulaFiles.filesDir)
        processBuilder.environment().putAll(env)
        processBuilder.redirectErrorStream(true)

        return try {
            val process = processBuilder.start()
            if (commandShouldTerminate) {
                collectOutput(process.inputStream, listener)
                getProcessResult(process)
            } else {
                streamOutputAsync(process.inputStream, listener, coroutineScope)
                OngoingExecution(process)
            }
        } catch (err: Exception) {
            FailedExecution("$err")
        }
    }

    private fun runCommand(command: List<String>, listener: (String) -> Any): ExecutionResult {
        if (!busyboxWrapper.busyboxIsPresent()) {
            return MissingExecutionAsset("busybox")
        }
        val env = busyboxWrapper.getBusyboxEnv()
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(ulaFiles.filesDir)
        processBuilder.environment().putAll(env)
        processBuilder.redirectErrorStream(true)
        return try {
            val process = processBuilder.start()
            collectOutput(process.inputStream, listener)
            getProcessResult(process)
        } catch (err: Exception) {
            FailedExecution("$err")
        }
    }

    private fun collectOutput(inputStream: InputStream, listener: (String) -> Any) {
        inputStream.bufferedReader(Charsets.UTF_8).forEachLine { listener(it) }
    }

    private fun streamOutputAsync(
        inputStream: InputStream,
        listener: (String) -> Any,
        scope: CoroutineScope,
    ) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                inputStream.bufferedReader(Charsets.UTF_8).forEachLine { listener(it) }
            }
        }
    }

    private fun getProcessResult(process: Process): ExecutionResult =
        if (process.waitFor() == 0) SuccessfulExecution else FailedExecution("exit ${process.exitValue()}")
}

class BusyboxWrapper(private val ulaFiles: UserlandFiles) {
    fun wrapScript(command: String): List<String> =
        listOf(ulaFiles.busybox.path, "sh") + command.split(" ")

    fun getBusyboxEnv(): HashMap<String, String> = hashMapOf(
        "LIB_PATH" to ulaFiles.supportDir.absolutePath,
        "ROOT_PATH" to ulaFiles.filesDir.absolutePath,
    )

    fun busyboxIsPresent(): Boolean = ulaFiles.busybox.exists()

    fun addBusyboxAndProot(command: String): List<String> =
        listOf(ulaFiles.busybox.absolutePath, "sh", "support/execInProot.sh") + command.split(" ")

    fun getProotEnv(filesystemDir: File, prootDebugLevel: String): HashMap<String, String> {
        handleHangingBindingDirectories(filesystemDir)
        val emulatedStorageBinding =
            "-b ${ulaFiles.emulatedUserDir.absolutePath}:/storage/internal"
        val externalStorageBinding = ulaFiles.sdCardUserDir?.let {
            "-b ${it.absolutePath}:/storage/sdcard"
        } ?: ""
        val apexBinding = if (java.io.File("/apex").isDirectory) "-b /apex:/apex " else ""
        val bindings = "$apexBinding$emulatedStorageBinding $externalStorageBinding".trim()
        return hashMapOf(
            "LD_LIBRARY_PATH" to ulaFiles.supportDir.absolutePath,
            "LIB_PATH" to ulaFiles.supportDir.absolutePath,
            "ROOT_PATH" to ulaFiles.filesDir.absolutePath,
            "ROOTFS_PATH" to filesystemDir.absolutePath,
            "PROOT_DEBUG_LEVEL" to prootDebugLevel,
            "EXTRA_BINDINGS" to bindings,
            "OS_VERSION" to System.getProperty("os.version")!!,
        )
    }

    fun prootIsPresent(): Boolean = ulaFiles.proot.exists()

    fun executionScriptIsPresent(): Boolean =
        File(ulaFiles.supportDir, "execInProot.sh").exists()

    private fun handleHangingBindingDirectories(filesystemDir: File) {
        val storageBindingDir = File(filesystemDir, "storage")
        if (storageBindingDir.exists() && storageBindingDir.isDirectory &&
            (storageBindingDir.listFiles()?.isEmpty() != false)
        ) {
            storageBindingDir.delete()
        }
        storageBindingDir.mkdirs()

        val sdCardBindingDir = File(filesystemDir, "sdcard")
        if (sdCardBindingDir.exists() && sdCardBindingDir.isDirectory &&
            (sdCardBindingDir.listFiles()?.isEmpty() != false)
        ) {
            sdCardBindingDir.delete()
        }
    }
}
