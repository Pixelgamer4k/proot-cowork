package com.proot.cowork.termux.terminal

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import com.termux.view.TerminalView

/**
 * Terminal surface that commits IME composing text immediately so input echoes
 * live instead of only when the soft keyboard is dismissed.
 */
class CoworkTerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TerminalView(context, attrs) {

    override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return LiveEchoInputConnection(base)
    }

    private class LiveEchoInputConnection(
        target: InputConnection,
    ) : InputConnectionWrapper(target, true) {
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (!text.isNullOrEmpty()) {
                return commitText(text, newCursorPosition)
            }
            return super.setComposingText(text, newCursorPosition)
        }
    }
}
