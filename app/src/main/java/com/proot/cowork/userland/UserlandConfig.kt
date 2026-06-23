package com.proot.cowork.userland

object UserlandConfig {
    /** UserLAnd stores each filesystem at filesDir/<id>/ */
    const val FILESYSTEM_ID = 1L
    const val FILESYSTEM_DIR = "1"

    const val VNC_HOST = "127.0.0.1"
    const val VNC_DISPLAY = 51
    const val VNC_PORT = 5900 + VNC_DISPLAY // 5951

    const val DEFAULT_USERNAME = "cowork"
    const val DEFAULT_VNC_PASSWORD = "userland"
    const val DEFAULT_GEOMETRY = "1280x720"
}
