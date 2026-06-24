package com.proot.cowork.termux.terminal

import android.content.Context
import com.proot.cowork.domain.desktop.TermuxStackSession
import com.proot.cowork.termux.bootstrap.TermuxBootstrap
import com.proot.cowork.termux.bootstrap.TermuxShellEnvironment
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import java.io.File
import java.io.FileInputStream

object TermuxTerminalController {

    private var session: TerminalSession? = null

    fun attach(terminalView: TerminalView, context: Context): Boolean {
        TerminalKeyboard.setup(terminalView)
        if (session?.isRunning == true) {
            ensureRenderer(terminalView)
            terminalView.setTerminalViewClient(CoworkTerminalViewClient(terminalView))
            terminalView.attachSession(session)
            return true
        }

        val login = TermuxBootstrap.loginExecutable(context)
        if (!login.canExecute()) return false

        ensureRenderer(terminalView)
        terminalView.setTerminalViewClient(CoworkTerminalViewClient(terminalView))

        if (terminalView.width > 0 && terminalView.height > 0) {
            startSession(terminalView, context, login)
            return session?.isRunning == true
        }

        terminalView.post { startSession(terminalView, context, login) }
        return false
    }

    private fun startSession(terminalView: TerminalView, context: Context, login: File) {
        if (session?.isRunning == true) return
        val home = TermuxBootstrap.homeDir(context).absolutePath
        val (shellPath, args) = loginProcessArgs(login)
        val client = CoworkTerminalSessionClient(terminalView)
        val newSession = TerminalSession(
            shellPath,
            home,
            args,
            TermuxShellEnvironment.build(context),
            10_000,
            client,
        )
        session = newSession
        terminalView.attachSession(newSession)
        if (newSession.isRunning) {
            TermuxStackSession.setTermuxReady(true)
            TerminalKeyboard.focusAndShow(terminalView)
        }
    }

    /**
     * Same behaviour as Termux [com.termux.shared.shell.TermuxShellUtils.setupProcessArgs]
     * for the `login` shell script.
     */
    private fun loginProcessArgs(login: File): Pair<String, Array<String>> {
        val prefixBin = login.parentFile ?: error("login has no parent")
        val interpreter = File(prefixBin, "sh")
        val shellPath = if (isElf(login)) {
            login.absolutePath
        } else {
            interpreter.absolutePath
        }
        val args = if (shellPath == interpreter.absolutePath) {
            arrayOf("-login", login.absolutePath)
        } else {
            arrayOf("-login")
        }
        return shellPath to args
    }

    private fun isElf(file: File): Boolean {
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(4)
                input.read(header) == 4 &&
                    header[0] == 0x7f.toByte() &&
                    header[1] == 'E'.code.toByte() &&
                    header[2] == 'L'.code.toByte() &&
                    header[3] == 'F'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureRenderer(terminalView: TerminalView) {
        if (terminalView.mRenderer == null) {
            terminalView.setTextSize(14)
        }
    }
}
