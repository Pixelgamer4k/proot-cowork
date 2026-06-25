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
    private val touchHandler = X11MouseTouchHandler(context, inputSender)

    init {
        LorieViewEmbed.attachCallback(lorieView)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        lorieView.isClickable = true
        lorieView.isFocusable = true
        lorieView.isFocusableInTouchMode = true

        addView(
            lorieView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        val dispatchTouch: (MotionEvent) -> Boolean = { event ->
            if (width > 0 && height > 0) {
                touchHandler.updateViewSize(width, height)
            }
            lorieView.requestFocus()
            touchHandler.onTouchEvent(event)
        }

        setOnTouchListener { _, event -> dispatchTouch(event) }
        lorieView.setOnTouchListener { _, event -> dispatchTouch(event) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        touchHandler.updateViewSize(w, h)
    }
}
