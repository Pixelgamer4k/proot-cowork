package com.proot.cowork.ui.agent

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.domain.agent.ExecutionMode
import com.proot.cowork.ui.theme.Motion

@Composable
fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isExecuting: Boolean,
    executionMode: ExecutionMode,
    onModeChange: (ExecutionMode) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        onFocusChange(isFocused)
    }

    val canSend = value.isNotBlank() && !isExecuting
    val sendScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.88f,
        animationSpec = Motion.springSnappy,
        label = "sendScale",
    )

    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    }
    var modeMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = when {
                        isExecuting -> "Agent running…"
                        isFocused -> stringResource(R.string.agent_hint_focused)
                        else -> stringResource(R.string.agent_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            interactionSource = interactionSource,
            maxLines = if (isFocused) 5 else 2,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            enabled = !isExecuting,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Surface(
                    onClick = { modeMenuOpen = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = when (executionMode) {
                                ExecutionMode.PLAN -> stringResource(R.string.mode_swarm_plan)
                                ExecutionMode.DIRECT -> stringResource(R.string.mode_swarm_direct)
                                ExecutionMode.SCHEDULE -> stringResource(R.string.mode_swarm_schedule)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.plan_mode)) },
                        onClick = {
                            onModeChange(ExecutionMode.PLAN)
                            modeMenuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.direct_mode)) },
                        onClick = {
                            onModeChange(ExecutionMode.DIRECT)
                            modeMenuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.schedule_mode)) },
                        onClick = {
                            onModeChange(ExecutionMode.SCHEDULE)
                            modeMenuOpen = false
                        },
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
                label = "composerTrailing",
            ) { sending ->
                if (sending) {
                    IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = stringResource(R.string.stop_agent),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    IconButton(
                        onClick = onSend,
                        enabled = canSend,
                        modifier = Modifier
                            .size(40.dp)
                            .scale(sendScale),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.send),
                            tint = if (canSend) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier
                                .background(
                                    if (canSend) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    } else {
                                        Color.Transparent
                                    },
                                    CircleShape,
                                )
                                .padding(6.dp),
                        )
                    }
                }
            }
        }
    }
}
