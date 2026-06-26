package com.proot.cowork.ui.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.proot.cowork.R
import com.proot.cowork.domain.skills.PendingSkillWrite
import com.proot.cowork.domain.skills.SkillSaveOffer
import com.proot.cowork.domain.skills.SkillWriteAction
import com.proot.cowork.ui.design.CoworkTokens

@Composable
fun SkillApprovalCard(
    pending: PendingSkillWrite,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionLabel = when (pending.action) {
        SkillWriteAction.CREATE -> stringResource(R.string.skill_action_create)
        SkillWriteAction.UPDATE -> stringResource(R.string.skill_action_update)
        SkillWriteAction.DELETE -> stringResource(R.string.skill_action_delete)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CoworkTokens.ShapeCard,
        color = CoworkTokens.Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CoworkTokens.Border, CoworkTokens.ShapeCard)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.skill_approval_title),
                fontWeight = FontWeight.SemiBold,
                color = CoworkTokens.TextPrimary,
            )
            Text(
                stringResource(R.string.skill_approval_body, actionLabel, pending.skillId),
                style = MaterialTheme.typography.bodySmall,
                color = CoworkTokens.TextSecondary,
            )
            Text(
                pending.reason,
                style = MaterialTheme.typography.bodySmall,
                color = CoworkTokens.TextMuted,
            )
            if (!pending.content.isNullOrBlank() && pending.action != SkillWriteAction.DELETE) {
                Text(
                    pending.content.lines().take(8).joinToString("\n"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CoworkTokens.SurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoworkTokens.TextSecondary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoworkTokens.Mint, contentColor = CoworkTokens.SpeakFg),
                ) {
                    Text(stringResource(R.string.skill_approve))
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.skill_reject))
                }
            }
        }
    }
}

@Composable
fun SkillSaveOfferCard(
    offer: SkillSaveOffer,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CoworkTokens.ShapeCard,
        color = CoworkTokens.Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CoworkTokens.Mint.copy(alpha = 0.35f), CoworkTokens.ShapeCard)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.skill_save_offer_title),
                fontWeight = FontWeight.SemiBold,
                color = CoworkTokens.TextPrimary,
            )
            Text(
                stringResource(R.string.skill_save_offer_body, offer.toolCallCount, offer.title),
                style = MaterialTheme.typography.bodySmall,
                color = CoworkTokens.TextSecondary,
            )
            Text(
                stringResource(R.string.skill_save_offer_id, offer.skillId),
                style = MaterialTheme.typography.labelMedium,
                color = CoworkTokens.Mint,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoworkTokens.Mint, contentColor = CoworkTokens.SpeakFg),
                ) {
                    Text(stringResource(R.string.skill_save_offer_save))
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.skill_save_offer_dismiss))
                }
            }
        }
    }
}
