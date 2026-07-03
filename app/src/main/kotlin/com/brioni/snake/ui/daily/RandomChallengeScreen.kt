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
import com.brioni.snake.ui.components.ScreenHeader
import com.brioni.snake.ui.components.SnakeButton
import com.brioni.snake.ui.components.SnakeOutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brioni.snake.R
import com.brioni.snake.game.Challenge

/**
 * The Random Challenge hub: a one-off surprise run for variety. Same layout as the
 * Daily, minus the best-today / streak stats. A "Shuffle" reroll picks a fresh
 * random [Challenge]; "Play" launches it. Nothing is recorded.
 */
@Composable
fun RandomChallengeScreen(
    onPlay: (Challenge) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var challenge by remember { mutableStateOf(Challenge.random()) }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.random_title),
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
                        text = stringResource(R.string.random_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    ConfigRow(stringResource(R.string.daily_row_twist), challenge.modifier.displayName)
                    Text(
                        text = challenge.modifier.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                    ConfigRow(stringResource(R.string.daily_row_mode), challenge.mode.displayName)
                    ConfigRow(stringResource(R.string.daily_row_level), challenge.level.displayName)
                    ConfigRow(stringResource(R.string.daily_row_board), challenge.scale.displayName)
                }
            }

            SnakeOutlinedButton(
                onClick = { challenge = Challenge.random() },
                modifier = Modifier.padding(top = 20.dp).widthIn(min = 220.dp),
            ) {
                Text(stringResource(R.string.random_shuffle))
            }
            SnakeButton(
                onClick = { onPlay(challenge) },
                modifier = Modifier.padding(top = 12.dp).widthIn(min = 220.dp),
            ) {
                Text(stringResource(R.string.random_play), style = MaterialTheme.typography.titleMedium)
            }
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
