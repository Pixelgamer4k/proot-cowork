package com.proot.cowork.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.proot.cowork.ui.design.CoworkTokens

@Composable
fun CoworkCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CoworkTokens.Border, CoworkTokens.ShapeCard),
        shape = CoworkTokens.ShapeCard,
        color = CoworkTokens.Surface,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun CoworkSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        modifier = modifier.padding(bottom = 8.dp),
    )
}
