package com.proot.cowork.termux.terminal

import android.content.Context
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.termux.view.TerminalView

object TerminalKeyboard {

    private val setupMarker = Any()

    fun setupOnce(terminalView: TerminalView) {
        if (terminalView.tag === setupMarker) return
        terminalView.tag = setupMarker
        setup(terminalView)
    }

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
            if (!view.hasFocus()) {
                view.requestFocus()
            }
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    fun hide(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun toggle(view: View) {
        view.requestFocus()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return
        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
    }
}
