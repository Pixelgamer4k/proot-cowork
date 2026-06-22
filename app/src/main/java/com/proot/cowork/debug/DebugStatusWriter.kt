package com.proot.cowork.debug

import android.content.Context
import android.os.Build
import com.proot.cowork.BuildConfig
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.domain.proot.DesktopState
import org.json.JSONObject
import java.io.File

object DebugStatusWriter {

    private var debugDir: File? = null

    fun init(context: Context) {
        if (!BuildConfig.DEBUG) return
        debugDir = File(context.filesDir, "debug").also { it.mkdirs() }
        refresh(context)
    }

    fun refresh(context: Context) {
        if (!BuildConfig.DEBUG) return
        val dir = debugDir ?: File(context.filesDir, "debug").also { it.mkdirs(); debugDir = it }
        val rootfs = File(context.filesDir, "rootfs")
        val status = JSONObject().apply {
            put("version", BuildConfig.VERSION_NAME)
            put("package", context.packageName)
            put("desktopState", DesktopSession.state.value.name)
            put("timestampMs", System.currentTimeMillis())
            put("deviceModel", Build.MODEL)
            put("abis", Build.SUPPORTED_ABIS.joinToString(","))
            put("rootfsInstalled", rootfs.isDirectory)
            put("rootfsHasXvfb", File(rootfs, "usr/bin/Xvfb").isFile)
            put("rootfsHasX11vnc", File(rootfs, "usr/bin/x11vnc").isFile)
            put("lastProotCommand", readText(dir, "last-proot-command.txt"))
            put("lastProotExit", readText(dir, "last-proot-exit.txt"))
            put("recentLogs", DesktopSession.logLines.value.takeLast(30).joinToString("\n"))
        }
        File(dir, "status.json").writeText(status.toString(2))
    }

    fun writeProotCommand(context: Context, command: List<String>) {
        if (!BuildConfig.DEBUG) return
        val dir = debugDir ?: File(context.filesDir, "debug").also { it.mkdirs(); debugDir = it }
        val text = command.joinToString(" ") { part ->
            if (part.contains(' ') || part.contains('"')) "\"${part.replace("\"", "\\\"")}\"" else part
        }
        File(dir, "last-proot-command.txt").writeText(text)
        refresh(context)
    }

    fun appendProotLog(context: Context, line: String) {
        if (!BuildConfig.DEBUG) return
        val dir = debugDir ?: File(context.filesDir, "debug").also { it.mkdirs(); debugDir = it }
        val logFile = File(dir, "last-proot.log")
        logFile.appendText("$line\n")
        if (logFile.length() > 512 * 1024) {
            val tail = logFile.readLines().takeLast(400).joinToString("\n")
            logFile.writeText(tail)
            if (tail.isNotEmpty()) logFile.appendText("\n")
        }
    }

    fun clearProotLog(context: Context) {
        if (!BuildConfig.DEBUG) return
        val dir = debugDir ?: return
        File(dir, "last-proot.log").writeText("")
    }

    fun writeProotExit(context: Context, code: Int) {
        if (!BuildConfig.DEBUG) return
        val dir = debugDir ?: File(context.filesDir, "debug").also { it.mkdirs(); debugDir = it }
        File(dir, "last-proot-exit.txt").writeText("exit=$code at ${System.currentTimeMillis()}")
        refresh(context)
    }

    fun writeTextFile(context: Context, name: String, text: String) {
        if (!BuildConfig.DEBUG) return
        val dir = debugDir ?: File(context.filesDir, "debug").also { it.mkdirs(); debugDir = it }
        File(dir, name).writeText(text)
    }

    fun tailLogs(context: Context, lines: Int = 80): String {
        if (!BuildConfig.DEBUG) return ""
        val dir = debugDir ?: return ""
        val file = File(dir, "last-proot.log")
        if (!file.isFile) return "(no proot log yet)"
        return file.readLines().takeLast(lines).joinToString("\n")
    }

    private fun readText(dir: File, name: String): String {
        val file = File(dir, name)
        return if (file.isFile) file.readText().trim() else ""
    }
}
