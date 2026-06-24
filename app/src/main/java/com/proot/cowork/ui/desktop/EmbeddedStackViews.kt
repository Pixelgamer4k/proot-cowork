package com.proot.cowork.ui.desktop

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.proot.cowork.domain.desktop.TermuxStackSession
import com.proot.cowork.termux.terminal.TermuxTerminalController
import com.termux.x11.X11EmbedController
import com.termux.x11.X11SurfaceHost
import com.termux.view.TerminalView

@Composable
fun EmbeddedX11Surface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (widthPx, heightPx) = remember(context) {
        val w = context.resources.displayMetrics.widthPixels.coerceAtLeast(640)
        w to (w * 9 / 16).coerceAtLeast(360)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            X11SurfaceHost(ctx).apply {
                setBackgroundColor(Color.BLACK)
                X11EmbedController.ensureServer(ctx, widthPx, heightPx)
                X11EmbedController.pollConnect(lorieView) {
                    TermuxStackSession.setX11Ready(true)
                }
            }
        },
    )
}

@Composable
fun EmbeddedTermuxSurface(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TerminalView(ctx, null).apply {
                setBackgroundColor(Color.parseColor("#000000"))
                if (TermuxTerminalController.attach(this, ctx)) {
                    TermuxStackSession.setTermuxReady(true)
                }
            }
        },
        update = { view ->
            if (TermuxTerminalController.attach(view, context)) {
                TermuxStackSession.setTermuxReady(true)
            }
        },
    )
}
