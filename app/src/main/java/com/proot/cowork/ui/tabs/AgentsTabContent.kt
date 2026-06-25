package com.proot.cowork.ui.tabs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.domain.agent.SwarmAgentState
import com.proot.cowork.domain.agent.SwarmAgentType
import com.proot.cowork.domain.agent.TaskStatus
import com.proot.cowork.ui.components.CoworkCard
import com.proot.cowork.ui.design.CoworkTokens
import com.proot.cowork.ui.theme.Motion

private fun iconFor(agent: SwarmAgentType): ImageVector = when (agent) {
    SwarmAgentType.Planner -> Icons.Default.Psychology
    SwarmAgentType.Researcher -> Icons.Default.Search
    SwarmAgentType.Executor -> Icons.Default.Terminal
    SwarmAgentType.Coder -> Icons.Default.Code
    SwarmAgentType.Validator -> Icons.Default.Verified
    SwarmAgentType.Slack -> Icons.Default.Notifications
}

@Composable
fun AgentsTabContent(
    agentStates: List<SwarmAgentState>,
    isExecuting: Boolean,
    maxAgentPool: Int,
    modifier: Modifier = Modifier,
) {
    val onlineCount = agentStates.count {
        it.status == TaskStatus.RUNNING || it.status == TaskStatus.COMPLETED
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.active_agents), fontWeight = FontWeight.SemiBold, color = CoworkTokens.TextPrimary)
                Text(
                    stringResource(R.string.agents_online_count, onlineCount.coerceAtLeast(if (isExecuting) 1 else 0)),
                    color = CoworkTokens.Mint,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                stringResource(R.string.agent_pool_limit, maxAgentPool),
                color = CoworkTokens.TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            AgentActivityBar(agentStates, isExecuting)
        }
        items(agentStates, key = { it.type.name }) { agent ->
            AgentCard(agent, isExecuting)
        }
    }
}

@Composable
private fun AgentActivityBar(agentStates: List<SwarmAgentState>, isExecuting: Boolean) {
    val active = agentStates.count { it.status == TaskStatus.RUNNING }
    val progress by animateFloatAsState(
        targetValue = if (isExecuting) (active.toFloat() / agentStates.size.coerceAtLeast(1)).coerceIn(0.1f, 1f) else 0.15f,
        animationSpec = Motion.springSmooth,
        label = "agentBar",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(CoworkTokens.SurfaceElevated),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(6.dp)
                .clip(CircleShape)
                .background(CoworkTokens.Mint),
        )
    }
}

@Composable
private fun AgentCard(agent: SwarmAgentState, isExecuting: Boolean) {
    val online = agent.status == TaskStatus.RUNNING || (isExecuting && agent.tasksCompleted > 0)
    CoworkCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (online) CoworkTokens.Mint.copy(alpha = 0.15f) else CoworkTokens.SurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(iconFor(agent.type), null, tint = if (online) CoworkTokens.Mint else CoworkTokens.TextMuted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(agent.type.displayName, fontWeight = FontWeight.SemiBold, color = CoworkTokens.TextPrimary)
                Text(
                    agent.currentTask ?: when (agent.status) {
                        TaskStatus.RUNNING -> stringResource(R.string.agent_working)
                        TaskStatus.COMPLETED -> stringResource(R.string.agent_idle_done)
                        TaskStatus.FAILED -> stringResource(R.string.agent_idle_failed)
                        else -> stringResource(R.string.agent_idle)
                    },
                    color = CoworkTokens.TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            Surface(shape = CoworkTokens.ShapePill, color = CoworkTokens.SurfaceElevated, modifier = Modifier.border(1.dp, CoworkTokens.Border, CoworkTokens.ShapePill)) {
                Text(
                    stringResource(R.string.agent_task_count, agent.tasksCompleted),
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = CoworkTokens.TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
