package com.proot.cowork.ui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proot.cowork.ui.design.CoworkTokens

private val DesktopWallpaperTop = Color(0xFF1E3A5F)
private val DesktopWallpaperBottom = Color(0xFF0F172A)
private val GridDot = Color(0xFF334155).copy(alpha = 0.35f)

/**
 * Static XFCE-style desktop illustration matching the Cowork reference mocks.
 * Shown whenever live X11 is unavailable so every tab keeps the same chrome.
 */
@Composable
fun DesktopReferenceMock(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(DesktopWallpaperTop, DesktopWallpaperBottom)),
            ),
    ) {
        PlusGridPattern()

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 36.dp),
        ) {
            Column(
                modifier = Modifier.width(52.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DesktopShortcut(Icons.Default.Storage, "File\nSystem")
                DesktopShortcut(Icons.Default.Home, "Home")
                DesktopShortcut(Icons.Default.Settings, "Settings")
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(CoworkTokens.Mint.copy(alpha = 0.14f))
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = CoworkTokens.Mint,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
        }

        DesktopDock(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun PlusGridPattern() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val step = 18.dp.toPx()
        var y = step
        while (y < size.height) {
            var x = step
            while (x < size.width) {
                drawCircle(color = GridDot, radius = 1.2f, center = Offset(x, y))
                drawLine(
                    color = GridDot,
                    start = Offset(x - 3f, y),
                    end = Offset(x + 3f, y),
                    strokeWidth = 1f,
                )
                drawLine(
                    color = GridDot,
                    start = Offset(x, y - 3f),
                    end = Offset(x, y + 3f),
                    strokeWidth = 1f,
                )
                x += step
            }
            y += step
        }
    }
}

@Composable
private fun DesktopShortcut(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color(0xFFE2E8F0), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = Color(0xFFCBD5E1),
            fontSize = 8.sp,
            lineHeight = 9.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DesktopDock(modifier: Modifier = Modifier) {
    val icons = listOf(
        Icons.Default.Computer,
        Icons.Default.Terminal,
        Icons.Default.Folder,
        Icons.Default.Language,
        Icons.Default.Search,
        Icons.Default.Description,
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icons.forEach { icon ->
            Icon(icon, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
        }
    }
}
