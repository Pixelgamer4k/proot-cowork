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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.data.chat.ChatThreadMeta
import com.proot.cowork.domain.agent.AgentFeatureFlags
import com.proot.cowork.domain.agent.AgentMessage
import com.proot.cowork.domain.agent.ShellCommandLogEntry
import com.proot.cowork.domain.agent.SwarmResponse
import com.proot.cowork.domain.agent.ToolCallStatus
import com.proot.cowork.domain.skills.PendingSkillWrite
import com.proot.cowork.domain.skills.SkillSaveOffer
import com.proot.cowork.ui.agent.swarm.ShellCommandLogCard
import com.proot.cowork.ui.agent.swarm.SwarmMessageItem
import com.proot.cowork.ui.kimi.KimiActionTimeline
import com.proot.cowork.ui.kimi.KimiAssistantBlock
import com.proot.cowork.ui.kimi.KimiChatTurn
import com.proot.cowork.ui.kimi.KimiComputerHeader
import com.proot.cowork.ui.kimi.KimiSystemNotice
import com.proot.cowork.ui.kimi.KimiThinkRow
import com.proot.cowork.ui.kimi.KimiTokens
import com.proot.cowork.ui.kimi.KimiUserCard
import com.proot.cowork.ui.kimi.groupMessagesIntoTurns
import com.proot.cowork.ui.kimi.thinkingLinesFromTools
import com.proot.cowork.ui.skills.SkillApprovalCard
import com.proot.cowork.ui.skills.SkillSaveOfferCard

private val QUICK_PROMPTS = listOf(
    "Create a React project in ~/Desktop",
    "Install Node.js and npm packages",
    "Run apt update and upgrade",
    "Write a Python script and run it",
)

