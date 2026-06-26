package com.proot.cowork.data.proot

import android.content.Context
import com.proot.cowork.data.prootcontainer.ProotContainerValidator
import com.proot.cowork.domain.agent.AgentRunController
import com.proot.cowork.domain.desktop.TermuxStackSession
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.termux.bootstrap.TermuxBootstrap
import com.proot.cowork.termux.bootstrap.TermuxShellEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class ShellResult(
    val exitCode: Int,
    val output: String,
    val error: String? = null,
) {
    val success: Boolean get() = exitCode == 0 && error == null

    companion object {
        fun error(message: String) = ShellResult(exitCode = -1, output = "", error = message)
    }
}

/** Runs bash commands inside the proot-distro guest (ubuntu by default). */
class ProotGuestShellExecutor(private val context: Context) {

    suspend fun run(
        command: String,
        distro: String = ProotContainerValidator.DEFAULT_DISTRO,
        timeoutMs: Long = 120_000L,
    ): ShellResult = withContext(Dispatchers.IO) {
        if (!AgentRunController.isActive()) {
            return@withContext ShellResult.error("Cancelled by user")
        }
        if (!ProotContainerValidator.isInstalled(context, distro)) {
            return@withContext ShellResult.error("Ubuntu container not installed")
        }
        val bash = TermuxBootstrap.shellExecutable(context)
            ?: return@withContext ShellResult.error("Termux bash not ready")

        val env = TermuxShellEnvironment.buildProcessEnvironment(context)
        val prootDistro = File(TermuxBootstrap.prefixDir(context), "bin/proot-distro")
        if (!prootDistro.canExecute()) {
            return@withContext ShellResult.error("proot-distro not installed in Termux prefix")
        }
        val guestCmd =
            "${shellQuote(prootDistro.absolutePath)} login ${shellQuote(distro)} --shared-tmp -- bash -lc ${shellQuote(command)}"
        val pb = ProcessBuilder(bash.absolutePath, "-c", guestCmd)
            .directory(TermuxBootstrap.homeDir(context))
            .redirectErrorStream(true)
        pb.environment().clear()
        pb.environment().putAll(env)

        val result = withTimeoutOrNull(timeoutMs) {
            runCatching {
                if (!AgentRunController.isActive()) {
                    return@runCatching ShellResult.error("Cancelled by user")
                }
                val process = pb.start()
                AgentRunController.registerProcess(process)
                try {
                    waitForProcess(process)
                    if (!AgentRunController.isActive()) {
                        if (process.isAlive) process.destroyForcibly()
                        ShellResult.error("Cancelled by user")
                    } else {
                        val output = process.inputStream.bufferedReader().use { it.readText() }
                        ShellResult(exitCode = process.exitValue(), output = output.trim())
                    }
                } finally {
                    AgentRunController.unregisterProcess(process)
                    if (process.isAlive) process.destroyForcibly()
                }
            }.getOrElse { ShellResult.error(it.message ?: "Shell failed") }
        } ?: ShellResult.error("Command timed out after ${timeoutMs / 1000}s")

        val line = "[$distro] $command → exit ${result.exitCode}"
        DesktopSession.appendLog(line)
        TermuxStackSession.appendLog(line)
        result
    }

    private suspend fun waitForProcess(process: Process) {
        while (process.isAlive) {
            if (!AgentRunController.isActive()) {
                process.destroyForcibly()
                return
            }
            delay(100)
        }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
