package com.proot.cowork.ui.tabs

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

private enum class ScheduleStatus { Pending, Done, Failed }

private data class ScheduleItem(
    val title: String,
    val whenLabel: String,
    val status: ScheduleStatus,
)

private val DEMO_SCHEDULE = listOf(
    ScheduleItem("Run system backup and sync to cloud storage", "2026-06-26 03:00", ScheduleStatus.Pending),
    ScheduleItem("Update all Python packages in venv", "2026-06-25 18:00", ScheduleStatus.Done),
    ScheduleItem("Generate weekly project status report", "2026-06-24 09:00", ScheduleStatus.Failed),
)

@Composable
fun ScheduleTabContent(
    onScheduleDraft: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.schedule_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.schedule_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Button(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onScheduleDraft(draft.trim())
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.schedule_action))
                }
            }
            Text(
                text = stringResource(R.string.coming_soon_schedule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        items(DEMO_SCHEDULE) { item ->
            ScheduleRow(item)
        }
    }
}

@Composable
private fun ScheduleRow(item: ScheduleItem) {
    val (label, color) = when (item.status) {
        ScheduleStatus.Pending -> stringResource(R.string.status_pending) to MaterialTheme.colorScheme.tertiary
        ScheduleStatus.Done -> stringResource(R.string.status_done) to MaterialTheme.colorScheme.primary
        ScheduleStatus.Failed -> stringResource(R.string.status_failed) to MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = item.title,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    fontWeight = FontWeight.Medium,
                )
                Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.18f)) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = item.whenLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
