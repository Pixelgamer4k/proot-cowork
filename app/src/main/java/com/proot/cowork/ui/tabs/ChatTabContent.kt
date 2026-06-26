package com.proot.cowork.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.domain.agent.AgentMessage
import com.proot.cowork.domain.agent.MessageRole
import com.proot.cowork.domain.agent.ShellCommandLogEntry
import com.proot.cowork.domain.agent.SwarmResponse
import com.proot.cowork.domain.skills.PendingSkillWrite
import com.proot.cowork.domain.skills.SkillSaveOffer
import com.proot.cowork.ui.agent.ToolMessageBubble
import com.proot.cowork.ui.agent.swarm.ShellCommandLogCard
import com.proot.cowork.ui.agent.swarm.SwarmMessageItem
import com.proot.cowork.ui.agent.swarm.ToolCallLimitBar
import com.proot.cowork.ui.chat.ChatMessageBubble
import com.proot.cowork.ui.design.CoworkTokens
import com.proot.cowork.ui.skills.SkillApprovalCard
import com.proot.cowork.ui.skills.SkillSaveOfferCard

private val QUICK_PROMPTS = listOf(
    "Create a React project",
    "Set up a database",
    "Install Node.js packages",
    "Run system update",
)

@Composable
fun ChatTabContent(
    messages: List<AgentMessage>,
    swarmResponse: SwarmResponse?,
    isExecuting: Boolean,
    isApiConfigured: Boolean,
    awaitingApproval: Boolean,
    toolCallCount: Int,
    maxToolCalls: Int,
    toolLimitReached: Boolean,
    shellCommandLog: List<ShellCommandLogEntry>,
    pendingSkillWrite: PendingSkillWrite?,
    skillSaveOffer: SkillSaveOffer?,
    composerBottomPadding: Dp,
    onQuickPrompt: (String) -> Unit,
    onUpdateSwarmTask: (String, String) -> Unit,
    onApprovePlan: () -> Unit,
    onRejectPlan: () -> Unit,
    onCancelSubtask: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNewConversation: () -> Unit,
    onExportTranscript: () -> Unit,
    onMessageCopied: () -> Unit,
    onEditUserMessage: (String, String) -> Unit,
    onRegenerateFrom: (String) -> Unit,
    onApproveSkillWrite: () -> Unit,
    onRejectSkillWrite: () -> Unit,
    onAcceptSkillSaveOffer: () -> Unit,
    onDismissSkillSaveOffer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val visibleMessages = messages.filterNot { msg ->
        isEmbeddedInSwarm(msg, swarmResponse, messages)
    }

    LaunchedEffect(visibleMessages.size, isExecuting, awaitingApproval, swarmResponse?.phase, pendingSkillWrite?.id, skillSaveOffer?.skillId) {
        if (visibleMessages.isNotEmpty() || isExecuting || awaitingApproval || pendingSkillWrite != null || skillSaveOffer != null) {
            listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = composerBottomPadding + 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (visibleMessages.isNotEmpty()) {
            item(key = "chat-actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onExportTranscript) {
                        Icon(Icons.Default.IosShare, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.chat_export))
                    }
                    TextButton(onClick = onNewConversation) {
                        Icon(Icons.Default.AddComment, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.chat_new_conversation))
                    }
                }
            }
        }

        if (visibleMessages.isEmpty() && !isExecuting && !awaitingApproval) {
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
                    if (isApiConfigured) {
                        Text(
                            stringResource(R.string.agent_empty_hint),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = CoworkTokens.TextSecondary,
                        )
                    } else {
                        val link = stringResource(R.string.agent_empty_settings_link)
                        val annotated = buildAnnotatedString {
                            withStyle(SpanStyle(color = CoworkTokens.Mint, textDecoration = TextDecoration.Underline)) {
                                append(link)
                            }
                            append(stringResource(R.string.agent_empty_settings_suffix))
                        }
                        Text(
                            text = annotated,
                            modifier = Modifier.clickable(onClick = onNavigateToSettings),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = CoworkTokens.TextSecondary,
                        )
                    }
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

        items(visibleMessages, key = { it.id }) { msg ->
            when {
                swarmResponse != null && msg.id == swarmResponse.messageId -> {
                    SwarmMessageItem(
                        response = swarmResponse,
                        editable = awaitingApproval,
                        onUpdateTask = onUpdateSwarmTask,
                        onApprove = onApprovePlan,
                        onReject = onRejectPlan,
                        onCancelSubtask = onCancelSubtask,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                msg.role == MessageRole.TOOL -> {
                    ToolMessageBubble(msg, Modifier.fillMaxWidth())
                }
                else -> {
                    ChatMessageBubble(
                        message = msg,
                        onCopy = onMessageCopied,
                        onEdit = if (msg.role == MessageRole.USER) {
                            { newText -> onEditUserMessage(msg.id, newText) }
                        } else {
                            null
                        },
                        onRegenerate = if (msg.role == MessageRole.USER || msg.role == MessageRole.ASSISTANT) {
                            { onRegenerateFrom(msg.id) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (pendingSkillWrite != null) {
            item(key = "skill-approval-${pendingSkillWrite.id}") {
                SkillApprovalCard(
                    pending = pendingSkillWrite,
                    onApprove = onApproveSkillWrite,
                    onReject = onRejectSkillWrite,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (skillSaveOffer != null) {
            item(key = "skill-save-${skillSaveOffer.skillId}") {
                SkillSaveOfferCard(
                    offer = skillSaveOffer,
                    onSave = onAcceptSkillSaveOffer,
                    onDismiss = onDismissSkillSaveOffer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (isExecuting && swarmResponse == null) {
            item(key = "fast-execution-hud") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ToolCallLimitBar(
                        count = toolCallCount,
                        max = maxToolCalls,
                        limitReached = toolLimitReached,
                    )
                    if (shellCommandLog.isNotEmpty()) {
                        ShellCommandLogCard(
                            entries = shellCommandLog,
                            expandedByDefault = true,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            repeat(3) { i ->
                                Box(
                                    Modifier
                                        .padding(end = 4.dp)
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(CoworkTokens.Mint.copy(alpha = 0.3f + i * 0.2f)),
                                )
                            }
                            Text(
                                stringResource(R.string.agent_working),
                                color = CoworkTokens.TextMuted,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun isEmbeddedInSwarm(
    msg: AgentMessage,
    swarm: SwarmResponse?,
    messages: List<AgentMessage>,
): Boolean {
    if (swarm == null) return false
    val swarmIdx = messages.indexOfFirst { it.id == swarm.messageId }
    if (swarmIdx < 0) return false
    val msgIdx = messages.indexOfFirst { it.id == msg.id }
    if (msgIdx < 0) return false
    if (msg.id == swarm.messageId) return false
    val endIdx = messages.drop(swarmIdx + 1).indexOfFirst { it.role == MessageRole.USER }
    val rangeEnd = if (endIdx < 0) messages.size else swarmIdx + 1 + endIdx
    return msgIdx in (swarmIdx + 1) until rangeEnd && msg.role == MessageRole.TOOL
}
