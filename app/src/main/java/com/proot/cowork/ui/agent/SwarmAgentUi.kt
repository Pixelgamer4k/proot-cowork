package com.proot.cowork.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.domain.agent.AgentMessage
import com.proot.cowork.domain.agent.SwarmTask
import com.proot.cowork.domain.agent.TaskPlan
import com.proot.cowork.domain.agent.TaskStatus
import com.proot.cowork.domain.agent.ToolCallStatus
import com.proot.cowork.ui.design.CoworkTokens

@Composable
fun ToolMessageBubble(message: AgentMessage, modifier: Modifier = Modifier) {
    val status = message.toolStatus ?: ToolCallStatus.COMPLETED
    val borderColor = when (status) {
        ToolCallStatus.RUNNING -> CoworkTokens.Mint.copy(alpha = 0.6f)
        ToolCallStatus.FAILED -> CoworkTokens.Failed.copy(alpha = 0.6f)
        ToolCallStatus.COMPLETED -> CoworkTokens.Border
        ToolCallStatus.REQUESTED -> CoworkTokens.Border
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CoworkTokens.SurfaceElevated,
    ) {
        Column(
            Modifier
                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status == ToolCallStatus.RUNNING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = CoworkTokens.Mint,
                    )
                } else {
                    Icon(Icons.Default.Build, null, tint = CoworkTokens.Mint, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = buildString {
                        message.agentName?.let { append("$it · ") }
                        append(message.toolName ?: "tool")
                    },
                    color = CoworkTokens.Mint,
                    fontWeight = FontWeight.SemiBold,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
            }
            if (message.content.isNotBlank()) {
                Spacer(Modifier.size(6.dp))
                Text(
                    text = message.content.take(1200),
                    color = CoworkTokens.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun SwarmApprovalCard(
    plan: TaskPlan,
    tasks: List<SwarmTask>,
    onUpdateTask: (String, String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CoworkTokens.ShapeCard,
        color = CoworkTokens.Mint.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, CoworkTokens.Mint.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.swarm_plan_title),
                color = CoworkTokens.Mint,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(4.dp))
            Text(plan.summary, color = CoworkTokens.TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            Spacer(Modifier.size(10.dp))
            EditableSwarmTaskTree(tasks = tasks, editable = true, onUpdateTask = onUpdateTask)
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.swarm_tap_execute_hint),
                color = CoworkTokens.TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    shape = CoworkTokens.ShapeCard,
                    colors = ButtonDefaults.buttonColors(containerColor = CoworkTokens.Mint, contentColor = CoworkTokens.SpeakFg),
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.swarm_execute), fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    shape = CoworkTokens.ShapeCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CoworkTokens.Border),
                ) {
                    Icon(Icons.Default.Close, null, tint = CoworkTokens.TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.cancel), color = CoworkTokens.TextSecondary)
                }
            }
        }
    }
}

@Composable
fun EditableSwarmTaskTree(
    tasks: List<SwarmTask>,
    editable: Boolean,
    onUpdateTask: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    depth: Int = 0,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tasks.forEach { task ->
            SwarmTaskRow(task, editable, depth, onUpdateTask)
            if (task.children.isNotEmpty()) {
                EditableSwarmTaskTree(
                    tasks = task.children,
                    editable = editable,
                    onUpdateTask = onUpdateTask,
                    modifier = Modifier.padding(start = (depth + 1) * 12.dp),
                    depth = depth + 1,
                )
            }
        }
    }
}

@Composable
private fun SwarmTaskRow(
    task: SwarmTask,
    editable: Boolean,
    depth: Int,
    onUpdateTask: (String, String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 8).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(task.status)
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${task.agent.displayName} · #${task.id}",
                color = CoworkTokens.TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
            if (editable) {
                OutlinedTextField(
                    value = task.title,
                    onValueChange = { onUpdateTask(task.id, it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    shape = CoworkTokens.ShapeCard,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoworkTokens.Mint.copy(alpha = 0.5f),
                        unfocusedBorderColor = CoworkTokens.Border,
                        focusedContainerColor = CoworkTokens.Surface,
                        unfocusedContainerColor = CoworkTokens.Surface,
                        focusedTextColor = CoworkTokens.TextPrimary,
                        unfocusedTextColor = CoworkTokens.TextPrimary,
                    ),
                )
            } else {
                Text(task.title, color = CoworkTokens.TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatusDot(status: TaskStatus) {
    val color = when (status) {
        TaskStatus.RUNNING -> CoworkTokens.Mint
        TaskStatus.COMPLETED -> CoworkTokens.Mint.copy(alpha = 0.7f)
        TaskStatus.FAILED -> CoworkTokens.Failed
        TaskStatus.CANCELLED -> CoworkTokens.TextMuted
        TaskStatus.PENDING -> CoworkTokens.TextMuted
    }
    if (status == TaskStatus.COMPLETED) {
        Icon(Icons.Default.Check, null, tint = color, modifier = Modifier.size(14.dp))
    } else {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}
