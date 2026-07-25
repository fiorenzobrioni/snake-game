package com.callbackdev.snake.ui.achievements

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.callbackdev.snake.ui.components.ScreenHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.callbackdev.snake.R
import com.callbackdev.snake.data.SettingsRepository
import com.callbackdev.snake.game.Achievement
import com.callbackdev.snake.game.AchievementTier

/**
 * Achievements screen, laid out as a **career ladder** rather than one flat list:
 * the player's rank leads, with a bar toward the next one, and the badges are
 * grouped into tiers that open up as the total climbs. A sealed tier says exactly
 * what it costs to reveal and how many badges it is holding back - a goal, not a
 * blank. Unlock state is read live from [repo].
 */
@Composable
fun AchievementsScreen(
    repo: SettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unlocked by repo.unlockedAchievements().collectAsState(initial = emptySet())
    val total = unlocked.size
    val rank = AchievementTier.rankFor(total)
    val next = AchievementTier.nextAfter(total)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.achievements_title),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RankCard(rank = rank, next = next, total = total)

            AchievementTier.entries.forEach { tier ->
                val revealed = tier.isRevealedAt(total)
                val earned = tier.achievements.count { it.name in unlocked }
                TierHeader(tier = tier, earned = earned, revealed = revealed)
                if (revealed) {
                    tier.achievements.forEach { achievement ->
                        AchievementCard(achievement, isUnlocked = achievement.name in unlocked)
                    }
                } else {
                    SealedTierCard(tier = tier, total = total)
                }
            }
        }
    }
}

/**
 * The player's standing: the rank name over a bar filling toward the next one.
 * This is the reward the whole ladder pays out, so it leads the screen with the
 * loudest card - the glassy accent treatment the menu tiles wear.
 */
@Composable
private fun RankCard(rank: AchievementTier, next: AchievementTier?, total: Int) {
    val scheme = MaterialTheme.colorScheme
    val target = if (next == null) {
        1f
    } else {
        val from = rank.revealAt
        ((total - from).toFloat() / (next.revealAt - from)).coerceIn(0f, 1f)
    }
    val progress by animateFloatAsState(targetValue = target, label = "rankProgress")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(scheme.primary.copy(alpha = 0.22f), scheme.primary.copy(alpha = 0.06f)),
                ),
            )
            .border(
                BorderStroke(
                    1.5.dp,
                    Brush.verticalGradient(
                        listOf(scheme.primary.copy(alpha = 0.70f), scheme.primary.copy(alpha = 0.20f)),
                    ),
                ),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.achievements_rank_label),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onBackground.copy(alpha = 0.7f),
        )
        Text(
            text = rank.displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.achievements_progress, total, Achievement.entries.size),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 6.dp),
        )
        // The bar toward the next rank (full, and captioned, once topped out).
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(scheme.onBackground.copy(alpha = 0.14f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(scheme.primary.copy(alpha = 0.75f), scheme.primary),
                        ),
                    ),
            )
        }
        Text(
            text = if (next == null) {
                stringResource(R.string.achievements_rank_max)
            } else {
                stringResource(R.string.achievements_rank_next, next.revealAt - total, next.displayName)
            },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onBackground.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** A tier's divider: its name, its earned count, and a lock while it is sealed. */
@Composable
private fun TierHeader(tier: AchievementTier, earned: Int, revealed: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A small pip in the accent (dimmed while sealed) marks the rank.
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (revealed) scheme.primary else scheme.onBackground.copy(alpha = 0.3f)),
        )
        Text(
            text = tier.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (revealed) scheme.onBackground else scheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        Text(
            text = if (revealed) {
                stringResource(R.string.achievements_tier_count, earned, tier.achievements.size)
            } else {
                stringResource(R.string.achievements_tier_sealed)
            },
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

/** A sealed tier: what it costs to open, and how many badges are waiting inside. */
@Composable
private fun SealedTierCard(tier: AchievementTier, total: Int) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.onBackground.copy(alpha = 0.05f))
            .border(
                BorderStroke(1.dp, scheme.onBackground.copy(alpha = 0.14f)),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.achievements_tier_locked, tier.revealAt - total),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onBackground.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.achievements_tier_hidden, tier.achievements.size),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onBackground.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** One badge: earned in the accent, still to come in glass. */
@Composable
private fun AchievementCard(achievement: Achievement, isUnlocked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isUnlocked) {
                    Brush.verticalGradient(
                        listOf(scheme.primary.copy(alpha = 0.20f), scheme.primary.copy(alpha = 0.06f)),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(scheme.onBackground.copy(alpha = 0.07f), scheme.onBackground.copy(alpha = 0.02f)),
                    )
                },
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (isUnlocked) scheme.primary.copy(alpha = 0.45f) else scheme.onBackground.copy(alpha = 0.12f),
                ),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isUnlocked) "★" else "☆",
            style = MaterialTheme.typography.headlineSmall,
            color = if (isUnlocked) scheme.primary else scheme.onSurface.copy(alpha = 0.35f),
            modifier = Modifier.padding(end = 14.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isUnlocked) scheme.onSurface else scheme.onSurface.copy(alpha = 0.65f),
            )
            Text(
                text = achievement.description,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface.copy(alpha = if (isUnlocked) 0.75f else 0.6f),
            )
        }
    }
}
