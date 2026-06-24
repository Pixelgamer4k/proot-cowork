package com.termux.x11

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log

/**
 * Safe LorieView setup when [MainActivity] is not running (in-process embed).
 * Works even if termux-x11 embed patches were not applied to the vendored sources.
 */
object LorieViewEmbed {
    private const val TAG = "LorieViewEmbed"

    fun attachCallback(lorieView: LorieView) {
        val callback = LorieView.Callback { _, _, screenWidth, screenHeight ->
            val rate = lorieView.display?.refreshRate?.toInt() ?: 60
            LorieView.sendWindowChange(screenWidth, screenHeight, rate, "builtin")
        }
        if (!assignCallback(lorieView, callback)) {
            try {
                lorieView.setCallback(callback)
            } catch (e: Exception) {
                Log.e(TAG, "setCallback failed", e)
                assignCallback(lorieView, callback)
                safeTrigger(lorieView)
            }
        } else {
            safeTrigger(lorieView)
        }
    }

    fun safeTrigger(lorieView: LorieView) {
        try {
            lorieView.triggerCallback()
        } catch (e: NullPointerException) {
            Log.w(TAG, "triggerCallback needs embed fallback: ${e.message}")
            applyEmbedSetup(lorieView)
        }
    }

    private fun assignCallback(lorieView: LorieView, callback: LorieView.Callback): Boolean {
        return try {
            val field = LorieView::class.java.getDeclaredField("mCallback")
            field.isAccessible = true
            field.set(lorieView, callback)
            true
        } catch (e: Exception) {
            Log.w(TAG, "could not assign mCallback via reflection", e)
            false
        }
    }

    private fun applyEmbedSetup(lorieView: LorieView) {
        lorieView.isFocusable = true
        lorieView.isFocusableInTouchMode = true
        lorieView.requestFocus()
        lorieView.background = object : ColorDrawable(Color.TRANSPARENT) {
            override fun isStateful(): Boolean = true
            override fun hasFocusStateSpecified(): Boolean = true
        }
        lorieView.post {
            val rate = lorieView.display?.refreshRate?.toInt() ?: 60
            val w = lorieView.width.coerceAtLeast(1)
            val h = lorieView.height.coerceAtLeast(1)
            LorieView.sendWindowChange(w, h, rate, "builtin")
        }
    }
}
