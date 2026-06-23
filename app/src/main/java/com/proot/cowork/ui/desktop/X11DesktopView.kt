package com.proot.cowork.ui.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.proot.cowork.data.x11.X11ConnectionManager
import com.termux.x11.LorieView

@Composable
fun X11DesktopView(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
  DisposableEffect(Unit) {
        onDispose { X11ConnectionManager.detach(context) }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LorieView(ctx).also { view ->
                X11ConnectionManager.attachLorieView(ctx, view)
            }
        },
    )
}
