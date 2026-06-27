package com.proot.cowork.termux.terminal

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class CoworkTerminalViewClient(
    private val terminalView: TerminalView,
) : TerminalViewClient {
    var ctrlLatched: Boolean = false
        private set
    var altLatched: Boolean = false
        private set
    var shiftLatched: Boolean = false
        private set

    fun toggleCtrl() {
        ctrlLatched = !ctrlLatched
    }

    fun toggleAlt() {
        altLatched = !altLatched
    }

    fun toggleShift() {
        shiftLatched = !shiftLatched
    }

    fun clearModifiers() {
        ctrlLatched = false
        altLatched = false
        shiftLatched = false
    }

    override fun onScale(scale: Float): Float = 1.0f
    override fun onSingleTapUp(e: MotionEvent) {
        TerminalKeyboard.focusAndShow(terminalView)
    }
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = ctrlLatched
    override fun readAltKey(): Boolean = altLatched
    override fun readShiftKey(): Boolean = shiftLatched
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() {}
    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        Log.e(tag, message, e)
    }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, Log.getStackTraceString(e)) }
}

class CoworkTerminalSessionClient(
    private val terminalView: TerminalView,
    private val onFinished: (() -> Unit)? = null,
) : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) {
        terminalView.post { terminalView.onScreenUpdated() }
    }
    override fun onTitleChanged(updatedSession: TerminalSession) {}
    override fun onSessionFinished(finishedSession: TerminalSession) {
        onFinished?.invoke()
    }
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
    override fun onPasteTextFromClipboard(session: TerminalSession) {}
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(changedSession: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int? = null
    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        Log.e(tag, message, e)
    }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, Log.getStackTraceString(e)) }
}
