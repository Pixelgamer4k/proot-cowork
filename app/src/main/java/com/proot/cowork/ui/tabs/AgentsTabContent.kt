package com.proot.cowork.ui.tabs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.proot.cowork.ui.components.CoworkCard
import com.proot.cowork.ui.theme.Motion

private data class AgentCardModel(
    val name: String,
    val role: String,
    val tasks: Int,
    val online: Boolean,
    val icon: ImageVector,
)

private val DEMO_AGENTS = listOf(
    AgentCardModel("Planner", "Task Planning", 24, true, Icons.Default.Psychology),
    AgentCardModel("Researcher", "Information Gathering", 56, false, Icons.Default.Search),
    AgentCardModel("Executor", "Command Execution", 89, true, Icons.Default.Terminal),
    AgentCardModel("Coder", "Code Generation", 67, false, Icons.Default.Code),
)

@Composable
fun AgentsTabContent(
    isExecuting: Boolean,
    modifier: Modifier = Modifier,
) {
    val onlineCount = DEMO_AGENTS.count { it.online } + if (isExecuting) 1 else 0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CoworkCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.active_agents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = stringResource(R.string.agents_online_count, onlineCount.coerceAtMost(6)),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                AgentActivityChart(active = isExecuting)
            }
        }

        items(DEMO_AGENTS) { agent ->
            AgentRow(agent = agent, highlight = isExecuting && agent.name == "Planner")
        }

        item {
            Text(
                text = stringResource(R.string.coming_soon_swarm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AgentActivityChart(active: Boolean) {
    val heights = listOf(0.35f, 0.55f, 0.75f, 0.5f, 0.9f, if (active) 1f else 0.4f)
    val labels = listOf("Pln", "Res", "Exe", "Cod", "Val", "Sl")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.zip(labels).forEach { (target, label) ->
            val heightFrac by animateFloatAsState(
                targetValue = target,
                animationSpec = Motion.springSmooth,
                label = "bar",
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = (72 * heightFrac).dp.coerceAtLeast(8.dp))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + heightFrac * 0.35f)),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AgentRow(agent: AgentCardModel, highlight: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (highlight) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(agent.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(agent.name, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.size(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (agent.online) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                            ),
                    )
                }
                Text(
                    text = agent.role,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.agent_task_count, agent.tasks),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
