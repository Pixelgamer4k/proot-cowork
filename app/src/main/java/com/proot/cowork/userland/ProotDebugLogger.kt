package com.proot.cowork.userland

import android.content.SharedPreferences

class ProotDebugLogger(
    private val prefs: SharedPreferences,
    private val files: UserlandFiles,
) {
    val isEnabled: Boolean
        get() = prefs.getBoolean("proot_debug_enabled", false)

    val verbosityLevel: String
        get() = prefs.getString("proot_debug_level", "1") ?: "1"
}
