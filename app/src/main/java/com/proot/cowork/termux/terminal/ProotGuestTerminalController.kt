package com.proot.cowork.termux.terminal

import android.content.Context
import com.proot.cowork.data.prootcontainer.ProotContainerValidator
import com.proot.cowork.termux.bootstrap.TermuxBootstrap
import com.proot.cowork.termux.bootstrap.TermuxShellEnvironment
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import java.io.File

/** Interactive PTY terminal logged into the Ubuntu proot guest. */
object ProotGuestTerminalController {

    private var session: TerminalSession? = null

    fun attach(terminalView: TerminalView, context: Context): Boolean {
        TerminalKeyboard.setup(terminalView)
        if (session?.isRunning == true) {
            ensureRenderer(terminalView)
            terminalView.setTerminalViewClient(CoworkTerminalViewClient(terminalView))
            terminalView.attachSession(session)
            return true
        }

        val distro = ProotContainerValidator.DEFAULT_DISTRO
        if (!ProotContainerValidator.isInstalled(context, distro)) return false

        val bash = TermuxBootstrap.shellExecutable(context) ?: return false
        ensureRenderer(terminalView)
        terminalView.setTerminalViewClient(CoworkTerminalViewClient(terminalView))

        if (terminalView.width > 0 && terminalView.height > 0) {
            startSession(terminalView, context, bash, distro)
            return session?.isRunning == true
        }

        terminalView.post { startSession(terminalView, context, bash, distro) }
        return false
    }

    private fun startSession(
        terminalView: TerminalView,
        context: Context,
        bash: File,
        distro: String,
    ) {
        if (session?.isRunning == true) return
        val home = TermuxBootstrap.homeDir(context).absolutePath
        val loginCmd = "proot-distro login $distro --shared-tmp -- bash -l"
        val client = CoworkTerminalSessionClient(terminalView)
        val newSession = TerminalSession(
            bash.absolutePath,
            home,
            arrayOf("-c", loginCmd),
            TermuxShellEnvironment.build(context),
            10_000,
            client,
        )
        session = newSession
        terminalView.attachSession(newSession)
        if (newSession.isRunning) {
            TerminalKeyboard.focusAndShow(terminalView)
        }
    }

    private fun ensureRenderer(terminalView: TerminalView) {
        if (terminalView.mRenderer == null) {
            terminalView.setTextSize(14)
        }
    }
}
