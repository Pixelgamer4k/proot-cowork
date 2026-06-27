package com.proot.cowork.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proot.cowork.termux.terminal.CoworkTerminalViewClient
import com.proot.cowork.termux.terminal.TerminalKeyInjector
import com.proot.cowork.ui.design.CoworkTokens
import com.termux.view.TerminalView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Termux-style extra keys bar (ESC, arrows, CTRL, TAB, etc.) anchored above the soft keyboard. */
@Composable
fun TerminalExtraKeysBar(
    terminalView: TerminalView?,
    client: CoworkTerminalViewClient?,
    onBeforeSend: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (terminalView == null || client == null) return

    var expanded by remember { mutableStateOf(true) }
    var modifierTick by remember { mutableIntStateOf(0) }
    modifierTick // force recomposition when CTRL/ALT toggles

    fun send(key: String) {
        onBeforeSend()
        TerminalKeyInjector.sendKey(terminalView, client, key)
        if (key == "CTRL" || key == "ALT" || key == "SHIFT") {
            modifierTick++
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CoworkTokens.Bg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ExtraKeyButton(
                label = TerminalKeyInjector.labelFor("ESC"),
                latched = false,
                modifier = Modifier.weight(1f),
                onPress = { send("ESC") },
                onRepeat = { send("ESC") },
            )
            ExtraKeyButton(
                label = "/",
                latched = false,
                modifier = Modifier.weight(1f),
                onPress = { send("/") },
                onRepeat = { send("/") },
            )
            ExtraKeyButton(
                label = TerminalKeyInjector.labelFor("-"),
                latched = false,
                modifier = Modifier.weight(1f),
                onPress = { send("-") },
                onRepeat = { send( "-") },
            )
            ExtraKeyButton(
                label = TerminalKeyInjector.labelFor("HOME"),
                latched = false,
                modifier = Modifier.weight(1f),
                onPress = { send( "HOME") },
                onRepeat = { send( "HOME") },
            )
            ExtraKeyButton(
                label = TerminalKeyInjector.labelFor("UP"),
                latched = false,
                modifier = Modifier.weight(1f),
                onPress = { send( "UP") },
                onRepeat = { send( "UP") },
            )
            ExtraKeyButton(
                label = TerminalKeyInjector.labelFor("END"),
                latched = false,
                modifier = Modifier.weight(1f),
                onPress = { send( "END") },
                onRepeat = { send( "END") },
            )
            ExtraKeyButton(
                label = TerminalKeyInjector.labelFor("PGUP"),
                latched = false,
                modifier = Modifier.weight(1f),
                onPress = { send( "PGUP") },
                onRepeat = { send( "PGUP") },
            )
            ExtraKeyButton(
                label = if (expanded) "⇕" else "⇳",
                latched = false,
                modifier = Modifier.weight(1f),
                onPress = { expanded = !expanded },
                onRepeat = { },
            )
        }

        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ExtraKeyButton(
                    label = TerminalKeyInjector.labelFor("TAB"),
                    latched = false,
                    modifier = Modifier.weight(1f),
                    onPress = { send( "TAB") },
                    onRepeat = { send( "TAB") },
                )
                ExtraKeyButton(
                    label = "CTRL",
                    latched = client.ctrlLatched,
                    modifier = Modifier.weight(1f),
                    onPress = { send( "CTRL") },
                    onRepeat = { },
                )
                ExtraKeyButton(
                    label = "ALT",
                    latched = client.altLatched,
                    modifier = Modifier.weight(1f),
                    onPress = { send( "ALT") },
                    onRepeat = { },
                )
                ExtraKeyButton(
                    label = TerminalKeyInjector.labelFor("LEFT"),
                    latched = false,
                    modifier = Modifier.weight(1f),
                    onPress = { send( "LEFT") },
                    onRepeat = { send( "LEFT") },
                )
                ExtraKeyButton(
                    label = TerminalKeyInjector.labelFor("DOWN"),
                    latched = false,
                    modifier = Modifier.weight(1f),
                    onPress = { send( "DOWN") },
                    onRepeat = { send( "DOWN") },
                )
                ExtraKeyButton(
                    label = TerminalKeyInjector.labelFor("RIGHT"),
                    latched = false,
                    modifier = Modifier.weight(1f),
                    onPress = { send( "RIGHT") },
                    onRepeat = { send( "RIGHT") },
                )
                ExtraKeyButton(
                    label = TerminalKeyInjector.labelFor("PGDN"),
                    latched = false,
                    modifier = Modifier.weight(1f),
                    onPress = { send( "PGDN") },
                    onRepeat = { send( "PGDN") },
                )
                ExtraKeyButton(
                    label = TerminalKeyInjector.labelFor("KEYBOARD"),
                    latched = false,
                    modifier = Modifier.weight(1f),
                    onPress = { send( "KEYBOARD") },
                    onRepeat = { },
                )
            }
        }
    }
}

@Composable
private fun ExtraKeyButton(
    label: String,
    latched: Boolean,
    onPress: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var repeatJob by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier = modifier
            .height(28.dp)
            .background(
                if (latched) CoworkTokens.Mint.copy(alpha = 0.25f)
                else CoworkTokens.SurfaceElevated,
            )
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        repeatJob?.cancel()
                        repeatJob = scope.launch {
                            delay(400)
                            while (isActive) {
                                onRepeat()
                                delay(80)
                            }
                        }
                        tryAwaitRelease()
                        repeatJob?.cancel()
                        repeatJob = null
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (latched) CoworkTokens.Mint else CoworkTokens.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
