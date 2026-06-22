package com.proot.cowork.data.vnc

import kotlinx.coroutines.delay
import java.net.InetSocketAddress
import java.net.Socket

object VncPortProbe {

    suspend fun waitUntilOpen(
        host: String = VncConfig.HOST,
        port: Int = VncConfig.PORT,
        timeoutMs: Long = VncConfig.BOOT_TIMEOUT_MS,
        pollMs: Long = VncConfig.POLL_INTERVAL_MS,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isOpen(host, port)) return true
            delay(pollMs)
        }
        return isOpen(host, port)
    }

    fun isOpen(host: String, port: Int): Boolean =
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), VncConfig.CONNECT_TIMEOUT_MS)
            }
        }.isSuccess
}
