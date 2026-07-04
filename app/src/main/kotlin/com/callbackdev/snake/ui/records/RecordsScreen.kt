package com.callbackdev.snake.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.callbackdev.snake.ui.components.ScreenHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope
import com.callbackdev.snake.R
import com.callbackdev.snake.data.SettingsRepository
import com.callbackdev.snake.game.BoardScale
import com.callbackdev.snake.game.GameMode
import com.callbackdev.snake.game.Level
import com.callbackdev.snake.game.LevelsMode
import com.callbackdev.snake.game.ScoreKey
import com.callbackdev.snake.game.ZenMode

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
    val levelsProgress by repo.allLevelsProgress().collectAsState(initial = emptyMap())
    val bestOverall = scores.values.maxOrNull() ?: 0

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.records_title),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameMode.entries.forEach { mode ->
                ModeRecords(mode = mode, scores = scores, levelsProgress = levelsProgress, bestOverall = bestOverall)
            }
        }
    }
}

@Composable
private fun ModeRecords(
    mode: GameMode,
    scores: Map<ScoreKey, Int>,
    levelsProgress: Map<BoardScale, Int>,
    bestOverall: Int,
) {
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

            if (mode == GameMode.Zen) {
                // Zen ignores the difficulty (no obstacles by design): a single
                // score row per scale, keyed on the pinned level.
                RecordRow(label = stringResource(R.string.records_best)) { scale ->
                    val value = scores[ScoreKey(mode, ZenMode.SCORE_LEVEL, scale)] ?: 0
                    ScoreCell(value = value, isTop = value > 0 && value == bestOverall)
                }
            } else if (mode == GameMode.Levels) {
                // Levels ignores the difficulty: a single score row per scale
                // (keyed on the pinned level) plus the deepest level reached.
                RecordRow(label = stringResource(R.string.records_best)) { scale ->
                    val value = scores[ScoreKey(mode, LevelsMode.SCORE_LEVEL, scale)] ?: 0
                    ScoreCell(value = value, isTop = value > 0 && value == bestOverall)
                }
                RecordRow(label = stringResource(R.string.records_best_level)) { scale ->
                    val played = (scores[ScoreKey(mode, LevelsMode.SCORE_LEVEL, scale)] ?: 0) > 0
                    val completed = levelsProgress[scale] ?: 0
                    val text = if (played) {
                        stringResource(
                            R.string.records_level_speed,
                            completed % LevelsMode.LEVEL_COUNT + 1,
                            completed / LevelsMode.LEVEL_COUNT + 1,
                        )
                    } else {
                        "—"
                    }
                    TextCell(text = text, emphasised = false, dimmed = !played)
                }
            } else {
                Level.entries.forEach { level ->
                    RecordRow(label = level.displayName) { scale ->
                        val value = scores[ScoreKey(mode, level, scale)] ?: 0
                        ScoreCell(value = value, isTop = value > 0 && value == bestOverall)
                    }
                }
            }
        }
    }
}

/** One record row: a label then a cell per board scale. */
@Composable
private fun RecordRow(label: String, cell: @Composable RowScope.(BoardScale) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.4f),
        )
        BoardScale.entries.forEach { scale -> cell(scale) }
    }
}

@Composable
private fun RowScope.ScoreCell(value: Int, isTop: Boolean) {
    TextCell(
        text = if (value > 0) value.toString() else "—",
        emphasised = isTop,
        dimmed = value <= 0,
    )
}

@Composable
private fun RowScope.TextCell(text: String, emphasised: Boolean, dimmed: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (emphasised) FontWeight.Bold else FontWeight.Normal,
        color = when {
            emphasised -> MaterialTheme.colorScheme.primary
            !dimmed -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        },
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f),
    )
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
