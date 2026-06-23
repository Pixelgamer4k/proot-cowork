package com.proot.cowork.data.vnc

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.ReceiveChannel

object VncReadiness {

    private val logMarkers = listOf(
        "VNC_READY",
        "Listening for VNC connections on TCP port",
        "tightvncserver",
        "New 'X' desktop is",
    )

    fun isReadyLogLine(line: String): Boolean =
        logMarkers.any { marker -> line.contains(marker) }

    suspend fun awaitReady(
        logLines: ReceiveChannel<String>,
        timeoutMs: Long = VncConfig.BOOT_TIMEOUT_MS,
        pollMs: Long = VncConfig.POLL_INTERVAL_MS,
    ): Boolean = coroutineScope {
        val ready = CompletableDeferred<Unit>()

        val logWatcher = launch {
            for (line in logLines) {
                if (isReadyLogLine(line)) {
                    ready.complete(Unit)
                    break
                }
            }
        }

        val portWatcher = launch {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!ready.isCompleted && System.currentTimeMillis() < deadline) {
                if (VncPortProbe.isOpen()) {
                    ready.complete(Unit)
                    break
                }
                delay(pollMs)
            }
        }

        val ok = withTimeoutOrNull(timeoutMs) { ready.await(); true } == true
        logWatcher.cancel()
        portWatcher.cancel()
        ok
    }
}
