package com.proot.cowork.data.proot

import android.content.Context
import com.proot.cowork.data.prootcontainer.ProotContainerValidator
import com.proot.cowork.domain.desktop.TermuxStackSession
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.termux.bootstrap.TermuxBootstrap
import com.proot.cowork.termux.bootstrap.TermuxShellEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader

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
        if (!ProotContainerValidator.isInstalled(context, distro)) {
            return@withContext ShellResult.error("Ubuntu container not installed")
        }
        val bash = TermuxBootstrap.shellExecutable(context)
            ?: return@withContext ShellResult.error("Termux bash not ready")

        val env = TermuxShellEnvironment.buildProcessEnvironment(context)
        val guestCmd = "proot-distro login ${shellQuote(distro)} --shared-tmp -- bash -lc ${shellQuote(command)}"
        val pb = ProcessBuilder(bash.absolutePath, "-c", guestCmd)
            .directory(TermuxBootstrap.homeDir(context))
            .redirectErrorStream(true)
        pb.environment().clear()
        pb.environment().putAll(env)

        val result = withTimeoutOrNull(timeoutMs) {
            runCatching {
                val process = pb.start()
                val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
                val code = process.waitFor()
                ShellResult(exitCode = code, output = output.trim())
            }.getOrElse { ShellResult.error(it.message ?: "Shell failed") }
        } ?: ShellResult.error("Command timed out after ${timeoutMs / 1000}s")

        val line = "[$distro] $command → exit ${result.exitCode}"
        DesktopSession.appendLog(line)
        TermuxStackSession.appendLog(line)
        result
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
