package com.proot.cowork.termux.bootstrap

import java.io.File

/** Shell snippets for embedded Termux (DISPLAY, proot-distro hints). */
object TermuxCoworkProfile {

    fun applyIfNeeded(prefix: File) {
        val marker = File(prefix, ".termux_cowork_profile_v1")
        if (marker.isFile) return

        val profileDir = File(prefix, "etc/profile.d").also { it.mkdirs() }
        File(profileDir, "cowork-env.sh").writeText(
            """
            |# Proot-Cowork embedded stack
            |export DISPLAY="${'$'}{DISPLAY:-:0}"
            |[ -n "${'$'}PREFIX" ] && export PATH="${'$'}PREFIX/bin:${'$'}PATH"
            """.trimMargin(),
        )

        val prootDistro = File(prefix, "bin/proot-distro")
        if (prootDistro.isFile) {
            File(profileDir, "cowork-proot-distro.sh").writeText(
                """
                |# proot-distro is preinstalled — e.g. proot-distro install ubuntu
                |# Use --shared-tmp when launching GUI apps on the embedded X11 display (:0).
                |alias pdl='proot-distro login'
                """.trimMargin(),
            )
        }

        marker.createNewFile()
    }
}
