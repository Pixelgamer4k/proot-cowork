package com.termux.x11

import android.content.Context
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import com.proot.cowork.termux.x11.X11MouseTouchHandler
import com.termux.x11.input.InputEventSender

object X11SurfaceHostRegistry {
    @Volatile
    var current: X11SurfaceHost? = null
}

/**
 * Hosts [LorieView] with mouse-style touch routing (not direct XI touch).
 */
class X11SurfaceHost(context: Context) : FrameLayout(context) {

    val lorieView = LorieView(context)
    private val inputSender = InputEventSender(lorieView)
    private val touchHandler = X11MouseTouchHandler(
        context,
        inputSender,
        isConnected = { LorieView.connected() },
    )

    private var desktopInputEnabled = true

    init {
        X11SurfaceHostRegistry.current = this
        LorieViewEmbed.attachCallback(lorieView)
        setDesktopInputEnabled(true)

        addView(
            lorieView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        val dispatchTouch: (MotionEvent) -> Boolean = { event ->
            if (width > 0 && height > 0) {
                touchHandler.updateViewSize(width, height)
            }
            if (desktopInputEnabled) {
                focusDesktopKeyboard()
            }
            touchHandler.onTouchEvent(event)
        }

        setOnTouchListener { _, event -> dispatchTouch(event) }
        lorieView.setOnTouchListener { _, event -> dispatchTouch(event) }
    }

    fun setDesktopInputEnabled(enabled: Boolean) {
        desktopInputEnabled = enabled
        isClickable = enabled
        isFocusable = enabled
        isFocusableInTouchMode = enabled
        lorieView.isClickable = enabled
        lorieView.isFocusable = enabled
        lorieView.isFocusableInTouchMode = enabled
        if (!enabled) {
            hideDesktopKeyboard()
            lorieView.clearFocus()
            clearFocus()
        }
    }

    fun focusDesktopKeyboard() {
        if (!desktopInputEnabled) return
        lorieView.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return
        lorieView.postDelayed({
            if (lorieView.hasFocus()) {
                imm.showSoftInput(lorieView, InputMethodManager.SHOW_IMPLICIT)
            }
        }, 80)
    }

    fun hideDesktopKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return
        imm.hideSoftInputFromWindow(lorieView.windowToken, 0)
    }

    override fun onDetachedFromWindow() {
        if (X11SurfaceHostRegistry.current === this) {
            X11SurfaceHostRegistry.current = null
        }
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        touchHandler.updateViewSize(w, h)
    }
}
