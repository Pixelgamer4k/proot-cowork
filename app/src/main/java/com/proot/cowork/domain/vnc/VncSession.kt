package com.proot.cowork.domain.vnc

import android.graphics.Bitmap
import com.proot.cowork.data.vnc.RfbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object VncSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var client: RfbClient? = null
    private var loopJob: Job? = null

    private val _frame = MutableStateFlow<Bitmap?>(null)
    val frame: StateFlow<Bitmap?> = _frame.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun connect() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            _error.value = null
            var bitmap: Bitmap? = null
            while (isActive) {
                try {
                    val rfb = RfbClient()
                    rfb.connect()
                    client = rfb
                    _connected.value = true
                    bitmap = Bitmap.createBitmap(rfb.width, rfb.height, Bitmap.Config.ARGB_8888)
                    var incremental = false
                    while (isActive && rfb.isConnected) {
                        rfb.requestFramebufferUpdate(incremental)
                        bitmap = rfb.readFramebuffer(bitmap)
                        _frame.value = bitmap
                        incremental = true
                        delay(16)
                    }
                } catch (e: Exception) {
                    _connected.value = false
                    _error.value = e.message ?: "VNC disconnected"
                    delay(1500)
                } finally {
                    client?.disconnect()
                    client = null
                    _connected.value = false
                }
            }
        }
    }

    fun disconnect() {
        loopJob?.cancel()
        loopJob = null
        client?.disconnect()
        client = null
        _connected.value = false
    }

    fun sendPointer(x: Int, y: Int, buttonMask: Int) {
        client?.sendPointerEvent(x, y, buttonMask)
    }

    fun sendKey(key: Int, down: Boolean) {
        client?.sendKeyEvent(key, down)
    }

    fun currentFrame(): Bitmap? = _frame.value
}
