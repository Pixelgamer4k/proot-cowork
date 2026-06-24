package com.termux.x11

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import com.termux.x11.input.InputEventSender
import com.termux.x11.input.InputStub

/**
 * Hosts a real [LorieView] (Termux:X11). Shows only what X clients paint — no placeholder UI.
 */
class X11SurfaceHost(context: Context) : FrameLayout(context) {

    val lorieView = LorieView(context)
    private val inputSender = InputEventSender(lorieView)

    init {
        lorieView.setCallback { _, _, screenWidth, screenHeight ->
            val rate = lorieView.display?.refreshRate?.toInt() ?: 60
            LorieView.sendWindowChange(screenWidth, screenHeight, rate, "builtin")
        }
        addView(
            lorieView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        setOnTouchListener { _, event -> handleTouch(event) }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        if (!LorieView.connected()) return false
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lorieView.requestFocus()
                inputSender.sendMouseEvent(
                    android.graphics.PointF(x, y),
                    InputStub.BUTTON_LEFT,
                    true,
                    false,
                )
            }
            MotionEvent.ACTION_UP -> {
                inputSender.sendMouseEvent(
                    android.graphics.PointF(x, y),
                    InputStub.BUTTON_LEFT,
                    false,
                    false,
                )
            }
            MotionEvent.ACTION_MOVE -> {
                inputSender.sendMouseEvent(
                    android.graphics.PointF(x, y),
                    InputStub.BUTTON_LEFT,
                    true,
                    false,
                )
            }
        }
        return true
    }
}
