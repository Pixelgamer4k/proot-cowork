package com.proot.cowork.ui.agent.swarm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proot.cowork.R
import com.proot.cowork.domain.agent.FileListingRow
import com.proot.cowork.domain.agent.PlanStep
import com.proot.cowork.domain.agent.SwarmPhase
import com.proot.cowork.domain.agent.SwarmResponse
import com.proot.cowork.domain.agent.SwarmResultType
import com.proot.cowork.domain.agent.SwarmTask
import com.proot.cowork.domain.agent.SummaryChip
import com.proot.cowork.domain.agent.TaskStatus
import com.proot.cowork.domain.agent.TerminalBlock
import com.proot.cowork.ui.design.CoworkTokens

private val SwarmBg = Color(0xFF121212)
private val SwarmSurface = Color(0xFF1E1E1E)
private val SwarmSurfaceAlt = Color(0xFF252525)
private val SwarmThinkingBg = Color(0xFF1A1A1A)
private val SwarmTerminalBg = Color(0xFF0D0D0D)
private val SwarmBorder = Color(0xFF2A2A2A)
private val SwarmAccent = Color(0xFF5EEAD4)
private val SwarmTextSecondary = Color(0xFFA1A1AA)
private val SwarmStdout = Color(0xFF4ADE80)
private val SwarmStderr = Color(0xFFF87171)

@Composable
fun SwarmMessageItem(
    response: SwarmResponse,
    editable: Boolean,
    onUpdateTask: (String, String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var thinkingExpanded by remember(response.messageId) { mutableStateOf(false) }

    LaunchedEffect(response.phase) {
        if (response.phase == SwarmPhase.COMPLETE) {
            thinkingExpanded = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(150))
            .clip(RoundedCornerShape(16.dp))
            .background(SwarmSurface)
            .border(1.dp, SwarmBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (response.thinkingLogs.isNotEmpty() || response.phase == SwarmPhase.PLANNING) {
            AgentThinkingPill(
                logs = response.thinkingLogs.ifEmpty { listOf("Planning swarm…") },
                agentCount = response.activeAgentCount.coerceAtLeast(1),
                expanded = thinkingExpanded,
                onToggle = { thinkingExpanded = !thinkingExpanded },
            )
        }

        if (response.plan.isNotEmpty() &&
            (response.phase == SwarmPhase.AWAITING_APPROVAL || response.phase == SwarmPhase.EXECUTING)
        ) {
            SwarmPlanCard(
                summary = response.summary,
                steps = response.plan,
                tasks = response.tasks,
                editable = editable && response.phase == SwarmPhase.AWAITING_APPROVAL,
                showActions = response.phase == SwarmPhase.AWAITING_APPROVAL,
                onUpdateTask = onUpdateTask,
                onApprove = onApprove,
                onReject = onReject,
            )
        }

        if (response.phase == SwarmPhase.EXECUTING) {
            ExecutionStatusRow(
                current = response.currentStep.coerceAtLeast(1),
                total = response.totalSteps.coerceAtLeast(response.plan.size).coerceAtLeast(1),
            )
        }

        if (response.phase == SwarmPhase.COMPLETE || response.resultType != SwarmResultType.NONE) {
            ExecutionResultView(
                response = response,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (response.terminalOutputs.isNotEmpty() &&
            (response.phase == SwarmPhase.EXECUTING || response.phase == SwarmPhase.COMPLETE)
        ) {
            response.terminalOutputs.forEach { terminal ->
                TerminalOutputCard(terminal = terminal)
            }
        }
    }
}

@Composable
fun SwarmPlanCard(
    summary: String,
    steps: List<PlanStep>,
    tasks: List<SwarmTask>,
    editable: Boolean,
    showActions: Boolean,
    onUpdateTask: (String, String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SwarmSurface)
            .border(1.dp, SwarmBorder, RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(SwarmSurface)
                .padding(start = 0.dp),
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .heightIn(min = 48.dp)
                    .background(SwarmAccent),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.swarm_plan_title),
                    color = SwarmAccent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = summary,
                    color = SwarmTextSecondary,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    steps.forEachIndexed { index, step ->
                        val task = tasks.getOrNull(index)
                        PlanStepRow(
                            index = index + 1,
                            step = step,
                            task = task,
                            editable = editable,
                            onUpdateTask = onUpdateTask,
                        )
                    }
                }
            }
        }

        if (showActions) {
            HorizontalDivider(color = SwarmBorder)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SwarmSurfaceAlt)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SwarmAccent,
                        contentColor = Color(0xFF0A0A0B),
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.swarm_execute), fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF2A2A2A),
                        contentColor = SwarmTextSecondary,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SwarmBorder),
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun PlanStepRow(
    index: Int,
    step: PlanStep,
    task: SwarmTask?,
    editable: Boolean,
    onUpdateTask: (String, String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (task?.status == TaskStatus.RUNNING) SwarmAccent.copy(alpha = 0.08f) else SwarmSurfaceAlt)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$index",
            color = SwarmAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.width(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = step.agent,
                color = SwarmTextSecondary.copy(alpha = 0.8f),
                fontSize = 11.sp,
            )
            if (editable && task != null) {
                androidx.compose.material3.OutlinedTextField(
                    value = task.title,
                    onValueChange = { onUpdateTask(task.id, it) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    singleLine = false,
                    maxLines = 2,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SwarmAccent.copy(alpha = 0.5f),
                        unfocusedBorderColor = SwarmBorder,
                        focusedContainerColor = SwarmSurface,
                        unfocusedContainerColor = SwarmSurface,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )
            } else {
                Text(
                    text = step.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (task?.status == TaskStatus.COMPLETED) {
            Text("✓", color = SwarmStdout, fontSize = 14.sp)
        } else if (task?.status == TaskStatus.RUNNING) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = SwarmAccent,
            )
        }
    }
}