@Composable
fun ChatTabContent(
    messages: List<AgentMessage>,
    chatThreads: List<ChatThreadMeta>,
    activeThreadId: String?,
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
    onSelectThread: (String) -> Unit,
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
    var threadMenuOpen by remember { mutableStateOf(false) }
    val activeThread = chatThreads.firstOrNull { it.id == activeThreadId }
    val turns = remember(messages) { groupMessagesIntoTurns(messages) }
    val lastTurnIndex = turns.lastIndex

    LaunchedEffect(turns.size, isExecuting, messages.size, pendingSkillWrite?.id, skillSaveOffer?.skillId) {
        if (turns.isNotEmpty() || isExecuting || pendingSkillWrite != null || skillSaveOffer != null) {
            listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(KimiTokens.Bg),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = composerBottomPadding + 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (messages.isNotEmpty() || chatThreads.isNotEmpty()) {
            item(key = "chat-toolbar") {
                KimiChatToolbar(
                    threadTitle = activeThread?.title ?: stringResource(R.string.chat_threads),
                    threadMenuOpen = threadMenuOpen,
                    onThreadMenuToggle = { threadMenuOpen = it },
                    chatThreads = chatThreads,
                    onSelectThread = onSelectThread,
                    onExportTranscript = onExportTranscript,
                    onNewConversation = onNewConversation,
                )
            }
        }

        if (turns.isEmpty() && !isExecuting) {
            item(key = "empty-hero") {
                KimiEmptyHero(
                    isApiConfigured = isApiConfigured,
                    onNavigateToSettings = onNavigateToSettings,
                    onQuickPrompt = onQuickPrompt,
                )
            }
        }

        itemsIndexed(turns, key = { index, turn -> turnKey(turn, index) }) { index, turn ->
            KimiTurnBlock(
                turn = turn,
                isActiveTurn = index == lastTurnIndex && isExecuting,
                isExecuting = isExecuting && index == lastTurnIndex,
                toolCallCount = if (index == lastTurnIndex) toolCallCount else turn.tools.size,
                maxToolCalls = maxToolCalls,
                toolLimitReached = toolLimitReached && index == lastTurnIndex,
                shellCommandLog = if (index == lastTurnIndex) shellCommandLog else emptyList(),
                onMessageCopied = onMessageCopied,
                onEditUserMessage = onEditUserMessage,
                onRegenerateFrom = onRegenerateFrom,
            )
        }

        if (AgentFeatureFlags.SWARM_UI_ENABLED && swarmResponse != null) {
            item(key = "swarm-${swarmResponse.messageId}") {
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
    }
}

@Composable
private fun KimiChatToolbar(
    threadTitle: String,
    threadMenuOpen: Boolean,
    onThreadMenuToggle: (Boolean) -> Unit,
    chatThreads: List<ChatThreadMeta>,
    onSelectThread: (String) -> Unit,
    onExportTranscript: () -> Unit,
    onNewConversation: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(onClick = { onThreadMenuToggle(true) }) {
                Icon(Icons.Default.History, null, tint = KimiTokens.TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(threadTitle, maxLines = 1, color = KimiTokens.TextSecondary)
            }
            DropdownMenu(expanded = threadMenuOpen, onDismissRequest = { onThreadMenuToggle(false) }) {
                chatThreads.forEach { thread ->
                    DropdownMenuItem(
                        text = { Text(thread.title, maxLines = 1) },
                        onClick = {
                            onThreadMenuToggle(false)
                            onSelectThread(thread.id)
                        },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExportTranscript) {
                Icon(Icons.Default.IosShare, null, tint = KimiTokens.TextSecondary, modifier = Modifier.size(16.dp))
            }
            TextButton(onClick = onNewConversation) {
                Icon(Icons.Default.AddComment, null, tint = KimiTokens.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun KimiEmptyHero(
    isApiConfigured: Boolean,
    onNavigateToSettings: () -> Unit,
    onQuickPrompt: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(KimiTokens.Card)
                .border(1.dp, KimiTokens.Border, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.SmartToy, null, tint = KimiTokens.Accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.size(16.dp))
        Text(
            stringResource(R.string.kimi_empty_title),
            fontWeight = FontWeight.SemiBold,
            color = KimiTokens.TextPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.size(8.dp))
        if (isApiConfigured) {
            Text(
                stringResource(R.string.kimi_empty_hint),
                color = KimiTokens.TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        } else {
            val link = stringResource(R.string.agent_empty_settings_link)
            val annotated = buildAnnotatedString {
                withStyle(SpanStyle(color = KimiTokens.Accent, textDecoration = TextDecoration.Underline)) {
                    append(link)
                }
                append(stringResource(R.string.agent_empty_settings_suffix))
            }
            Text(
                text = annotated,
                modifier = Modifier.clickable(onClick = onNavigateToSettings),
                color = KimiTokens.TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK_PROMPTS.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { prompt ->
                        Surface(
                            onClick = { onQuickPrompt(prompt) },
                            shape = KimiTokens.ShapePill,
                            color = KimiTokens.Card,
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, KimiTokens.Border, KimiTokens.ShapePill),
                        ) {
                            Text(
                                prompt,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                color = KimiTokens.TextSecondary,
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

@Composable
private fun KimiTurnBlock(
    turn: KimiChatTurn,
    isActiveTurn: Boolean,
    isExecuting: Boolean,
    toolCallCount: Int,
    maxToolCalls: Int,
    toolLimitReached: Boolean,
    shellCommandLog: List<ShellCommandLogEntry>,
    onMessageCopied: () -> Unit,
    onEditUserMessage: (String, String) -> Unit,
    onRegenerateFrom: (String) -> Unit,
) {
    var thinkExpanded by remember(turn.user?.id) { mutableStateOf(isExecuting) }

    LaunchedEffect(isExecuting) {
        if (!isExecuting) thinkExpanded = false
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        turn.systemNotices.forEach { notice ->
            KimiSystemNotice(text = notice.content)
        }

        turn.user?.let { user ->
            KimiUserCard(
                message = user,
                onCopy = onMessageCopied,
                onEdit = { newText -> onEditUserMessage(user.id, newText) },
                onRegenerate = { onRegenerateFrom(user.id) },
            )
        }

        val hasAgentActivity = turn.tools.isNotEmpty() || turn.assistant != null || isActiveTurn
        if (hasAgentActivity) {
            val showComputer = isActiveTurn && (isExecuting || turn.tools.isNotEmpty())
            if (showComputer) {
                KimiComputerHeader(
                    isActive = isExecuting,
                    toolCallCount = toolCallCount,
                    maxToolCalls = maxToolCalls,
                    toolLimitReached = toolLimitReached,
                )
            }

            val thinkLines = when {
                turn.tools.isNotEmpty() -> thinkingLinesFromTools(turn.tools)
                isExecuting -> listOf(stringResource(R.string.kimi_thinking))
                else -> emptyList()
            }
            if (thinkLines.isNotEmpty()) {
                KimiThinkRow(
                    lines = thinkLines,
                    expanded = thinkExpanded,
                    onToggle = { thinkExpanded = !thinkExpanded },
                )
            }

            if (turn.tools.isNotEmpty()) {
                KimiActionTimeline(tools = turn.tools)
            }

            if (isActiveTurn && shellCommandLog.isNotEmpty()) {
                ShellCommandLogCard(entries = shellCommandLog, expandedByDefault = true)
            }

            if (turn.assistant != null) {
                val assistant = turn.assistant
                val streaming = isExecuting &&
                    assistant.content.isBlank() &&
                    turn.tools.any { it.toolStatus == ToolCallStatus.RUNNING }
                KimiAssistantBlock(
                    content = assistant.content,
                    isStreaming = isExecuting && (assistant.content.isBlank() || streaming),
                    onCopy = onMessageCopied,
                    onRegenerate = { onRegenerateFrom(assistant.id) },
                )
            } else if (isExecuting && isActiveTurn) {
                KimiAssistantBlock(
                    content = "",
                    isStreaming = true,
                    onCopy = onMessageCopied,
                    onRegenerate = null,
                )
            }
        }
    }
}

private fun turnKey(turn: KimiChatTurn, index: Int): String {
    val userId = turn.user?.id ?: "nouser"
    val asstId = turn.assistant?.id ?: "noasst"
    return "turn-$index-$userId-$asstId-${turn.tools.size}"
}
