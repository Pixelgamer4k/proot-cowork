package com.proot.cowork.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.domain.agent.AgentMessage
import com.proot.cowork.domain.agent.MessageRole
import com.proot.cowork.domain.agent.SwarmTask
import com.proot.cowork.ui.theme.Motion

private val QUICK_PROMPTS = listOf(
    "Create a React project",
    "Set up a database",
    "Install Node.js packages",
    "Run system update",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatTabContent(
    messages: List<AgentMessage>,
    swarmTasks: List<SwarmTask>,
    isExecuting: Boolean,
    composerBottomPadding: androidx.compose.ui.unit.Dp,
    onQuickPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isExecuting) {
        if (messages.isNotEmpty() || isExecuting) {
            val target = if (isExecuting) messages.size else messages.lastIndex
            listState.animateScrollToItem(target.coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = composerBottomPadding + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (messages.isEmpty() && !isExecuting) {
            item(key = "hero") {
                ChatHero(onQuickPrompt = onQuickPrompt)
            }
        }

        if (swarmTasks.isNotEmpty()) {
            item(key = "swarm") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(Motion.tweenMedium) + slideInVertically { -it / 3 },
                    exit = fadeOut(Motion.tweenQuick),
                ) {
                    SwarmPlanCard(swarmTasks)
                }
            }
        }

        items(messages, key = { it.id }) { message ->
            ChatBubble(message)
        }

        if (isExecuting) {
            item(key = "typing") {
                TypingRow()
            }
        }
    }
}

@Composable
private fun ChatHero(onQuickPrompt: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = stringResource(R.string.agent_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.agent_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            QUICK_PROMPTS.forEach { prompt ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { onQuickPrompt(prompt) },
                ) {
                    Text(
                        text = prompt,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: AgentMessage) {
    val isUser = message.role == MessageRole.USER
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 18.dp,
    )
    val color = when (message.role) {
        MessageRole.USER -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        MessageRole.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = shape,
            color = color,
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TypingRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + i * 0.2f)),
            )
        }
        Text(
            text = stringResource(R.string.agent_working),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwarmPlanCard(tasks: List<SwarmTask>) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.swarm_plan_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            tasks.forEach { task ->
                Text(
                    text = "• ${task.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
