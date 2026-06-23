package com.proot.cowork.data.vnc

import com.proot.cowork.userland.UserlandConfig

object VncConfig {
    const val HOST = UserlandConfig.VNC_HOST
    const val PORT = UserlandConfig.VNC_PORT
    const val PASSWORD = UserlandConfig.DEFAULT_VNC_PASSWORD
    const val CONNECT_TIMEOUT_MS = 5_000
    const val BOOT_TIMEOUT_MS = 180_000L
    const val POLL_INTERVAL_MS = 750L
}
