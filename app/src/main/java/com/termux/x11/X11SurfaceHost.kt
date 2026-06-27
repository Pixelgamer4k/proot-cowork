package com.termux.x11

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import com.proot.cowork.termux.x11.X11MouseTouchHandler
import com.termux.x11.input.InputEventSender

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
                lorieView.requestFocus()
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
            lorieView.clearFocus()
            clearFocus()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        touchHandler.updateViewSize(w, h)
    }
}
