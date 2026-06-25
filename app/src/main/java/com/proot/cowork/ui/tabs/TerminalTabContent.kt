package com.proot.cowork.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.proot.cowork.R

@Composable
fun TerminalTabContent(
    logLines: List<String>,
    onCommandSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var command by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            reverseLayout = true,
        ) {
            if (logLines.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.terminal_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            } else {
                items(logLines.asReversed()) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = terminalLineColor(line),
                    )
                }
            }
        }

        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = {
                Text(
                    stringResource(R.string.terminal_input_hint),
                    fontFamily = FontFamily.Monospace,
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (command.isNotBlank()) {
                        onCommandSubmit(command.trim())
                        command = ""
                    }
                },
            ),
        )
        Text(
            text = stringResource(R.string.coming_soon_terminal),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, bottom = 4.dp),
        )
    }
}

private fun terminalLineColor(line: String): Color {
    val lower = line.lowercase()
    return when {
        lower.contains("error") || lower.contains("failed") -> Color(0xFFFF6B6B)
        lower.contains("ready") || lower.contains("running") -> Color(0xFF4FD1C5)
        line.trimStart().startsWith("$") || line.contains("~ $") -> Color(0xFF94A3B8)
        else -> Color(0xFFE2E8F0)
    }
}
