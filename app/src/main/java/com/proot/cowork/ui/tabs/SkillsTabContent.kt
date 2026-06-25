package com.proot.cowork.ui.tabs

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proot.cowork.R

private data class SkillItem(
    val id: String,
    val description: String,
    val tags: List<String>,
    val uses: Int,
    val active: Boolean,
)

private val SKILLS = listOf(
    SkillItem(
        "python-development",
        "Write, debug, and run Python code in the proot environment",
        listOf("python", "coding"),
        47,
        true,
    ),
    SkillItem(
        "web-automation",
        "Browse websites and automate form interactions",
        listOf("browser", "automation"),
        23,
        true,
    ),
    SkillItem(
        "file-management",
        "Organize, search, and manage files in the container",
        listOf("files", "system"),
        31,
        true,
    ),
    SkillItem(
        "data-analysis",
        "Analyze datasets with pandas and visualization tools",
        listOf("data", "python"),
        0,
        false,
    ),
    SkillItem(
        "devops",
        "Manage services, cron jobs, and system packages",
        listOf("system", "automation"),
        0,
        false,
    ),
    SkillItem(
        "api-integration",
        "Connect to REST APIs and webhooks",
        listOf("api", "network"),
        0,
        false,
    ),
)

@Composable
fun SkillsTabContent(
    skillsDirLabel: String,
    modifier: Modifier = Modifier,
) {
    val active = SKILLS.filter { it.active }
    val available = SKILLS.filter { !it.active }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.skills_dir_hint, skillsDirLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                text = stringResource(R.string.skills_active_header, active.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(active) { skill -> SkillCard(skill) }
        item {
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.skills_available_header, available.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(available) { skill -> SkillCard(skill) }
    }
}

@Composable
private fun SkillCard(skill: SkillItem) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(skill.id, fontWeight = FontWeight.SemiBold)
                Icon(
                    imageVector = if (skill.active) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    tint = if (skill.active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = skill.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    skill.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (skill.active) {
                    Text(
                        text = stringResource(R.string.skill_uses, skill.uses),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
