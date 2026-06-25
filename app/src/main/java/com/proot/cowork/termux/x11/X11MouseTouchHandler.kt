package com.proot.cowork.termux.x11

import android.content.Context
import android.graphics.PointF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.termux.x11.LorieView
import com.termux.x11.input.InputEventSender
import com.termux.x11.input.InputStub
import kotlin.math.hypot

/**
 * Maps touchscreen input to X11 mouse events (absolute cursor), not XI touch events.
 * Touchpad-style: finger moves the cursor; tap clicks; drag holds left button; long-press right-clicks.
 */
class X11MouseTouchHandler(
    context: Context,
    private val inputSender: InputEventSender,
    private val screenWidth: Int = X11DisplayConfig.WIDTH,
    private val screenHeight: Int = X11DisplayConfig.HEIGHT,
) {
    private var viewWidth = 1
    private var viewHeight = 1

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastTwoFingerY = 0f
    private var isDragging = false
    private var pointerDown = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onLongPress(e: MotionEvent) {
                if (!LorieView.connected() || e.pointerCount > 1) return
                val (x, y) = mapPoint(e.x, e.y)
                clickAt(x, y, InputStub.BUTTON_RIGHT)
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (!LorieView.connected()) return false
                inputSender.sendMouseWheelEvent(distanceX, distanceY)
                return true
            }
        },
    )

    fun updateViewSize(width: Int, height: Int) {
        viewWidth = width.coerceAtLeast(1)
        viewHeight = height.coerceAtLeast(1)
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (!LorieView.connected()) return false

        if (event.pointerCount >= 2) {
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    lastTwoFingerY = (event.getY(0) + event.getY(1)) / 2f
                }
                MotionEvent.ACTION_MOVE -> {
                    val centerY = (event.getY(0) + event.getY(1)) / 2f
                    if (lastTwoFingerY != 0f) {
                        val delta = lastTwoFingerY - centerY
                        inputSender.sendMouseWheelEvent(0f, delta * 0.35f)
                    }
                    lastTwoFingerY = centerY
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    lastTwoFingerY = 0f
                    pointerDown = false
                    isDragging = false
                }
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerDown = true
                isDragging = false
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                moveCursor(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                moveCursor(event.x, event.y)
                if (pointerDown && !isDragging) {
                    val dist = hypot(event.x - downX, event.y - downY)
                    if (dist > touchSlop) {
                        isDragging = true
                        val (x, y) = mapPoint(event.x, event.y)
                        inputSender.sendMouseEvent(PointF(x, y), InputStub.BUTTON_LEFT, true, false)
                    }
                } else if (isDragging) {
                    val (x, y) = mapPoint(event.x, event.y)
                    inputSender.sendMouseEvent(PointF(x, y), InputStub.BUTTON_LEFT, true, false)
                }
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val (x, y) = mapPoint(event.x, event.y)
                if (isDragging) {
                    inputSender.sendMouseEvent(PointF(x, y), InputStub.BUTTON_LEFT, false, false)
                } else {
                    clickAt(x, y, InputStub.BUTTON_LEFT)
                }
                pointerDown = false
                isDragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    val (x, y) = mapPoint(event.x, event.y)
                    inputSender.sendMouseEvent(PointF(x, y), InputStub.BUTTON_LEFT, false, false)
                }
                pointerDown = false
                isDragging = false
            }
        }

        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun mapPoint(viewX: Float, viewY: Float): Pair<Float, Float> {
        val x = (viewX / viewWidth * screenWidth).coerceIn(0f, screenWidth - 1f)
        val y = (viewY / viewHeight * screenHeight).coerceIn(0f, screenHeight - 1f)
        return x to y
    }

    private fun moveCursor(viewX: Float, viewY: Float) {
        val (x, y) = mapPoint(viewX, viewY)
        inputSender.sendCursorMove(x, y, false)
    }

    private fun clickAt(x: Float, y: Float, button: Int) {
        inputSender.sendMouseEvent(PointF(x, y), button, true, false)
        inputSender.sendMouseEvent(PointF(x, y), button, false, false)
    }
}
