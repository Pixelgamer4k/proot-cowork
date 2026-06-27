package com.proot.cowork.termux.x11

import com.proot.cowork.data.files.GuestPaths
import com.proot.cowork.data.proot.ProotGuestShellExecutor

/** Captures the X11 desktop inside the proot guest and saves to Desktop/Artifacts. */
object DesktopScreenshot {

    suspend fun captureToArtifacts(
        shell: ProotGuestShellExecutor,
        artifactsDir: String = GuestPaths.ARTIFACTS_DIR,
    ): Result<String> {
        val ts = System.currentTimeMillis()
        val guestPath = "${artifactsDir.trimEnd('/')}/screenshot_$ts.png"
        val quotedPath = shellQuote(guestPath)
        val quotedDir = shellQuote(artifactsDir)
        val cmd = """
            mkdir -p $quotedDir && export DISPLAY=:0 && (
              scrot -o $quotedPath 2>/dev/null ||
              maim -u $quotedPath 2>/dev/null ||
              import -window root $quotedPath 2>/dev/null
            ) && test -f $quotedPath && echo OK:$guestPath || echo FAIL
        """.trimIndent()
        val result = shell.run(cmd, timeoutMs = 45_000L)
        if (!result.success) {
            return Result.failure(Exception(result.error ?: result.output.ifBlank { "Screenshot command failed" }))
        }
        val line = result.output.lines().lastOrNull { it.startsWith("OK:") }
            ?: return Result.failure(Exception("Screenshot tools unavailable in guest (install scrot/maim/imagemagick)"))
        return Result.success(line.removePrefix("OK:"))
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
