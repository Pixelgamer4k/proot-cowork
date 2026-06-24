package com.proot.cowork.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.proot.cowork.R
import com.proot.cowork.domain.desktop.StackFrontLayer
import com.proot.cowork.domain.desktop.TermuxStackSession

/**
 * 16:9 stack: Termux terminal on top by default, Termux:X11 (LorieView) behind.
 * Toggle brings X11 forward; Termux keeps running underneath.
 */
@Composable
fun TermuxStackPanel(
    modifier: Modifier = Modifier,
    termuxLayer: @Composable (Modifier) -> Unit = { EmbeddedTermuxSurface(it) },
    x11Layer: @Composable (Modifier) -> Unit = { EmbeddedX11Surface(it) },
) {
    val frontLayer by TermuxStackSession.frontLayer.collectAsState()
    val x11Ready by TermuxStackSession.x11Ready.collectAsState()
    val termuxReady by TermuxStackSession.termuxReady.collectAsState()
    val bootstrapReady by TermuxStackSession.bootstrapReady.collectAsState()
    val logLines by TermuxStackSession.logLines.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        val x11Z = if (frontLayer == StackFrontLayer.X11) 1f else 0f
        val termuxZ = if (frontLayer == StackFrontLayer.TERMUX) 1f else 0f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(x11Z),
        ) {
            x11Layer(Modifier.fillMaxSize())
            if (!x11Ready) {
                LayerBootOverlay(stringResource(R.string.stack_x11_booting))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(termuxZ),
        ) {
            termuxLayer(Modifier.fillMaxSize())
            if (!termuxReady) {
                val status = logLines.lastOrNull()
                    ?: if (!bootstrapReady) stringResource(R.string.stack_termux_booting)
                    else stringResource(R.string.stack_termux_booting)
                LayerBootOverlay(status)
            }
        }

        FilledTonalButton(
            onClick = { TermuxStackSession.toggleFrontLayer() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .zIndex(2f),
        ) {
            Text(
                when (frontLayer) {
                    StackFrontLayer.TERMUX -> stringResource(R.string.stack_show_x11)
                    StackFrontLayer.X11 -> stringResource(R.string.stack_show_termux)
                },
            )
        }
    }
}

@Composable
private fun LayerBootOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = message,
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TermuxLayerPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.stack_termux_placeholder),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun X11LayerPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.stack_x11_placeholder),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
