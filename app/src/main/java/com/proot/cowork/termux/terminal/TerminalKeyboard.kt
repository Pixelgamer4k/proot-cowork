package com.proot.cowork.termux.terminal

import android.content.Context
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.termux.view.TerminalView

object TerminalKeyboard {

    fun setup(terminalView: TerminalView) {
        terminalView.isFocusable = true
        terminalView.isFocusableInTouchMode = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            terminalView.defaultFocusHighlightEnabled = false
        }
        terminalView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                show(view)
            }
        }
    }

    fun focusAndShow(view: View) {
        view.requestFocus()
        show(view)
    }

    fun show(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return
        view.postDelayed({
            view.requestFocus()
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }
}
