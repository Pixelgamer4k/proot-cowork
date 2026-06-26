package com.proot.cowork.ui.tabs

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.domain.skills.SkillDefinition
import com.proot.cowork.ui.components.CoworkSectionLabel
import com.proot.cowork.ui.design.CoworkTokens

@Composable
fun SkillsTabContent(
    skills: List<SkillDefinition>,
    skillsDirLabel: String,
    onToggleSkill: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = skills.filter { it.enabled }
    val available = skills.filter { !it.enabled }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                stringResource(R.string.skills_dir_hint, skillsDirLabel),
                color = CoworkTokens.TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
        }
        item { CoworkSectionLabel(stringResource(R.string.skills_active_header, active.size)) }
        if (active.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.skills_empty_active),
                    color = CoworkTokens.TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            items(active, key = { it.id }) { SkillCard(it, onToggleSkill) }
        }
        item { Spacer(Modifier.size(4.dp)); CoworkSectionLabel(stringResource(R.string.skills_available_header, available.size)) }
        if (available.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.skills_empty_available),
                    color = CoworkTokens.TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            items(available, key = { it.id }) { SkillCard(it, onToggleSkill) }
        }
    }
}

@Composable
private fun SkillCard(skill: SkillDefinition, onToggle: (String, Boolean) -> Unit) {
    Surface(
        shape = CoworkTokens.ShapeCard,
        color = CoworkTokens.Surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CoworkTokens.Border, CoworkTokens.ShapeCard)
            .clickable { onToggle(skill.id, !skill.enabled) },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(skill.name, fontWeight = FontWeight.SemiBold, color = CoworkTokens.TextPrimary)
                Icon(
                    if (skill.enabled) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = if (skill.enabled) {
                        stringResource(R.string.skill_disable)
                    } else {
                        stringResource(R.string.skill_enable)
                    },
                    tint = if (skill.enabled) CoworkTokens.Mint else CoworkTokens.TextMuted,
                )
            }
            Text(
                skill.description,
                Modifier.padding(top = 6.dp),
                color = CoworkTokens.TextMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    skill.tags.forEach { tag ->
                        Surface(shape = CoworkTokens.ShapePill, color = CoworkTokens.Mint.copy(alpha = 0.22f)) {
                            Text(
                                tag,
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = CoworkTokens.SpeakFg,
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                if (skill.enabled) {
                    Text(
                        stringResource(R.string.skill_uses, skill.useCount),
                        color = CoworkTokens.TextMuted,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
