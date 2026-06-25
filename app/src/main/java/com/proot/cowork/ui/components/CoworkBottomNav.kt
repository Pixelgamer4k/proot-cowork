package com.proot.cowork.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proot.cowork.ui.design.CoworkTokens
import com.proot.cowork.ui.home.CoworkTab
import com.proot.cowork.ui.theme.Motion

@Composable
fun CoworkBottomNav(
    selected: CoworkTab,
    onSelect: (CoworkTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(CoworkTokens.NavHeight)
            .background(CoworkTokens.SurfaceElevated.copy(alpha = 0.98f))
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoworkTab.entries.forEach { tab ->
            CoworkNavItem(tab = tab, selected = tab == selected, onClick = { onSelect(tab) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CoworkNavItem(
    tab: CoworkTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = Motion.springSnappy,
        label = "navScale",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) CoworkTokens.Mint else CoworkTokens.TextMuted,
        animationSpec = Motion.tweenColorQuick,
        label = "navTint",
    )
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(width = 20.dp, height = 2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(CoworkTokens.Mint),
            )
        } else {
            Box(modifier = Modifier.size(width = 20.dp, height = 2.dp))
        }
        Icon(
            imageVector = tab.icon,
            contentDescription = stringResource(tab.labelRes),
            tint = tint,
            modifier = Modifier.scale(scale).size(21.dp),
        )
        Text(
            text = stringResource(tab.labelRes),
            color = tint,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
