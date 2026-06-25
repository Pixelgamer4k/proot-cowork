package com.proot.cowork.ui.home

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import com.proot.cowork.R

enum class CoworkTab(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Chat(R.string.tab_chat, Icons.AutoMirrored.Filled.Chat),
    Agents(R.string.tab_agents, Icons.Default.SmartToy),
    Skills(R.string.tab_skills, Icons.Default.Build),
    Schedule(R.string.tab_schedule, Icons.Default.CalendarMonth),
    Files(R.string.tab_files, Icons.Default.Folder),
    Terminal(R.string.tab_terminal, Icons.Default.Terminal),
}
