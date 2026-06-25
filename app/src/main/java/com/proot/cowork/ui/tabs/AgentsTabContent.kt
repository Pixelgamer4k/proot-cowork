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
import com.proot.cowork.ui.components.CoworkCard
import com.proot.cowork.ui.design.CoworkTokens
import com.proot.cowork.ui.theme.Motion

private data class AgentCardModel(val name: String, val role: String, val tasks: Int, val online: Boolean, val icon: ImageVector)

private val DEMO_AGENTS = listOf(
    AgentCardModel("Planner", "Task Planning", 24, true, Icons.Default.Psychology),
    AgentCardModel("Researcher", "Information Gathering", 56, false, Icons.Default.Search),
    AgentCardModel("Executor", "Command Execution", 89, true, Icons.Default.Terminal),
    AgentCardModel("Coder", "Code Generation", 67, false, Icons.Default.Code),
)

@Composable
fun AgentsTabContent(isExecuting: Boolean, modifier: Modifier = Modifier) {
    val onlineCount = DEMO_AGENTS.count { it.online } + if (isExecuting) 1 else 0
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            CoworkCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.active_agents), fontWeight = FontWeight.SemiBold, color = CoworkTokens.TextPrimary)
                    Surface(shape = CoworkTokens.ShapePill, color = CoworkTokens.Mint.copy(alpha = 0.14f)) {
                        Text(
                            stringResource(R.string.agents_online_count, onlineCount.coerceAtMost(6)),
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = CoworkTokens.Mint,
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                AgentActivityChart(isExecuting)
            }
        }
        items(DEMO_AGENTS) { AgentRow(it, isExecuting && it.name == "Planner") }
    }
}

@Composable
private fun AgentActivityChart(active: Boolean) {
    val heights = listOf(0.32f, 0.52f, 0.72f, 0.48f, 0.88f, if (active) 1f else 0.38f)
    val labels = listOf("Pln", "Res", "Exe", "Cod", "Val", "Sl")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        heights.zip(labels).forEach { (target, label) ->
            val h by animateFloatAsState(target, Motion.springSmooth, label = "bar")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(width = 26.dp, height = maxOf(8.dp, (68 * h).dp))
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(CoworkTokens.Mint.copy(alpha = 0.28f + h * 0.45f)),
                )
                Spacer(Modifier.height(6.dp))
                Text(label, color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AgentRow(agent: AgentCardModel, highlight: Boolean) {
    Surface(
        shape = CoworkTokens.ShapeCard,
        color = if (highlight) CoworkTokens.Mint.copy(alpha = 0.08f) else CoworkTokens.Surface,
        modifier = Modifier.fillMaxWidth().border(1.dp, CoworkTokens.Border, CoworkTokens.ShapeCard),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CoworkTokens.ShapeIconTile).background(CoworkTokens.SurfaceElevated).border(1.dp, CoworkTokens.Border, CoworkTokens.ShapeIconTile),
                contentAlignment = Alignment.Center,
            ) { Icon(agent.icon, null, tint = CoworkTokens.Mint) }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(agent.name, fontWeight = FontWeight.SemiBold, color = CoworkTokens.TextPrimary)
                    Spacer(Modifier.size(6.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (agent.online) CoworkTokens.Mint else CoworkTokens.TextMuted))
                }
                Text(agent.role, color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.agent_task_count, agent.tasks), color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        }
    }
}
