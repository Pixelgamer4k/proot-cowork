package com.proot.cowork.ui.tabs

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.domain.schedule.ScheduleStatus
import com.proot.cowork.domain.schedule.ScheduledTask
import com.proot.cowork.ui.design.CoworkTokens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ScheduleTabContent(
    tasks: List<ScheduledTask>,
    isApiConfigured: Boolean,
    onSchedule: (String, Long) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var draft by rememberSaveable { mutableStateOf("") }
    var selectedTime by rememberSaveable { mutableStateOf(defaultScheduleTime()) }

    val timeLabel = rememberScheduleLabel(selectedTime)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.schedule_prompt), color = CoworkTokens.TextSecondary)
            if (!isApiConfigured) {
                Text(
                    stringResource(R.string.schedule_api_required),
                    color = CoworkTokens.Failed,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                placeholder = { Text(stringResource(R.string.schedule_placeholder), color = CoworkTokens.TextMuted) },
                minLines = 2,
                maxLines = 4,
                shape = CoworkTokens.ShapeCard,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CoworkTokens.Border,
                    unfocusedBorderColor = CoworkTokens.Border,
                    focusedContainerColor = CoworkTokens.Surface,
                    unfocusedContainerColor = CoworkTokens.Surface,
                ),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = selectedTime }
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                cal.set(Calendar.YEAR, year)
                                cal.set(Calendar.MONTH, month)
                                cal.set(Calendar.DAY_OF_MONTH, day)
                                selectedTime = cal.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH),
                        ).show()
                    },
                    shape = CoworkTokens.ShapeCard,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(timeLabel, maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = selectedTime }
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                cal.set(Calendar.HOUR_OF_DAY, hour)
                                cal.set(Calendar.MINUTE, minute)
                                cal.set(Calendar.SECOND, 0)
                                selectedTime = cal.timeInMillis
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true,
                        ).show()
                    },
                    shape = CoworkTokens.ShapeCard,
                ) {
                    Text(stringResource(R.string.schedule_pick_time))
                }
            }
            OutlinedButton(
                onClick = {
                    if (draft.isNotBlank() && isApiConfigured) {
                        onSchedule(draft.trim(), selectedTime)
                        draft = ""
                        selectedTime = defaultScheduleTime()
                    }
                },
                enabled = draft.isNotBlank() && isApiConfigured && selectedTime > System.currentTimeMillis(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = CoworkTokens.ShapeCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, CoworkTokens.Mint),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoworkTokens.Mint),
            ) {
                Text(stringResource(R.string.schedule_action), fontWeight = FontWeight.SemiBold)
            }
        }

        if (tasks.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.schedule_empty),
                    color = CoworkTokens.TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                ScheduleRow(task, onCancel = onCancel, onDelete = onDelete)
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    item: ScheduledTask,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val (label, color) = when (item.status) {
        ScheduleStatus.PENDING -> stringResource(R.string.status_pending) to CoworkTokens.Pending
        ScheduleStatus.RUNNING -> stringResource(R.string.status_running) to CoworkTokens.Mint
        ScheduleStatus.DONE -> stringResource(R.string.status_done) to CoworkTokens.Done
        ScheduleStatus.FAILED -> stringResource(R.string.status_failed) to CoworkTokens.Failed
        ScheduleStatus.CANCELLED -> stringResource(R.string.status_cancelled) to CoworkTokens.TextMuted
    }
    val whenLabel = rememberScheduleLabel(item.triggerAtMillis)
    Surface(
        shape = CoworkTokens.ShapeCard,
        color = CoworkTokens.Surface,
        modifier = Modifier.fillMaxWidth().border(1.dp, CoworkTokens.Border, CoworkTokens.ShapeCard),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(item.prompt, Modifier.weight(1f).padding(end = 8.dp), color = CoworkTokens.TextPrimary, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CoworkTokens.ShapePill, color = color.copy(alpha = 0.18f)) {
                        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), color = color, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    }
                    if (item.status == ScheduleStatus.PENDING) {
                        IconButton(onClick = { onCancel(item.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.schedule_cancel), tint = CoworkTokens.TextMuted)
                        }
                    } else {
                        IconButton(onClick = { onDelete(item.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.schedule_delete), tint = CoworkTokens.TextMuted)
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, Modifier.size(14.dp), tint = CoworkTokens.TextMuted)
                Spacer(Modifier.size(6.dp))
                Text(whenLabel, color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
            item.lastError?.let { error ->
                Text(error, Modifier.padding(top = 6.dp), color = CoworkTokens.Failed, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun rememberScheduleLabel(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(millis))
}

private fun defaultScheduleTime(): Long {
    val cal = Calendar.getInstance()
    cal.add(Calendar.HOUR_OF_DAY, 1)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
