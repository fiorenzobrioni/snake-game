package com.brioni.snake.ui.menu

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brioni.snake.R
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.game.Skin
import com.brioni.snake.ui.components.SnakeButton
import com.brioni.snake.ui.components.SnakeOutlinedButton
import com.brioni.snake.ui.game.paletteFor
import kotlinx.coroutines.flow.map

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
            Text(
                text = stringResource(R.string.game_title),
                style = titleStyle,
                modifier = Modifier.scale(titleScale),
            )
            // A small in-game-style snake emblem just beneath the wordmark.
            TitleSnake(
                palette = palette,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(width = 168.dp, height = 38.dp),
            )

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
        }
    }
}
