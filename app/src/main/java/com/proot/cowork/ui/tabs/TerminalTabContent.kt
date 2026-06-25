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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.proot.cowork.ui.design.CoworkTokens

private val DEMO_TERMINAL = listOf(
    "~ \$ ls -la",
    "drwxr-xr-x  projects",
    "drwxr-xr-x  documents",
    "-rw-r--r--  app.py",
    "-rw-r--r--  requirements.txt",
    "~ \$ python app.py",
    " * Running on http://127.0.0.1:5000",
    "~ \$ curl http://127.0.0.1:5000/api/tasks",
    "[{\"id\":1,\"title\":\"First task\",\"done\":false}]",
)

@Composable
fun TerminalTabContent(
    logLines: List<String>,
    onCommandSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var command by rememberSaveable { mutableStateOf("") }
    val lines = if (logLines.isEmpty()) DEMO_TERMINAL else logLines

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(CoworkTokens.Bg)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            reverseLayout = true,
        ) {
            items(lines.asReversed()) { line ->
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    color = terminalLineColor(line),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        }

        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = {
                Text(
                    stringResource(R.string.terminal_input_hint),
                    fontFamily = FontFamily.Monospace,
                    color = CoworkTokens.TextMuted,
                )
            },
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = CoworkTokens.TextPrimary,
            ),
            singleLine = true,
            shape = CoworkTokens.ShapePill,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CoworkTokens.Border,
                unfocusedBorderColor = CoworkTokens.Border,
                focusedContainerColor = CoworkTokens.Surface,
                unfocusedContainerColor = CoworkTokens.Surface,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (command.isNotBlank()) {
                    onCommandSubmit(command.trim())
                    command = ""
                }
            }),
        )
    }
}

private fun terminalLineColor(line: String): Color {
    val lower = line.lowercase()
    return when {
        lower.contains("error") || lower.contains("failed") -> CoworkTokens.Failed
        lower.contains("running") || line.trimStart().startsWith("[") -> CoworkTokens.Mint
        line.contains("~ \$") || line.contains("~$") -> CoworkTokens.TerminalPrompt
        else -> CoworkTokens.TextSecondary
    }
}
