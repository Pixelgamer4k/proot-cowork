package com.proot.cowork.userland

import java.io.File

/**
 * Vendored from UserLAnd LocalServerManager (BSD-2-Clause).
 */
class LocalServerManager(
    private val applicationFilesDirPath: String,
    private val busyboxExecutor: BusyboxExecutor,
) {
    private val vncDisplayNumber = UserlandConfig.VNC_DISPLAY

    fun Process.pid(): Long =
        toString().substringAfter("pid=").substringBefore(",").substringBefore("]").trim().toLong()

    fun startServer(session: CoworkSession, log: (String) -> Unit = {}): Long = when (session.serviceType) {
        ServiceType.Vnc -> startVncServer(session, log)
        else -> -1L
    }

    fun stopService(session: CoworkSession) {
        val command = "support/killProcTree.sh ${session.pid} ${session.serverPid(applicationFilesDirPath)}"
        busyboxExecutor.executeScript(command)
    }

    fun isServerRunning(session: CoworkSession): Boolean {
        val serverPid = session.serverPid(applicationFilesDirPath)
        if (serverPid <= 0) return false
        val command = "support/isServerInProcTree.sh $serverPid"
        return busyboxExecutor.executeScript(command) is SuccessfulExecution
    }

    private fun startVncServer(session: CoworkSession, log: (String) -> Unit = {}): Long {
        val filesystemDirName = session.filesystemId.toString()
        deletePidFile(session)
        val command = "/support/startVNCServer.sh"
        val env = hashMapOf(
            "INITIAL_USERNAME" to session.username,
            "INITIAL_VNC_PASSWORD" to session.vncPassword,
            "DIMENSIONS" to session.geometry,
            "VNC_DISPLAY" to vncDisplayNumber.toString(),
        )
        val result = busyboxExecutor.executeProotCommand(
            command = command,
            filesystemDirName = filesystemDirName,
            commandShouldTerminate = false,
            env = env,
            listener = log,
        )
        return when (result) {
            is OngoingExecution -> result.process.pid()
            is FailedExecution -> -1L
            else -> -1L
        }
    }

    private fun deletePidFile(session: CoworkSession) {
        val pidFile = File(session.pidFilePath(applicationFilesDirPath))
        if (pidFile.exists()) pidFile.delete()
    }
}

private fun CoworkSession.pidRelativeFilePath(): String {
    val display = UserlandConfig.VNC_DISPLAY
    return "/home/$username/.vnc/localhost:$display.pid"
}

private fun CoworkSession.pidFilePath(applicationFilesDirPath: String): String =
    "$applicationFilesDirPath/$filesystemId${pidRelativeFilePath()}"

private fun CoworkSession.serverPid(applicationFilesDirPath: String): Long {
    val pidFile = File(pidFilePath(applicationFilesDirPath))
    if (!pidFile.exists()) return -1
    return runCatching { pidFile.readText().trim().toLong() }.getOrDefault(-1)
}
