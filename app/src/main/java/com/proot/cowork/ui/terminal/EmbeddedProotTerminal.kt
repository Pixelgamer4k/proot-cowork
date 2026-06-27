package com.proot.cowork.ui.terminal

import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import com.proot.cowork.R
import com.proot.cowork.domain.desktop.TermuxStackSession
import com.proot.cowork.termux.terminal.CoworkTerminalViewClient
import com.proot.cowork.termux.terminal.ProotGuestTerminalController
import com.proot.cowork.termux.terminal.TerminalKeyboard
import com.proot.cowork.ui.design.CoworkTokens
import com.termux.view.TerminalView
import com.termux.x11.X11SurfaceHostRegistry

private class TerminalSurfaceHolder {
    var terminalView: TerminalView? = null
    var viewClient: CoworkTerminalViewClient? = null
}

@Composable
fun EmbeddedProotTerminal(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bootstrapReady by TermuxStackSession.bootstrapReady.collectAsState()
    val x11Ready by TermuxStackSession.x11Ready.collectAsState()
    val sessionRunning by ProotGuestTerminalController.sessionRunning.collectAsState()
    val holder = remember { TerminalSurfaceHolder() }
    var barEpoch by remember { mutableIntStateOf(0) }

    if (!bootstrapReady) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.terminal_bootstrapping),
                color = CoworkTokens.TextMuted,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    DisposableEffect(isActive) {
        if (isActive) {
            X11SurfaceHostRegistry.current?.hideDesktopKeyboard()
        }
        onDispose { }
    }

    LaunchedEffect(isActive, x11Ready, barEpoch) {
        if (!isActive) return@LaunchedEffect
        X11SurfaceHostRegistry.current?.hideDesktopKeyboard()
        val view = holder.terminalView ?: return@LaunchedEffect
        ProotGuestTerminalController.ensureAttached(view, context, holder.viewClient)
        ProotGuestTerminalController.restoreFocus(view)
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        if (!sessionRunning && barEpoch > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Column {
                    Text(
                        stringResource(R.string.terminal_session_ended),
                        color = CoworkTokens.Failed,
                    )
                    Button(
                        onClick = {
                            holder.terminalView?.let { view ->
                                ProotGuestTerminalController.ensureAttached(view, context, holder.viewClient)
                                ProotGuestTerminalController.restoreFocus(view)
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.terminal_reconnect))
                    }
                }
            }
        }

        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    setBackgroundColor(Color.parseColor("#0D0D0D"))
                    TerminalKeyboard.setupOnce(this)
                    val client = CoworkTerminalViewClient(this)
                    setTerminalViewClient(client)
                    holder.terminalView = this
                    holder.viewClient = client
                    barEpoch++
                    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                        v.post {
                            (v as? TerminalView)?.onScreenUpdated()
                            v.invalidate()
                        }
                        insets
                    }
                    ProotGuestTerminalController.ensureAttached(this, context, client)
                }
            },
            update = { view ->
                if (isActive) {
                    X11SurfaceHostRegistry.current?.hideDesktopKeyboard()
                }
                if (!ProotGuestTerminalController.isSessionRunning()) {
                    ProotGuestTerminalController.ensureAttached(view, context, holder.viewClient)
                }
            },
        )

        if (barEpoch > 0) {
            TerminalExtraKeysBar(
                terminalView = holder.terminalView,
                client = holder.viewClient,
                onBeforeSend = {
                    holder.terminalView?.let { tv ->
                        ProotGuestTerminalController.ensureAttached(tv, context, holder.viewClient)
                    }
                },
                modifier = Modifier.fillMaxWidth().imePadding(),
            )
        }
    }
}
