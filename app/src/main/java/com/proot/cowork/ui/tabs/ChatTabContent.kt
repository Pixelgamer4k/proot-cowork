package com.proot.cowork.ui.tabs

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
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
import com.proot.cowork.ui.design.CoworkTokens

private val QUICK_PROMPTS = listOf(
    "Create a React project",
    "Set up a database",
    "Install Node.js packages",
    "Run system update",
)

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
            listState.animateScrollToItem((if (isExecuting) messages.size else messages.lastIndex).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = composerBottomPadding + 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (messages.isEmpty() && !isExecuting) {
            item(key = "hero") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CoworkTokens.Mint.copy(alpha = 0.12f))
                            .border(1.dp, CoworkTokens.Mint.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.SmartToy, null, tint = CoworkTokens.Mint, modifier = Modifier.size(30.dp))
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(stringResource(R.string.agent_empty_title), fontWeight = FontWeight.SemiBold, color = CoworkTokens.TextPrimary)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.agent_empty_hint), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.size(14.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QUICK_PROMPTS.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { prompt ->
                                    Surface(
                                        onClick = { onQuickPrompt(prompt) },
                                        shape = CoworkTokens.ShapePill,
                                        color = CoworkTokens.SurfaceElevated,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            prompt,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            color = CoworkTokens.TextSecondary,
                                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        if (swarmTasks.isNotEmpty()) {
            item(key = "swarm") {
                Surface(shape = CoworkTokens.ShapeCard, color = CoworkTokens.Mint.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.swarm_plan_title), color = CoworkTokens.Mint, fontWeight = FontWeight.SemiBold)
                        swarmTasks.forEach { Text("• ${it.title}", color = CoworkTokens.TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        items(messages, key = { it.id }) { msg ->
            val isUser = msg.role == MessageRole.USER
            Box(Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
                Surface(
                    modifier = Modifier.widthIn(max = 300.dp),
                    shape = RoundedCornerShape(18.dp, 18.dp, if (isUser) 18.dp else 6.dp, if (isUser) 6.dp else 18.dp),
                    color = when (msg.role) {
                        MessageRole.USER -> CoworkTokens.Mint.copy(alpha = 0.16f)
                        MessageRole.SYSTEM -> CoworkTokens.SurfaceElevated
                        else -> CoworkTokens.Surface
                    },
                ) {
                    Text(msg.content, Modifier.padding(14.dp, 10.dp), color = CoworkTokens.TextPrimary)
                }
            }
        }
        if (isExecuting) {
            item(key = "typing") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(3) { i ->
                        Box(Modifier.padding(end = 4.dp).size(6.dp).clip(RoundedCornerShape(50)).background(CoworkTokens.Mint.copy(alpha = 0.3f + i * 0.2f)))
                    }
                    Text(stringResource(R.string.agent_working), color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
