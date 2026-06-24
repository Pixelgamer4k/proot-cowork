package com.proot.cowork.termux.terminal

import android.content.Context
import com.proot.cowork.termux.bootstrap.TermuxBootstrap
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

object TermuxTerminalController {

    private var session: TerminalSession? = null

    fun attach(terminalView: TerminalView, context: Context): Boolean {
        if (session?.isRunning == true) {
            terminalView.attachSession(session)
            return true
        }
        val bash = TermuxBootstrap.shellExecutable(context) ?: return false

        val home = TermuxBootstrap.prefixDir(context).resolve("home").absolutePath
        val client = CoworkTerminalSessionClient(terminalView)
        val newSession = TerminalSession(
            bash.absolutePath,
            home,
            arrayOf("-l"),
            TermuxBootstrap.shellEnvironment(context),
            10_000,
            client,
        )
        session = newSession
        terminalView.setTerminalViewClient(CoworkTerminalViewClient())
        terminalView.attachSession(newSession)
        return true
    }
}
