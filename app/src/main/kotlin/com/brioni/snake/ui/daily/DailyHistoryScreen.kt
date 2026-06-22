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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import com.brioni.snake.game.Challenge
import com.brioni.snake.ui.components.SnakeButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Number of days shown by the Daily history / weekly screen. */
private const val HISTORY_DAYS = 7

/**
 * Daily history (weekly) screen: the last [HISTORY_DAYS] days of Daily results -
 * best per day plus a "this week" aggregate (best, total, days played). Per-day
 * bests are read in one bulk flow; each row also recovers that day's twist from the
 * deterministic [Challenge.forDay].
 */
@Composable
fun DailyHistoryScreen(
    repo: SettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val endEpochDay = remember(today) { today.toEpochDay() }
    val bests by repo.dailyBests(endEpochDay, HISTORY_DAYS).collectAsState(initial = emptyMap())

    // Only played days are present in [bests], so they drive the aggregates directly.
    val weekBest = bests.values.maxOrNull() ?: 0
    val weekTotal = bests.values.sum()
    val daysPlayed = bests.size

    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.daily_history_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.daily_history_subtitle),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        // This-week aggregate tiles.
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(
                label = stringResource(R.string.daily_history_week_best),
                value = weekBest.toString(),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.daily_history_week_total),
                value = weekTotal.toString(),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.daily_history_days_played),
                value = "$daysPlayed/$HISTORY_DAYS",
                modifier = Modifier.weight(1f),
            )
        }

        // Per-day table, most recent first.
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                for (offset in 0 until HISTORY_DAYS) {
                    val day = endEpochDay - offset
                    val date = LocalDate.ofEpochDay(day)
                    val twist = remember(day) { Challenge.forDay(day).modifier.displayName }
                    val score = bests[day]
                    HistoryRow(
                        date = date.format(dateFormat),
                        twist = twist,
                        score = score?.toString() ?: "-",
                        played = score != null,
                    )
                }
            }
        }

        SnakeButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 24.dp).widthIn(min = 220.dp),
        ) {
            Text(stringResource(R.string.action_menu))
        }
    }
}

@Composable
private fun HistoryRow(date: String, twist: String, score: String, played: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = date,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = twist,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = score,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (played) FontWeight.Bold else FontWeight.Normal,
            color = if (played) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
