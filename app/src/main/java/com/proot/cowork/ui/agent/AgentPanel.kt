package com.proot.cowork.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.domain.agent.AgentMessage
import com.proot.cowork.domain.agent.ExecutionMode
import com.proot.cowork.domain.agent.MessageRole
import com.proot.cowork.domain.agent.SwarmTask
import com.proot.cowork.ui.theme.Motion

@Composable
fun AgentPanel(
    messages: List<AgentMessage>,
    swarmTasks: List<SwarmTask>,
    executionMode: ExecutionMode,
    isExecuting: Boolean,
    onModeChange: (ExecutionMode) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenSkills: () -> Unit,
    onNavigateToSettings: () -> Unit,
    composerBottomPadding: Dp,
    scrollOnInput: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isExecuting) {
        if (messages.isNotEmpty() || isExecuting) {
            val target = if (isExecuting) messages.size else messages.lastIndex
            listState.animateScrollToItem(target.coerceAtLeast(0))
        }
    }

    LaunchedEffect(composerBottomPadding, scrollOnInput) {
        if (scrollOnInput && (messages.isNotEmpty() || isExecuting)) {
            val target = if (isExecuting) messages.size else messages.lastIndex
            listState.animateScrollToItem(target.coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        AgentToolbar(
            executionMode = executionMode,
            onModeChange = onModeChange,
            onOpenSkills = onOpenSkills,
            onOpenBrowser = onOpenBrowser,
            onOpenTerminal = onOpenTerminal,
            onNavigateToSettings = onNavigateToSettings,
        )

        AnimatedVisibility(
            visible = swarmTasks.isNotEmpty(),
            enter = fadeIn(Motion.tweenMedium) + slideInVertically(Motion.springSmoothOffset) { -it / 2 },
            exit = fadeOut(Motion.tweenQuick),
        ) {
            SwarmTaskTree(
                tasks = swarmTasks,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = composerBottomPadding + 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty() && !isExecuting) {
                item(key = "empty") {
                    EmptyAgentState()
                }
            }
            items(messages, key = { it.id }) { message ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(Motion.tweenMedium) + slideInVertically(Motion.springSmoothOffset) { it / 3 },
                ) {
                    MessageBubble(message)
                }
            }
            if (isExecuting) {
                item(key = "typing") {
                    TypingIndicator()
                }
            }
        }
    }
}

@Composable
private fun AgentToolbar(
    executionMode: ExecutionMode,
    onModeChange: (ExecutionMode) -> Unit,
    onOpenSkills: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenTerminal: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ModeChip(
                selected = executionMode == ExecutionMode.SWARM,
                label = stringResource(R.string.mode_swarm_short),
                onClick = { onModeChange(ExecutionMode.SWARM) },
            )
            ModeChip(
                selected = executionMode == ExecutionMode.FAST,
                label = stringResource(R.string.mode_fast),
                onClick = { onModeChange(ExecutionMode.FAST) },
            )
        }
        Row {
            IconButton(onClick = onOpenSkills, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Psychology, contentDescription = stringResource(R.string.skills))
            }
            IconButton(onClick = onOpenBrowser, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.browser))
            }
            IconButton(onClick = onOpenTerminal, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Terminal, contentDescription = stringResource(R.string.terminal))
            }
            IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
            }
        }
    }
}

@Composable
private fun ModeChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun EmptyAgentState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        )
        Text(
            text = stringResource(R.string.agent_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.agent_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageBubble(message: AgentMessage) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp,
    )

    val containerColor = when (message.role) {
        MessageRole.USER -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        MessageRole.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = shape,
            color = containerColor,
            tonalElevation = 0.dp,
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Icon(
            Icons.Default.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = 0.35f + (index * 0.2f),
                            ),
                        ),
                )
            }
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.agent_working),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwarmTaskTree(
    tasks: List<SwarmTask>,
    modifier: Modifier = Modifier,
    indent: Int = 0,
) {
    Column(modifier = modifier.padding(start = (indent * 10).dp)) {
        tasks.forEach { task ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (task.children.isNotEmpty()) {
                SwarmTaskTree(tasks = task.children, indent = indent + 1)
            }
        }
    }
}
