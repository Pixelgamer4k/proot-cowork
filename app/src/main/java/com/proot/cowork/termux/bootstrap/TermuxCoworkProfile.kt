package com.proot.cowork.termux.bootstrap

import java.io.File

/** Shell snippets for embedded Termux (DISPLAY, proot-distro XFCE). */
object TermuxCoworkProfile {

    fun applyIfNeeded(prefix: File) {
        val marker = File(prefix, ".termux_cowork_profile_v3")
        if (marker.isFile) return

        File(prefix, ".termux_cowork_profile_v1").delete()
        File(prefix, ".termux_cowork_profile_v2").delete()
        val profileDir = File(prefix, "etc/profile.d").also { it.mkdirs() }
        File(profileDir, "cowork-env.sh").writeText(
            """
            |# Proot-Cowork embedded stack (1280x720 @ 60Hz X11 on :0)
            |export DISPLAY="${'$'}{DISPLAY:-:0}"
            |export COWORK_X11_WIDTH=1280
            |export COWORK_X11_HEIGHT=720
            |export COWORK_X11_FPS=60
            |[ -n "${'$'}PREFIX" ] && export PATH="${'$'}PREFIX/bin:${'$'}PATH"
            """.trimMargin(),
        )

        if (File(prefix, "bin/proot-distro").isFile) {
            File(profileDir, "cowork-proot-distro.sh").writeText(
                """
                |# Import Ubuntu + XFCE built in Termux (proot-distro backup .tar.gz)
                |#   proot-xfce-start ubuntu
                |alias pdl='proot-distro login'
                """.trimMargin(),
            )
        }

        marker.createNewFile()
    }
}
