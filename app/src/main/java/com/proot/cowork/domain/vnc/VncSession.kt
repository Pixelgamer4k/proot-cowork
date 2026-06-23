package com.proot.cowork.domain.vnc

import android.graphics.Bitmap
import com.proot.cowork.data.vnc.RfbClient
import com.proot.cowork.data.vnc.VncPortProbe
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
import kotlinx.coroutines.withContext

object VncSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var client: RfbClient? = null
    private var loopJob: Job? = null
    private var generation = 0

    private val _frame = MutableStateFlow<Bitmap?>(null)
    val frame: StateFlow<Bitmap?> = _frame.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun connect() {
        if (loopJob?.isActive == true) return
        val gen = ++generation
        loopJob = scope.launch {
            _error.value = null
            var bitmap: Bitmap? = null
            while (isActive && gen == generation) {
                try {
                    if (!VncPortProbe.isOpen()) {
                        _connected.value = false
                        delay(400)
                        continue
                    }

                    val rfb = RfbClient()
                    rfb.connect()
                    if (gen != generation) {
                        rfb.disconnect()
                        break
                    }

                    client = rfb
                    _connected.value = true
                    bitmap = Bitmap.createBitmap(rfb.width, rfb.height, Bitmap.Config.ARGB_8888)
                    var incremental = false
                    while (isActive && gen == generation && rfb.isConnected) {
                        rfb.requestFramebufferUpdate(incremental)
                        bitmap = rfb.readFramebuffer(bitmap)
                        val displayFrame = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        withContext(Dispatchers.Main) {
                            if (gen == generation) {
                                _frame.value = displayFrame
                            } else {
                                displayFrame.recycle()
                            }
                        }
                        incremental = true
                        delay(33)
                    }
                } catch (e: Exception) {
                    if (gen != generation) break
                    _connected.value = false
                    _error.value = e.message ?: "VNC disconnected"
                    withContext(Dispatchers.Main) {
                        if (gen == generation) _frame.value = null
                    }
                    delay(1500)
                } finally {
                    client?.disconnect()
                    if (client != null && gen == generation) {
                        client = null
                        _connected.value = false
                    }
                }
            }
        }
    }

    fun disconnect() {
        generation++
        loopJob?.cancel()
        loopJob = null
        client?.disconnect()
        client = null
        _connected.value = false
        scope.launch(Dispatchers.Main) {
            _frame.value = null
            _error.value = null
        }
    }

    fun sendPointer(x: Int, y: Int, buttonMask: Int) {
        if (!_connected.value) return
        val rfb = client ?: return
        scope.launch {
            runCatching { rfb.sendPointerEvent(x, y, buttonMask) }
        }
    }

    fun sendKey(key: Int, down: Boolean) {
        if (!_connected.value) return
        val rfb = client ?: return
        scope.launch {
            runCatching { rfb.sendKeyEvent(key, down) }
        }
    }

    fun currentFrame(): Bitmap? = _frame.value
}
