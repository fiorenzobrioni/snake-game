package com.brioni.snake.ui.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brioni.snake.R
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.game.DailyChallenge
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Daily Challenge hub: shows today's seeded run (mode / level / board), the
 * player's best for today and their current streak, and launches the run via
 * [onPlay]. Today's [DailyChallenge] is derived from the device date once on
 * entry so the whole screen reads a single, stable challenge.
 */
@Composable
fun DailyChallengeScreen(
    repo: SettingsRepository,
    onPlay: (DailyChallenge) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val challenge = remember(today) { DailyChallenge.forDay(today.toEpochDay()) }
    val best by repo.dailyBest(challenge.epochDay).collectAsState(initial = 0)
    val streak by repo.dailyStreak().collectAsState(initial = 0)
    val lastPlayed by repo.dailyLastPlayedDay().collectAsState(initial = null)

    // The streak is "alive" only if today's or yesterday's daily was played;
    // otherwise an old streak has lapsed and shows as 0 until they play today.
    val liveStreak = when (lastPlayed) {
        challenge.epochDay, challenge.epochDay - 1 -> streak
        else -> 0
    }
    val playedToday = lastPlayed == challenge.epochDay

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.daily_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.daily_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                ConfigRow(stringResource(R.string.daily_row_mode), challenge.mode.displayName)
                ConfigRow(stringResource(R.string.daily_row_level), challenge.level.displayName)
                ConfigRow(stringResource(R.string.daily_row_board), challenge.scale.displayName)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(
                label = stringResource(R.string.daily_best_label),
                value = best.toString(),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.daily_streak_label),
                value = stringResource(R.string.daily_streak_value, liveStreak),
                modifier = Modifier.weight(1f),
            )
        }

        Button(
            onClick = { onPlay(challenge) },
            modifier = Modifier.padding(top = 32.dp).widthIn(min = 220.dp),
        ) {
            Text(
                stringResource(if (playedToday) R.string.daily_play_again else R.string.daily_play),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 12.dp).widthIn(min = 220.dp),
        ) {
            Text(stringResource(R.string.action_menu))
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
