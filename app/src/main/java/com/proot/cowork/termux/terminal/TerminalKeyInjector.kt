package com.proot.cowork.termux.terminal

import android.os.Build
import android.view.KeyEvent
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

/** Sends Termux-style extra-key presses to a [TerminalView]. */
object TerminalKeyInjector {

    private val primaryKeyCodes = mapOf(
        "SPACE" to KeyEvent.KEYCODE_SPACE,
        "ESC" to KeyEvent.KEYCODE_ESCAPE,
        "TAB" to KeyEvent.KEYCODE_TAB,
        "HOME" to KeyEvent.KEYCODE_MOVE_HOME,
        "END" to KeyEvent.KEYCODE_MOVE_END,
        "PGUP" to KeyEvent.KEYCODE_PAGE_UP,
        "PGDN" to KeyEvent.KEYCODE_PAGE_DOWN,
        "INS" to KeyEvent.KEYCODE_INSERT,
        "DEL" to KeyEvent.KEYCODE_FORWARD_DEL,
        "BKSP" to KeyEvent.KEYCODE_DEL,
        "UP" to KeyEvent.KEYCODE_DPAD_UP,
        "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT,
        "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
        "ENTER" to KeyEvent.KEYCODE_ENTER,
    )

    private val displayLabels = mapOf(
        "LEFT" to "←",
        "RIGHT" to "→",
        "UP" to "↑",
        "DOWN" to "↓",
        "TAB" to "↹",
        "BKSP" to "⌫",
        "DEL" to "⌦",
        "KEYBOARD" to "⌨",
        "DRAWER" to "☰",
        "PASTE" to "⎘",
        "HOME" to "⇱",
        "END" to "⇲",
        "PGUP" to "⇑",
        "PGDN" to "⇓",
        "-" to "―",
    )

    fun labelFor(key: String): String = displayLabels[key] ?: key

    fun sendKey(
        terminalView: TerminalView,
        client: CoworkTerminalViewClient,
        key: String,
    ) {
        val session = terminalView.currentSession
        if (session == null || !session.isRunning) return

        when (key) {
            "CTRL" -> client.toggleCtrl()
            "ALT" -> client.toggleAlt()
            "SHIFT" -> client.toggleShift()
            "KEYBOARD" -> TerminalKeyboard.toggle(terminalView)
            else -> sendTerminalKey(terminalView, client, key)
        }
    }

    private fun sendTerminalKey(
        terminalView: TerminalView,
        client: CoworkTerminalViewClient,
        key: String,
    ) {
        val ctrl = client.ctrlLatched
        val alt = client.altLatched
        val shift = client.shiftLatched

        val keyCode = primaryKeyCodes[key]
        if (keyCode != null) {
            var metaState = 0
            if (ctrl) metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            if (alt) metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            if (shift) metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            val event = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, metaState)
            terminalView.onKeyDown(keyCode, event)
            val up = KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, metaState)
            terminalView.onKeyUp(keyCode, up)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            key.codePoints().forEach { codePoint ->
                terminalView.inputCodePoint(codePoint, ctrl, alt)
            }
        } else {
            val session = terminalView.currentSession
            if (session != null && key.isNotEmpty()) {
                session.write(key)
            }
        }
    }
}