@Composable
fun AgentThinkingPill(
    logs: List<String>,
    agentCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(8.dp),
            color = SwarmSurfaceAlt,
            modifier = Modifier.height(32.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🧠", fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$agentCount agent${if (agentCount == 1) "" else "s"} thinking…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .heightIn(max = 150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SwarmThinkingBg)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                logs.takeLast(24).forEach { line ->
                    Text(
                        text = line,
                        color = Color(0xFF9E9E9E),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                TextButton(onClick = onToggle, modifier = Modifier.align(Alignment.End)) {
                    Text("▾ Hide reasoning", color = SwarmTextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ExecutionStatusRow(current: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = SwarmAccent,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Executing step $current/$total…",
            color = SwarmTextSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
fun ExecutionResultView(
    response: SwarmResponse,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.animateContentSize(tween(150)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        response.narrativeSummary?.let { summary ->
            Text(
                text = summary.lines().take(6).joinToString("\n"),
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }

        if (response.summaryChips.isNotEmpty()) {
            SummaryChipsRow(chips = response.summaryChips)
        }

        if (response.fileRows.isNotEmpty()) {
            FileListingTable(rows = response.fileRows)
        }
    }
}

@Composable
private fun SummaryChipsRow(chips: List<SummaryChip>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SwarmSurfaceAlt,
                border = androidx.compose.foundation.BorderStroke(1.dp, SwarmBorder),
            ) {
                Text(
                    text = "${chip.icon} ${chip.label}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FileListingTable(rows: List<FileListingRow>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, SwarmBorder, RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(SwarmSurfaceAlt)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            TableHeaderCell("Type", 0.18f)
            TableHeaderCell("Name", 0.42f)
            TableHeaderCell("Size", 0.18f)
            TableHeaderCell("Modified", 0.22f)
        }
        rows.take(40).forEachIndexed { index, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (index % 2 == 0) SwarmSurface else SwarmSurfaceAlt)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                TableCell(row.type, 0.18f, SwarmAccent)
                TableCell(row.name, 0.42f, Color.White)
                TableCell(row.size, 0.18f, SwarmTextSecondary)
                TableCell(row.modified, 0.22f, SwarmTextSecondary)
            }
        }
    }
}

@Composable
private fun RowScope.TableHeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        color = SwarmTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun RowScope.TableCell(text: String, weight: Float, color: Color) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        color = color,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun TerminalOutputCard(terminal: TerminalBlock) {
    var expanded by remember(terminal.id) { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, SwarmBorder, RoundedCornerShape(12.dp))
            .background(SwarmTerminalBg),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "🔧 ${terminal.agentName} · ${terminal.toolName}",
                color = SwarmAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText("terminal", terminal.stdout + terminal.stderr))
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = SwarmTextSecondary, modifier = Modifier.size(16.dp))
            }
        }

        if (!expanded) {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text("Show terminal output", color = SwarmTextSecondary, fontSize = 12.sp)
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                if (terminal.stdout.isNotBlank()) {
                    Text(
                        text = terminal.stdout,
                        color = SwarmStdout,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                if (terminal.stderr.isNotBlank()) {
                    Text(
                        text = terminal.stderr,
                        color = SwarmStderr,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                terminal.exitCode?.let { code ->
                    Text("exit $code", color = SwarmTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
            TextButton(onClick = { expanded = false }, modifier = Modifier.align(Alignment.End)) {
                Text("Hide output", color = SwarmTextSecondary, fontSize = 12.sp)
            }
        }
    }
}
