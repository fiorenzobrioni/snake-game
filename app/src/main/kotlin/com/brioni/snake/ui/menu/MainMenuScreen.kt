package com.brioni.snake.ui.menu

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brioni.snake.R
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.game.Skin
import com.brioni.snake.ui.game.paletteFor
import kotlinx.coroutines.flow.map

/**
 * The app's landing screen: an animated title with Play and Settings actions.
 * Pure navigation host; gameplay state lives in the game ViewModel. The animated
 * AGSL backdrop is provided by the App shell (shared across the menu screens).
 */
@Composable
fun MainMenuScreen(
    repo: SettingsRepository,
    onPlay: () -> Unit,
    onRecords: () -> Unit,
    onAchievements: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "titlePulse")
    val titleScale by pulse.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "titleScale",
    )

    // Recolour the decorations from the player's selected skin.
    val skinFlow = remember(repo) { repo.settings.map { it.skin } }
    val skin by skinFlow.collectAsState(initial = Skin.Classic)
    val palette = paletteFor(skin)

    Box(modifier = modifier.fillMaxSize()) {
        // Discreet decorative layer drawn behind the title and buttons.
        MenuDecorations(palette = palette, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.game_title),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.scale(titleScale),
            )

            Button(
                onClick = onPlay,
                modifier = Modifier.padding(top = 48.dp).widthIn(min = 220.dp),
            ) {
                Text(stringResource(R.string.menu_play), style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                onClick = onRecords,
                modifier = Modifier.padding(top = 12.dp).widthIn(min = 220.dp),
            ) {
                Text(stringResource(R.string.menu_records))
            }
            OutlinedButton(
                onClick = onAchievements,
                modifier = Modifier.padding(top = 12.dp).widthIn(min = 220.dp),
            ) {
                Text(stringResource(R.string.menu_achievements))
            }
            OutlinedButton(
                onClick = onSettings,
                modifier = Modifier.padding(top = 12.dp).widthIn(min = 220.dp),
            ) {
                Text(stringResource(R.string.menu_settings))
            }
        }
    }
}
