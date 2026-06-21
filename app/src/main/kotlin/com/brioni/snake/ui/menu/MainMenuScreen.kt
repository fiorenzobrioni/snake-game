package com.brioni.snake.ui.menu

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.brioni.snake.R
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.game.Mission
import com.brioni.snake.game.Skin
import com.brioni.snake.ui.components.SnakeButton
import com.brioni.snake.ui.components.SnakeOutlinedButton
import com.brioni.snake.ui.game.SnakeEmblem
import com.brioni.snake.ui.game.paletteFor
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * The app's landing screen: a branded title with a small skin-coloured snake
 * emblem beneath it, plus the Play / navigation actions. Pure navigation host;
 * gameplay state lives in the game ViewModel. The animated AGSL backdrop is
 * provided by the App shell (shared across the menu screens).
 */
@Composable
fun MainMenuScreen(
    repo: SettingsRepository,
    onPlay: () -> Unit,
    onCustom: () -> Unit,
    onDaily: () -> Unit,
    onRandom: () -> Unit,
    onRecords: () -> Unit,
    onAchievements: () -> Unit,
    onSettings: () -> Unit,
    onCredits: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "titlePulse")
    val titleScale by pulse.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "titleScale",
    )

    // The title + emblem reflect the player's selected skin.
    val skinFlow = remember(repo) { repo.settings.map { it.skin } }
    val skin by skinFlow.collectAsState(initial = Skin.Classic)
    val palette = paletteFor(skin)

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    // A vertical green gradient + a soft same-hue glow give the wordmark depth and
    // "character" instead of a flat solid fill.
    val titleStyle = MaterialTheme.typography.displayLarge.merge(
        TextStyle(
            brush = Brush.verticalGradient(listOf(primary, secondary)),
            shadow = Shadow(color = primary.copy(alpha = 0.5f), blurRadius = 34f),
            fontWeight = FontWeight.Bold,
        ),
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Measure the laid-out wordmark so the emblem below can match its width.
            var titleSize by remember { mutableStateOf(IntSize.Zero) }
            Text(
                text = stringResource(R.string.game_title),
                style = titleStyle,
                modifier = Modifier.scale(titleScale),
                onTextLayout = { titleSize = it.size },
            )
            // A static, in-game-accurate snake (drawn through the gameplay renderer)
            // beneath the wordmark, matching the active skin. It is drawn a touch
            // wider than the word (the emblem renderer just fits more cells, so the
            // body thickness is unchanged) so the snake reads as a little longer than
            // the title - it sits better graphically than an exact word-width match.
            if (titleSize.width > 0) {
                val density = LocalDensity.current
                val emblemWidth = with(density) { (titleSize.width * EMBLEM_WIDTH_FACTOR).toDp() }
                val emblemHeight = with(density) { (titleSize.height * 0.42f).toDp() }
                SnakeEmblem(
                    palette = palette,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(width = emblemWidth, height = emblemHeight),
                )
            }

            SnakeButton(
                onClick = onPlay,
                modifier = Modifier.padding(top = 44.dp).widthIn(min = 220.dp),
            ) {
                Text(stringResource(R.string.menu_play), style = MaterialTheme.typography.titleMedium)
            }
            SnakeOutlinedButton(
                onClick = onCustom,
                modifier = Modifier.padding(top = 12.dp).widthIn(min = 220.dp),
            ) {
                Text(stringResource(R.string.menu_custom))
            }
            SnakeOutlinedButton(
                onClick = onDaily,
                modifier = Modifier.padding(top = 12.dp).widthIn(min = 220.dp),
            ) {
                Text(stringResource(R.string.menu_daily))
            }
            SnakeOutlinedButton(
                onClick = onRandom,
                modifier = Modifier.padding(top = 12.dp).widthIn(min = 220.dp),
            ) {
                Text(stringResource(R.string.menu_random))
            }
            SnakeOutlinedButton(
                onClick = onRecords,
                modifier = Modifier.padding(top = 12.dp).widthIn(min = 220.dp),
            ) {
                Text(stringResource(R.string.menu_records))
            }
            SnakeOutlinedButton(
                onClick = onAchievements,
                modifier = Modifier.padding(top = 12.dp).widthIn(min = 220.dp),
            ) {
                Text(stringResource(R.string.menu_achievements))
            }
            SnakeOutlinedButton(
                onClick = onSettings,
                modifier = Modifier.padding(top = 12.dp).widthIn(min = 220.dp),
            ) {
                Text(stringResource(R.string.menu_settings))
            }
            SnakeOutlinedButton(
                onClick = onCredits,
                modifier = Modifier.padding(top = 12.dp).widthIn(min = 220.dp),
            ) {
                Text(stringResource(R.string.menu_credits))
            }

            DailyMissionsCard(
                repo = repo,
                modifier = Modifier
                    .padding(top = 28.dp)
                    .widthIn(max = 320.dp),
            )
        }
    }
}

/**
 * A compact "Today's Missions" card (Step 6.9.5): the day's rotating per-run goals
 * with a tick on the ones already completed today, so a single run has a sense of
 * purpose. Completion is read back from [SettingsRepository]; progress within a run
 * is shown on the game-over banner.
 */
@Composable
private fun DailyMissionsCard(repo: SettingsRepository, modifier: Modifier = Modifier) {
    val epochDay = remember { LocalDate.now().toEpochDay() }
    val missions = remember(epochDay) { Mission.forDay(epochDay) }
    val done by repo.completedMissionsForDay(epochDay).collectAsState(initial = emptySet())
    val completedCount = missions.count { it.id in done }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.missions_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = stringResource(R.string.missions_progress, completedCount, missions.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
        missions.forEach { mission ->
            val isDone = mission.id in done
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isDone) "✓" else "○",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDone) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                )
                Text(
                    text = mission.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDone) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

/** The menu emblem is drawn this much wider than the wordmark (a little longer). */
private const val EMBLEM_WIDTH_FACTOR = 1.18f
