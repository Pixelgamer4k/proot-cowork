package com.proot.cowork.ui.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.ui.design.CoworkTokens
import com.proot.cowork.ui.terminal.EmbeddedProotTerminal

@Composable
fun TerminalTabContent(
    containerInstalled: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!containerInstalled) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.terminal_container_required),
                color = CoworkTokens.TextMuted,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    EmbeddedProotTerminal(isActive = isActive, modifier = modifier)
}
