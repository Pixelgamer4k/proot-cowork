package com.proot.cowork.debug

import android.content.Context
import android.net.Uri
import com.proot.cowork.BuildConfig
import com.proot.cowork.ProotCoworkApp
import com.proot.cowork.data.proot.ProotCommandBuilder
import com.proot.cowork.data.proot.ProotProcessLauncher
import com.proot.cowork.data.proot.RuntimeBootstrap
import com.proot.cowork.data.rootfs.RootfsValidator
import com.proot.cowork.domain.proot.DesktopSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object DebugBridge {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun handle(context: Context, command: String, extras: Map<String, String>): String {
        if (!BuildConfig.DEBUG) {
            return "Debug bridge disabled (not a debug build)"
        }

        return when (command.uppercase()) {
            "DUMP_STATUS" -> {
                DebugStatusWriter.refresh(context)
                "Wrote files/debug/status.json"
            }
            "START_DESKTOP" -> {
                (context.applicationContext as ProotCoworkApp)
                    .rootfsRepository.startDesktopService()
                DebugStatusWriter.refresh(context)
                "Started ProotDesktopService"
            }
            "STOP_DESKTOP" -> {
                (context.applicationContext as ProotCoworkApp)
                    .rootfsRepository.stopDesktopService()
                DebugStatusWriter.refresh(context)
                "Stopped desktop"
            }
            "REBOOT_DESKTOP" -> {
                (context.applicationContext as ProotCoworkApp)
                    .rootfsRepository.rebootDesktopService()
                DebugStatusWriter.refresh(context)
                "Rebooting desktop"
            }
            "TAIL_LOGS" -> {
                val lines = extras["lines"]?.toIntOrNull() ?: 80
                DebugBridge.tailLogs(context, lines)
            }
            "DUMP_PROOT_CMD" -> dumpProotCommand(context)
            "IMPORT_ROOTFS" -> {
                val path = extras["path"] ?: "/sdcard/proot-cowork-rootfs.tar.gz"
                importRootfsAsync(context, path)
                "Import started from $path (check status.json)"
            }
            "RUN_PROOT_SHELL" -> {
                val shellCmd = extras["command"] ?: "echo hello"
                runProotShellAsync(context, shellCmd)
                "Proot shell started: $shellCmd"
            }
            else -> "Unknown command: $command. Use: DUMP_STATUS, START_DESKTOP, STOP_DESKTOP, REBOOT_DESKTOP, TAIL_LOGS, DUMP_PROOT_CMD, IMPORT_ROOTFS, RUN_PROOT_SHELL"
        }
    }

    private fun tailLogs(context: Context, lines: Int): String {
        val session = DesktopSession.logLines.value.takeLast(lines).joinToString("\n")
        val file = DebugStatusWriter.tailLogs(context, lines)
        return buildString {
            appendLine("=== session logs (last $lines) ===")
            appendLine(if (session.isBlank()) "(empty)" else session)
            appendLine()
            appendLine("=== files/debug/last-proot.log (last $lines) ===")
            appendLine(file)
        }
    }

    private fun dumpProotCommand(context: Context): String {
        val app = context.applicationContext as ProotCoworkApp
        val rootfs = app.settingsRepository.getRootfsDir()
        if (!RootfsValidator.isValid(rootfs)) {
            return "Rootfs invalid or missing at ${rootfs.absolutePath}"
        }
        val runtime = RuntimeBootstrap(context).ensureRuntime()
        val command = ProotCommandBuilder.buildStartDesktop(context, runtime, rootfs)
        DebugStatusWriter.writeProotCommand(context, command)
        return command.joinToString(" ")
    }

    private fun importRootfsAsync(context: Context, path: String) {
        scope.launch {
            val file = resolveImportFile(context, path)
            if (file == null) {
                DesktopSession.appendLog("Debug import failed: not a file: $path")
                DebugStatusWriter.refresh(context)
                return@launch
            }
            val uri = Uri.fromFile(file)
            DesktopSession.appendLog("Debug import from ${file.absolutePath} (${file.length()} bytes)")
            val result = (context.applicationContext as ProotCoworkApp)
                .rootfsRepository.importFromUri(uri)
            DesktopSession.appendLog(
                when (result) {
                    is com.proot.cowork.data.rootfs.ImportResult.Success ->
                        "Debug import OK: ${result.rootfsDir.absolutePath}"
                    is com.proot.cowork.data.rootfs.ImportResult.Error ->
                        "Debug import error: ${result.message}"
                },
            )
            DebugStatusWriter.refresh(context)
        }
    }

    /** Resolve tarball paths that Java File cannot see directly (scoped storage aliases). */
    private fun resolveImportFile(context: Context, path: String): File? {
        val name = File(path).name
        val candidates = linkedSetOf<File>()
        candidates += File(path)
        if (path.startsWith("/sdcard/")) {
            candidates += File(path.replace("/sdcard/", "/storage/emulated/0/"))
        }
        context.getExternalFilesDir(null)?.let { ext ->
            candidates += File(ext, name)
        }
        context.filesDir?.let { files ->
            candidates += File(files, name)
        }
        return candidates.firstOrNull { it.isFile && it.canRead() && it.length() > 0L }
    }

    private fun runProotShellAsync(context: Context, shellCommand: String) {
        scope.launch {
            val app = context.applicationContext as ProotCoworkApp
            val rootfs = app.settingsRepository.getRootfsDir()
            if (!RootfsValidator.isValid(rootfs)) {
                DesktopSession.appendLog("RUN_PROOT_SHELL: invalid rootfs")
                return@launch
            }
            try {
                val runtime = RuntimeBootstrap(context).ensureRuntime()
                val command = ProotCommandBuilder.buildShell(runtime, rootfs, shellCommand)
                DebugStatusWriter.writeProotCommand(context, command)
                DebugStatusWriter.clearProotLog(context)
                val env = ProotCommandBuilder.launchEnvironment(context, runtime)
                DebugStatusWriter.writeProotCommand(context, command)
                DebugStatusWriter.clearProotLog(context)
                val process = ProotProcessLauncher.start(
                    context = context,
                    argv = command,
                    env = env,
                )
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        DesktopSession.appendLog("[shell] $line")
                        DebugStatusWriter.appendProotLog(context, line)
                        line = reader.readLine()
                    }
                }
                val exit = process.waitFor()
                DebugStatusWriter.writeProotExit(context, exit)
                DesktopSession.appendLog("[shell] exit=$exit")
                DebugStatusWriter.refresh(context)
            } catch (e: Exception) {
                DesktopSession.appendLog("[shell] error: ${e.message}")
                DebugStatusWriter.refresh(context)
            }
        }
    }
}
