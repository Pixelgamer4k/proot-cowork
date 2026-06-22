package com.proot.cowork.ui.desktop

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import com.proot.cowork.domain.vnc.VncSession

private const val BUTTON_LEFT = 1
private const val BUTTON_RIGHT = 4

@Composable
fun VncDesktopView(modifier: Modifier = Modifier) {
    val frame by VncSession.frame.collectAsState()
    val connected by VncSession.connected.collectAsState()
    val error by VncSession.error.collectAsState()

    DisposableEffect(Unit) {
        VncSession.connect()
        onDispose { VncSession.disconnect() }
    }

    var viewWidth by remember { mutableIntStateOf(1) }
    var viewHeight by remember { mutableIntStateOf(1) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                viewWidth = size.width.coerceAtLeast(1)
                viewHeight = size.height.coerceAtLeast(1)
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            frame != null -> {
                val bitmap = frame!!
                VncFrame(
                    bitmap = bitmap,
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                )
            }
            error != null -> {
                Text(
                    text = error ?: "VNC error",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> {
                CircularProgressIndicator()
                Text(
                    text = if (connected) "Waiting for frame…" else "Connecting to VNC…",
                    modifier = Modifier.align(Alignment.BottomCenter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VncFrame(
    bitmap: Bitmap,
    viewWidth: Int,
    viewHeight: Int,
) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Linux desktop",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(bitmap.width, bitmap.height, viewWidth, viewHeight) {
                detectTapGestures { offset ->
                    val (x, y) = mapPoint(offset.x, offset.y, bitmap, viewWidth, viewHeight)
                    VncSession.sendPointer(x, y, BUTTON_LEFT)
                    VncSession.sendPointer(x, y, 0)
                }
            }
            .pointerInput(bitmap.width, bitmap.height, viewWidth, viewHeight) {
                var dragging = false
                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        val (x, y) = mapPoint(offset.x, offset.y, bitmap, viewWidth, viewHeight)
                        VncSession.sendPointer(x, y, BUTTON_LEFT)
                    },
                    onDragEnd = {
                        dragging = false
                        VncSession.sendPointer(0, 0, 0)
                    },
                    onDragCancel = {
                        dragging = false
                        VncSession.sendPointer(0, 0, 0)
                    },
                ) { change, _ ->
                    if (dragging) {
                        val (x, y) = mapPoint(change.position.x, change.position.y, bitmap, viewWidth, viewHeight)
                        VncSession.sendPointer(x, y, BUTTON_LEFT)
                    }
                }
            },
    )
}

private fun mapPoint(
    touchX: Float,
    touchY: Float,
    bitmap: Bitmap,
    viewWidth: Int,
    viewHeight: Int,
): Pair<Int, Int> {
    val scale = minOf(
        viewWidth.toFloat() / bitmap.width.toFloat(),
        viewHeight.toFloat() / bitmap.height.toFloat(),
    )
    val drawnW = bitmap.width * scale
    val drawnH = bitmap.height * scale
    val offsetX = (viewWidth - drawnW) / 2f
    val offsetY = (viewHeight - drawnH) / 2f
    val x = ((touchX - offsetX) / scale).toInt().coerceIn(0, bitmap.width - 1)
    val y = ((touchY - offsetY) / scale).toInt().coerceIn(0, bitmap.height - 1)
    return x to y
}
