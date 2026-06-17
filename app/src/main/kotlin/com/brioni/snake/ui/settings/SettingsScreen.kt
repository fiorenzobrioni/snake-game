package com.brioni.snake.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brioni.snake.R
import com.brioni.snake.audio.GameAudio
import com.brioni.snake.data.Settings
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.game.BoardScale
import com.brioni.snake.game.ControlScheme
import com.brioni.snake.game.Level
import com.brioni.snake.game.Skin
import com.brioni.snake.game.SnakeSpeed
import com.brioni.snake.game.SpecialFrequency
import com.brioni.snake.game.ThemeMode
import com.brioni.snake.ui.game.paletteFor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Settings screen: control scheme, difficulty level, board scale and the three
 * audio volumes (master / music / SFX), all persisted via [repo]'s DataStore.
 * Volume changes preview live through [audio] while dragging and persist when
 * the gesture ends.
 */
@Composable
fun SettingsScreen(
    repo: SettingsRepository,
    audio: GameAudio,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by repo.settings.collectAsState(
        initial = Settings(Level.Beginner, BoardScale.Classic, ControlScheme.Swipe),
    )
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        ChoiceSection(
            title = stringResource(R.string.settings_control_scheme),
            options = ControlScheme.entries,
            selected = settings.controlScheme,
            label = { it.displayName },
            onSelected = { scheme -> scope.launch { repo.setControlScheme(scheme) } },
        )

        ChoiceSection(
            title = stringResource(R.string.settings_level),
            options = Level.entries,
            selected = settings.level,
            label = { it.label },
            onSelected = { level -> scope.launch { repo.setLevel(level) } },
        )

        ChoiceSection(
            title = stringResource(R.string.settings_snake_speed),
            options = SnakeSpeed.entries,
            selected = settings.snakeSpeed,
            label = { it.label },
            onSelected = { speed -> scope.launch { repo.setSnakeSpeed(speed) } },
        )

        ChoiceSection(
            title = stringResource(R.string.settings_board_scale),
            options = BoardScale.entries,
            selected = settings.scale,
            label = { it.label },
            onSelected = { scale -> scope.launch { repo.setScale(scale) } },
        )

        SkinSection(
            selected = settings.skin,
            onSelected = { skin -> scope.launch { repo.setSkin(skin) } },
        )

        ChoiceSection(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { it.displayName },
            onSelected = { themeMode -> scope.launch { repo.setThemeMode(themeMode) } },
        )

        ToggleSection(
            title = stringResource(R.string.settings_hazards),
            checked = settings.hazardsEnabled,
            onCheckedChange = { enabled -> scope.launch { repo.setHazardsEnabled(enabled) } },
        )

        ChoiceSection(
            title = stringResource(R.string.settings_special_frequency),
            options = SpecialFrequency.entries,
            selected = settings.specialFrequency,
            label = { it.displayName },
            onSelected = { value -> scope.launch { repo.setSpecialFrequency(value) } },
        )

        VolumeSection(
            title = stringResource(R.string.settings_master_volume),
            value = settings.masterVolume,
            onPreview = { audio.previewVolumes(it, settings.musicVolume, settings.sfxVolume) },
            onCommit = { scope.launch { repo.setMasterVolume(it) } },
        )

        VolumeSection(
            title = stringResource(R.string.settings_music_volume),
            value = settings.musicVolume,
            onPreview = { audio.previewVolumes(settings.masterVolume, it, settings.sfxVolume) },
            onCommit = { scope.launch { repo.setMusicVolume(it) } },
        )

        VolumeSection(
            title = stringResource(R.string.settings_sfx_volume),
            value = settings.sfxVolume,
            onPreview = { audio.previewVolumes(settings.masterVolume, settings.musicVolume, it) },
            onCommit = { scope.launch { repo.setSfxVolume(it) } },
        )

        ToggleSection(
            title = stringResource(R.string.settings_crt_filter),
            checked = settings.crtEnabled,
            onCheckedChange = { enabled -> scope.launch { repo.setCrtEnabled(enabled) } },
        )

        Button(
            onClick = onBack,
            modifier = Modifier.padding(top = 32.dp).widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_menu))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceSection(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        // FlowRow so a section with many chips (e.g. the 5 levels) wraps neatly
        // and centred instead of overflowing the width or inflating the row.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

/**
 * Skin picker: a row of tappable preview cards, each showing the skin's snake +
 * food swatches so the choice is visual rather than a bare label. The selected
 * card is outlined in the theme's primary colour.
 */
@Composable
private fun SkinSection(
    selected: Skin,
    onSelected: (Skin) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.settings_skin),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        // Two cards per row, centred, so the fourth never gets squeezed.
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Skin.entries.chunked(2).forEach { rowSkins ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowSkins.forEach { skin ->
                        SkinCard(
                            skin = skin,
                            selected = skin == selected,
                            onClick = { onSelected(skin) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkinCard(skin: Skin, selected: Boolean, onClick: () -> Unit) {
    val palette = remember(skin) { paletteFor(skin) }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = border,
                shape = RoundedCornerShape(12.dp),
            )
            .background(
                Brush.verticalGradient(listOf(palette.boardTop, palette.boardBottom)),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Swatch(palette.snakeHead)
            Swatch(palette.snakeBody)
            Swatch(palette.growMedium)
            Swatch(palette.shrinkMedium)
        }
        Text(
            text = skin.displayName,
            style = MaterialTheme.typography.labelMedium,
            // The card is always a dark gradient, so use a light caption in both
            // themes (theme-driven onSurface would be black-on-dark in light mode).
            color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Swatch(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

@Composable
private fun ToggleSection(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A 0–100% volume slider. Local state tracks the drag for instant feedback
 * ([onPreview]); the change is persisted only when the gesture ends ([onCommit])
 * to avoid a DataStore write per pixel. Re-syncs if the stored [value] changes
 * externally.
 */
@Composable
private fun VolumeSection(
    title: String,
    value: Float,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { sliderValue = value }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.settings_volume_label, title, (sliderValue * 100).roundToInt()),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                onPreview(it)
            },
            onValueChangeFinished = { onCommit(sliderValue) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
