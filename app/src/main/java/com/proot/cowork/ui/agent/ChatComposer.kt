package com.proot.cowork.ui.agent

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.domain.agent.ExecutionMode
import com.proot.cowork.ui.design.CoworkTokens
import com.proot.cowork.ui.theme.Motion

@Composable
fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isExecuting: Boolean,
    isApiConfigured: Boolean,
    awaitingApproval: Boolean = false,
    executionMode: ExecutionMode,
    onModeChange: (ExecutionMode) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var modeMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) { onFocusChange(isFocused) }

    val showSend = value.isNotBlank() && !isExecuting && !awaitingApproval
    val canSend = showSend && isApiConfigured
    val borderColor = if (isFocused) CoworkTokens.Mint.copy(alpha = 0.5f) else CoworkTokens.Border

    val modeLabel = when (executionMode) {
        ExecutionMode.SWARM -> stringResource(R.string.mode_swarm_short)
        ExecutionMode.FAST -> stringResource(R.string.mode_fast)
    }
    val modeIcon = when (executionMode) {
        ExecutionMode.SWARM -> Icons.Default.Bolt
        ExecutionMode.FAST -> Icons.Default.FlashOn
    }
    val modeBorderColor = if (executionMode == ExecutionMode.SWARM) {
        CoworkTokens.Mint.copy(alpha = 0.45f)
    } else {
        CoworkTokens.Border
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .heightIn(min = 118.dp)
            .clip(CoworkTokens.ShapeComposer)
            .background(CoworkTokens.Surface)
            .border(1.dp, borderColor, CoworkTokens.ShapeComposer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = when {
                        awaitingApproval -> stringResource(R.string.swarm_awaiting_execute)
                        !isApiConfigured -> stringResource(R.string.agent_api_required)
                        isExecuting -> stringResource(R.string.agent_working)
                        else -> stringResource(R.string.agent_hint_focused)
                    },
                    color = CoworkTokens.TextMuted,
                )
            },
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = CoworkTokens.Mint,
                focusedTextColor = CoworkTokens.TextPrimary,
                unfocusedTextColor = CoworkTokens.TextPrimary,
            ),
            interactionSource = interactionSource,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            enabled = !isExecuting,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CoworkTokens.SurfaceElevated),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = CoworkTokens.TextSecondary)
            }

            Spacer(modifier = Modifier.size(8.dp))

            Box {
                Surface(
                    onClick = { modeMenuOpen = true },
                    shape = CoworkTokens.ShapePill,
                    color = CoworkTokens.SurfaceElevated,
                    modifier = Modifier.border(1.dp, modeBorderColor, CoworkTokens.ShapePill),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(modeIcon, null, tint = CoworkTokens.Mint, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = modeLabel,
                            color = CoworkTokens.TextPrimary,
                            fontWeight = FontWeight.Medium,
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        )
                        Icon(Icons.Default.UnfoldMore, null, tint = CoworkTokens.TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mode_swarm_short)) },
                        onClick = { onModeChange(ExecutionMode.SWARM); modeMenuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mode_fast)) },
                        onClick = { onModeChange(ExecutionMode.FAST); modeMenuOpen = false },
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            AnimatedContent(
                targetState = isExecuting,
                transitionSpec = {
                    (fadeIn(Motion.tweenQuick) + scaleIn(Motion.springBouncy))
                        .togetherWith(fadeOut(Motion.tweenQuick) + scaleOut())
                },
                label = "composerAction",
            ) { sending ->
                if (sending) {
                    IconButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, stringResource(R.string.stop_agent), tint = CoworkTokens.Failed)
                    }
                } else if (showSend) {
                    Surface(
                        onClick = { if (canSend) onSend() },
                        shape = CoworkTokens.ShapePill,
                        color = CoworkTokens.SpeakBg,
                        modifier = Modifier.alpha(if (canSend) 1f else 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = CoworkTokens.SpeakFg, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.send), color = CoworkTokens.SpeakFg, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Surface(
                        onClick = { },
                        shape = CoworkTokens.ShapePill,
                        color = CoworkTokens.SpeakBg,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Mic, null, tint = CoworkTokens.SpeakFg, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.speak), color = CoworkTokens.SpeakFg, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
