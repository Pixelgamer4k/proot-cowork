package com.proot.cowork.ui.terminal

import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.proot.cowork.R
import com.proot.cowork.domain.desktop.TermuxStackSession
import com.proot.cowork.termux.terminal.CoworkTerminalViewClient
import com.proot.cowork.termux.terminal.ProotGuestTerminalController
import com.proot.cowork.termux.terminal.TerminalKeyboard
import com.proot.cowork.ui.design.CoworkTokens
import com.termux.view.TerminalView

@Composable
fun EmbeddedProotTerminal(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bootstrapReady by TermuxStackSession.bootstrapReady.collectAsState()
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    var viewClient by remember { mutableStateOf<CoworkTerminalViewClient?>(null) }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    setBackgroundColor(Color.parseColor("#0D0D0D"))
                    TerminalKeyboard.setup(this)
                    val client = CoworkTerminalViewClient(this)
                    setTerminalViewClient(client)
                    terminalView = this
                    viewClient = client
                }
            },
            update = { view ->
                terminalView = view
                if (ProotGuestTerminalController.attach(view, context, viewClient)) {
                    if (!view.hasFocus()) {
                        TerminalKeyboard.focusAndShow(view)
                    }
                }
            },
        )

        TerminalExtraKeysBar(
            terminalView = terminalView,
            client = viewClient,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
