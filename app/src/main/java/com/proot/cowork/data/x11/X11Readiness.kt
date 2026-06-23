package com.proot.cowork.data.x11

import com.proot.cowork.domain.proot.DesktopSession
import kotlinx.coroutines.delay
import java.io.File

object X11Readiness {
    suspend fun awaitSocket(tmpDir: File, display: Int = 0, timeoutMs: Long = 60_000): Boolean {
        val socket = File(tmpDir, ".X11-unix/X$display")
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (socket.exists()) {
                DesktopSession.appendLog("X11 socket ready at ${socket.absolutePath}")
                return true
            }
            delay(200)
        }
        DesktopSession.appendLog("Timed out waiting for X11 socket ${socket.absolutePath}")
        return false
    }
}
