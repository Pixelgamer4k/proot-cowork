package com.proot.cowork.ui.tabs

import androidx.compose.foundation.border
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
import com.proot.cowork.ui.components.CoworkSectionLabel
import com.proot.cowork.ui.design.CoworkTokens

private data class SkillItem(val id: String, val description: String, val tags: List<String>, val uses: Int, val active: Boolean)

private val SKILLS = listOf(
    SkillItem("python-development", "Write, debug, and run Python code in the proot environment", listOf("python", "coding"), 47, true),
    SkillItem("web-automation", "Browse websites and automate form interactions", listOf("browser", "automation"), 23, true),
    SkillItem("file-management", "Organize, search, and manage files in the container", listOf("files", "system"), 31, true),
    SkillItem("data-analysis", "Analyze datasets with pandas and visualization tools", listOf("data", "python"), 0, false),
    SkillItem("devops", "Manage services, cron jobs, and system packages", listOf("system", "automation"), 0, false),
    SkillItem("api-integration", "Connect to REST APIs and webhooks", listOf("api", "network"), 0, false),
)

@Composable
fun SkillsTabContent(skillsDirLabel: String, modifier: Modifier = Modifier) {
    val active = SKILLS.filter { it.active }
    val available = SKILLS.filter { !it.active }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text(stringResource(R.string.skills_dir_hint, skillsDirLabel), color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
        item { CoworkSectionLabel(stringResource(R.string.skills_active_header, active.size)) }
        items(active) { SkillCard(it) }
        item { Spacer(Modifier.size(4.dp)); CoworkSectionLabel(stringResource(R.string.skills_available_header, available.size)) }
        items(available) { SkillCard(it) }
    }
}

@Composable
private fun SkillCard(skill: SkillItem) {
    Surface(
        shape = CoworkTokens.ShapeCard,
        color = CoworkTokens.Surface,
        modifier = Modifier.fillMaxWidth().border(1.dp, CoworkTokens.Border, CoworkTokens.ShapeCard),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(skill.id, fontWeight = FontWeight.SemiBold, color = CoworkTokens.TextPrimary)
                Icon(if (skill.active) Icons.Default.Check else Icons.Default.Add, null, tint = if (skill.active) CoworkTokens.Mint else CoworkTokens.TextMuted)
            }
            Text(skill.description, Modifier.padding(top = 6.dp), color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    skill.tags.forEach { tag ->
                        Surface(shape = CoworkTokens.ShapePill, color = CoworkTokens.Mint.copy(alpha = 0.12f)) {
                            Text(tag, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = CoworkTokens.Mint, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (skill.active) Text(stringResource(R.string.skill_uses, skill.uses), color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
        }
    }
}
