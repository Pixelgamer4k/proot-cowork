package com.proot.cowork.domain.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared state for the Termux + X11 dual-layer desktop (no proot).
 * Both layers keep running; [frontLayer] only changes z-order and touch routing.
 */
object TermuxStackSession {
    private val _frontLayer = MutableStateFlow(StackFrontLayer.X11)
    val frontLayer: StateFlow<StackFrontLayer> = _frontLayer.asStateFlow()

    private val _x11Ready = MutableStateFlow(false)
    val x11Ready: StateFlow<Boolean> = _x11Ready.asStateFlow()

    private val _termuxReady = MutableStateFlow(false)
    val termuxReady: StateFlow<Boolean> = _termuxReady.asStateFlow()

    private val _bootstrapReady = MutableStateFlow(false)
    val bootstrapReady: StateFlow<Boolean> = _bootstrapReady.asStateFlow()

    private val _logLines = MutableStateFlow<List<String>>(emptyList())
    val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    fun setFrontLayer(layer: StackFrontLayer) {
        _frontLayer.value = layer
    }

    fun toggleFrontLayer() {
        _frontLayer.value = when (_frontLayer.value) {
            StackFrontLayer.TERMUX -> StackFrontLayer.X11
            StackFrontLayer.X11 -> StackFrontLayer.TERMUX
        }
    }

    fun setX11Ready(ready: Boolean) {
        _x11Ready.value = ready
    }

    fun setTermuxReady(ready: Boolean) {
        _termuxReady.value = ready
    }

    fun setBootstrapReady(ready: Boolean) {
        _bootstrapReady.value = ready
    }

    fun appendLog(line: String) {
        _logLines.value = (_logLines.value + line).takeLast(200)
    }

    fun clearLogs() {
        _logLines.value = emptyList()
    }

    fun resetReadyFlags() {
        _x11Ready.value = false
        _termuxReady.value = false
    }
}
