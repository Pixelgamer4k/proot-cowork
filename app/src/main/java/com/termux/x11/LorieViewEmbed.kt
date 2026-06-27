package com.termux.x11

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import com.proot.cowork.termux.x11.X11DisplayConfig
import com.proot.cowork.termux.bootstrap.TermuxBootstrap
import com.proot.cowork.termux.bootstrap.TermuxX11Demo

/**
 * Safe LorieView setup when [MainActivity] is not running (in-process embed).
 * Works even if termux-x11 embed patches were not applied to the vendored sources.
 */
object LorieViewEmbed {
    private const val TAG = "LorieViewEmbed"

    fun attachCallback(lorieView: LorieView) {
        val callback = LorieView.Callback { _, _, screenWidth, screenHeight ->
            val rate = X11DisplayConfig.FPS
            LorieView.sendWindowChange(
                screenWidth.coerceAtLeast(X11DisplayConfig.WIDTH),
                screenHeight.coerceAtLeast(X11DisplayConfig.HEIGHT),
                rate,
                "builtin",
            )
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
        lorieView.isFocusableInTouchMode = false
        lorieView.requestFocus()
        lorieView.background = object : ColorDrawable(Color.TRANSPARENT) {
            override fun isStateful(): Boolean = true
            override fun hasFocusStateSpecified(): Boolean = true
        }
        lorieView.post {
            val rate = X11DisplayConfig.FPS
            LorieView.sendWindowChange(
                X11DisplayConfig.WIDTH,
                X11DisplayConfig.HEIGHT,
                rate,
                "builtin",
            )
        }
    }
}
