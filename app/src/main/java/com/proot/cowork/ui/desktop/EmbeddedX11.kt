package com.proot.cowork.ui.desktop

import android.content.Context
import com.termux.x11.MainActivity
import com.termux.x11.Prefs

object EmbeddedX11 {
    fun ensurePrefs(context: Context) {
        if (MainActivity.prefs == null) {
            MainActivity.prefs = Prefs(context.applicationContext)
        }
    }
}
