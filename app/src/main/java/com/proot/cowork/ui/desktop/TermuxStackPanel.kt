package com.proot.cowork.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.domain.desktop.TermuxStackSession
import com.proot.cowork.domain.proot.DesktopState

/**
 * Embedded X11 desktop surface. Terminal runs headless in the background for proot/XFCE only.
 */
@Composable
fun TermuxStackPanel(
    modifier: Modifier = Modifier,
    desktopState: DesktopState = DesktopState.RUNNING,
    desktopInputEnabled: Boolean = true,
    x11Layer: @Composable (Modifier, Boolean) -> Unit = { mod, enabled ->
        EmbeddedX11Surface(modifier = mod, inputEnabled = enabled)
    },
) {
    val x11Ready by TermuxStackSession.x11Ready.collectAsState()
    val bootstrapReady by TermuxStackSession.bootstrapReady.collectAsState()
    val logLines by TermuxStackSession.logLines.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        x11Layer(Modifier.fillMaxSize(), desktopInputEnabled)

        val showOverlay = !x11Ready || desktopState == DesktopState.STARTING
        if (showOverlay) {
            val message = when {
                desktopState == DesktopState.STARTING ->
                    logLines.lastOrNull { it.contains("XFCE", ignoreCase = true) }
                        ?: stringResource(R.string.desktop_starting)
                !bootstrapReady -> stringResource(R.string.stack_termux_booting)
                else -> stringResource(R.string.stack_x11_booting)
            }
            LayerBootOverlay(message)
        }
    }
}

@Composable
private fun LayerBootOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = message,
                modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
