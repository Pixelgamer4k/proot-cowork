package com.proot.cowork.termux.terminal

import android.content.Context
import com.proot.cowork.data.prootcontainer.ProotContainerValidator
import com.proot.cowork.termux.bootstrap.CoworkAssetInstaller
import com.proot.cowork.termux.bootstrap.TermuxBootstrap
import com.proot.cowork.termux.bootstrap.TermuxShellEnvironment
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import java.io.File

/** Interactive PTY terminal logged into the Ubuntu proot guest. */
object ProotGuestTerminalController {

    private var session: TerminalSession? = null
    private var attachedView: TerminalView? = null
    private var attachedClient: CoworkTerminalViewClient? = null

    fun ensureAttached(
        terminalView: TerminalView,
        context: Context,
        client: CoworkTerminalViewClient?,
    ): Boolean {
        TerminalKeyboard.setupOnce(terminalView)
        val viewClient = client ?: CoworkTerminalViewClient(terminalView)

        if (session?.isRunning != true) {
            session = null
            attachedView = null
        }

        if (session?.isRunning == true && attachedView === terminalView) {
            return true
        }

        if (session?.isRunning == true) {
            bindView(terminalView, viewClient, session!!)
            return true
        }

        val distro = ProotContainerValidator.DEFAULT_DISTRO
        if (!ProotContainerValidator.isInstalled(context, distro)) return false

        val bash = TermuxBootstrap.shellExecutable(context) ?: return false
        ensureRenderer(terminalView)

        if (terminalView.width > 0 && terminalView.height > 0) {
            startSession(terminalView, context, bash, distro, viewClient)
            return session?.isRunning == true
        }

        terminalView.post { startSession(terminalView, context, bash, distro, viewClient) }
        return false
    }

    fun isSessionRunning(): Boolean = session?.isRunning == true

    fun onSessionFinished() {
        session = null
        attachedView = null
        attachedClient = null
    }

    fun restoreFocus(terminalView: TerminalView?) {
        val view = terminalView ?: attachedView ?: return
        if (session?.isRunning == true) {
            TerminalKeyboard.focusAndShow(view)
        }
    }

    private fun bindView(
        terminalView: TerminalView,
        client: CoworkTerminalViewClient,
        activeSession: TerminalSession,
    ) {
        ensureRenderer(terminalView)
        terminalView.setTerminalViewClient(client)
        if (terminalView.currentSession !== activeSession) {
            terminalView.attachSession(activeSession)
        }
        attachedView = terminalView
        attachedClient = client
    }

    private fun startSession(
        terminalView: TerminalView,
        context: Context,
        bash: File,
        distro: String,
        client: CoworkTerminalViewClient,
    ) {
        if (session?.isRunning == true) {
            bindView(terminalView, client, session!!)
            return
        }

        val prefix = TermuxBootstrap.prefixDir(context)
        CoworkAssetInstaller.installIfNeeded(context, prefix)
        val script = File(prefix, "share/cowork/proot-guest-login.sh")
        if (!script.isFile) return

        val home = TermuxBootstrap.homeDir(context).absolutePath
        val sessionClient = CoworkTerminalSessionClient(terminalView) {
            onSessionFinished()
        }
        val newSession = TerminalSession(
            bash.absolutePath,
            home,
            arrayOf(bash.absolutePath, script.absolutePath, distro),
            TermuxShellEnvironment.build(context),
            10_000,
            sessionClient,
        )
        session = newSession
        bindView(terminalView, client, newSession)
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
