package com.proot.cowork.termux.terminal

import android.content.Context
import com.proot.cowork.domain.desktop.TermuxStackSession
import com.proot.cowork.termux.bootstrap.TermuxBootstrap
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import java.io.File

object TermuxTerminalController {

    private var session: TerminalSession? = null

    fun attach(terminalView: TerminalView, context: Context): Boolean {
        if (session?.isRunning == true) {
            ensureRenderer(terminalView)
            terminalView.setTerminalViewClient(CoworkTerminalViewClient())
            terminalView.attachSession(session)
            return true
        }

        val bash = TermuxBootstrap.shellExecutable(context) ?: return false
        ensureRenderer(terminalView)
        terminalView.setTerminalViewClient(CoworkTerminalViewClient())

        if (terminalView.width > 0 && terminalView.height > 0) {
            startSession(terminalView, context, bash)
            return session?.isRunning == true
        }

        terminalView.post { startSession(terminalView, context, bash) }
        return false
    }

    private fun startSession(terminalView: TerminalView, context: Context, bash: File) {
        if (session?.isRunning == true) return
        val home = TermuxBootstrap.prefixDir(context).resolve("home").absolutePath
        val client = CoworkTerminalSessionClient(terminalView)
        val newSession = TerminalSession(
            bash.absolutePath,
            home,
            // Leading "-" marks a login shell (same convention as Termux).
            arrayOf("-bash"),
            TermuxBootstrap.shellEnvironment(context),
            10_000,
            client,
        )
        session = newSession
        terminalView.attachSession(newSession)
        if (newSession.isRunning) {
            TermuxStackSession.setTermuxReady(true)
        }
    }

    private fun ensureRenderer(terminalView: TerminalView) {
        if (terminalView.mRenderer == null) {
            // Termux default font size (dp); required before attachSession/updateSize.
            terminalView.setTextSize(14)
        }
    }
}
