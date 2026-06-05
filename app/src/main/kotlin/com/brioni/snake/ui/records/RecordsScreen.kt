package com.brioni.snake.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brioni.snake.R
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.game.BoardScale
import com.brioni.snake.game.GameMode
import com.brioni.snake.game.Level
import com.brioni.snake.game.ScoreKey

/**
 * Records screen: best scores laid out as a (level × board-scale) table for each
 * play mode. Reads every stored highscore in one bulk flow and shows a dash for
 * slots not yet played.
 */
@Composable
fun RecordsScreen(
    repo: SettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scores by repo.allHighScores().collectAsState(initial = emptyMap())
    val bestOverall = scores.values.maxOrNull() ?: 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.records_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        GameMode.entries.forEach { mode ->
            ModeRecords(mode = mode, scores = scores, bestOverall = bestOverall)
        }

        Button(
            onClick = onBack,
            modifier = Modifier.padding(top = 28.dp).widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_menu))
        }
    }
}

@Composable
private fun ModeRecords(mode: GameMode, scores: Map<ScoreKey, Int>, bestOverall: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = mode.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // Header row: a blank corner then each board scale.
            Row(modifier = Modifier.fillMaxWidth()) {
                HeaderCell("", weight = 1.4f)
                BoardScale.entries.forEach { scale -> HeaderCell(scale.displayName, weight = 1f) }
            }

            Level.entries.forEach { level ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(
                        text = level.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1.4f),
                    )
                    BoardScale.entries.forEach { scale ->
                        val value = scores[ScoreKey(mode, level, scale)] ?: 0
                        val isTop = value > 0 && value == bestOverall
                        Text(
                            text = if (value > 0) value.toString() else "—",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isTop -> MaterialTheme.colorScheme.primary
                                value > 0 -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(weight),
    )
}
