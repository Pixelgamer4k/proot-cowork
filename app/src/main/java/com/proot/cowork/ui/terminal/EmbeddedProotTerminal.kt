package com.proot.cowork.ui.terminal

import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.proot.cowork.R
import com.proot.cowork.domain.desktop.TermuxStackSession
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

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            TerminalView(ctx, null).apply {
                setBackgroundColor(Color.parseColor("#0D0D0D"))
                TerminalKeyboard.setup(this)
            }
        },
        update = { view ->
            if (ProotGuestTerminalController.attach(view, context)) {
                if (!view.hasFocus()) {
                    TerminalKeyboard.focusAndShow(view)
                }
            }
        },
    )
}
