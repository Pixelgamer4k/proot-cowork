package com.proot.cowork.ui.desktop

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.proot.cowork.domain.desktop.TermuxStackSession
import com.proot.cowork.termux.bootstrap.TermuxX11Demo
import com.proot.cowork.termux.x11.X11DisplayConfig
import com.proot.cowork.termux.terminal.TerminalKeyboard
import com.proot.cowork.termux.terminal.TermuxTerminalController
import com.termux.x11.X11EmbedController
import com.termux.x11.X11SurfaceHost
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun EmbeddedX11Surface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bootstrapReady by TermuxStackSession.bootstrapReady.collectAsState()
    var serverStarted by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            X11SurfaceHost(ctx).apply {
                setBackgroundColor(Color.BLACK)
            }
        },
        update = { host ->
            if (!bootstrapReady) return@AndroidView
            if (!serverStarted) {
                serverStarted = true
                scope.launch(Dispatchers.IO) {
                    if (X11EmbedController.ensureServer(
                            context,
                            X11DisplayConfig.WIDTH,
                            X11DisplayConfig.HEIGHT,
                        )
                    ) {
                        TermuxX11Demo.paintBackground(context)
                    }
                }
            }
            X11EmbedController.pollConnect(host.lorieView) {
                TermuxStackSession.setX11Ready(true)
            }
        },
    )
}

@Composable
fun EmbeddedTermuxSurface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bootstrapReady by TermuxStackSession.bootstrapReady.collectAsState()

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TerminalView(ctx, null).apply {
                setBackgroundColor(Color.parseColor("#000000"))
                TerminalKeyboard.setup(this)
            }
        },
        update = { view ->
            if (!bootstrapReady) return@AndroidView
            if (TermuxTerminalController.attach(view, context)) {
                TermuxStackSession.setTermuxReady(true)
                if (!view.hasFocus()) {
                    TerminalKeyboard.focusAndShow(view)
                }
            }
        },
    )
}
