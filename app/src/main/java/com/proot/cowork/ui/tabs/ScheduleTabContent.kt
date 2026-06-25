package com.proot.cowork.ui.tabs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.ui.design.CoworkTokens

private enum class ScheduleStatus { Pending, Done, Failed }
private data class ScheduleItem(val title: String, val whenLabel: String, val status: ScheduleStatus)

private val DEMO_SCHEDULE = listOf(
    ScheduleItem("Run system backup and sync to cloud storage", "2026-06-26 03:00", ScheduleStatus.Pending),
    ScheduleItem("Update all Python packages in venv", "2026-06-25 18:00", ScheduleStatus.Done),
    ScheduleItem("Generate weekly project status report", "2026-06-24 09:00", ScheduleStatus.Failed),
)

@Composable
fun ScheduleTabContent(onScheduleDraft: (String) -> Unit, modifier: Modifier = Modifier) {
    var draft by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.schedule_prompt), color = CoworkTokens.TextSecondary)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.schedule_placeholder), color = CoworkTokens.TextMuted) },
                    singleLine = true,
                    shape = CoworkTokens.ShapeCard,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoworkTokens.Border,
                        unfocusedBorderColor = CoworkTokens.Border,
                        focusedContainerColor = CoworkTokens.Surface,
                        unfocusedContainerColor = CoworkTokens.Surface,
                    ),
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowRight, null, tint = CoworkTokens.TextMuted) },
                )
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = { if (draft.isNotBlank()) { onScheduleDraft(draft.trim()); draft = "" } },
                    enabled = draft.isNotBlank(),
                    shape = CoworkTokens.ShapeCard,
                    colors = ButtonDefaults.buttonColors(containerColor = CoworkTokens.Mint, contentColor = CoworkTokens.SpeakFg),
                ) { Text(stringResource(R.string.schedule_action), fontWeight = FontWeight.SemiBold) }
            }
        }
        items(DEMO_SCHEDULE) { ScheduleRow(it) }
    }
}

@Composable
private fun ScheduleRow(item: ScheduleItem) {
    val (label, color) = when (item.status) {
        ScheduleStatus.Pending -> stringResource(R.string.status_pending) to CoworkTokens.Pending
        ScheduleStatus.Done -> stringResource(R.string.status_done) to CoworkTokens.Done
        ScheduleStatus.Failed -> stringResource(R.string.status_failed) to CoworkTokens.Failed
    }
    Surface(
        shape = CoworkTokens.ShapeCard,
        color = CoworkTokens.Surface,
        modifier = Modifier.fillMaxWidth().border(1.dp, CoworkTokens.Border, CoworkTokens.ShapeCard),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(item.title, Modifier.weight(1f).padding(end = 8.dp), color = CoworkTokens.TextPrimary, fontWeight = FontWeight.Medium)
                Surface(shape = CoworkTokens.ShapePill, color = color.copy(alpha = 0.18f)) {
                    Text(label, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), color = color, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, Modifier.size(14.dp), tint = CoworkTokens.TextMuted)
                Spacer(Modifier.size(6.dp))
                Text(item.whenLabel, color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
    }
}
