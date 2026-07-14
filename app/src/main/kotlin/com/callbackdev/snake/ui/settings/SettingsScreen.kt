package com.callbackdev.snake.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import com.callbackdev.snake.ui.components.ScreenHeader
import com.callbackdev.snake.ui.components.SnakeOutlinedButton
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callbackdev.snake.R
import com.callbackdev.snake.audio.GameAudio
import com.callbackdev.snake.data.Settings
import com.callbackdev.snake.data.SettingsRepository
import com.callbackdev.snake.game.BackBehavior
import com.callbackdev.snake.game.BoardScale
import com.callbackdev.snake.game.BoardTerrain
import com.callbackdev.snake.game.ControlScheme
import com.callbackdev.snake.game.Level
import com.callbackdev.snake.game.Skin
import com.callbackdev.snake.game.SpecialFrequency
import com.callbackdev.snake.game.ThemeMode
import com.callbackdev.snake.ui.game.Shaders
import com.callbackdev.snake.ui.game.SkinPalette
import com.callbackdev.snake.ui.game.SnakeEmblem
import com.callbackdev.snake.ui.game.TerrainLayer
import com.callbackdev.snake.ui.game.paletteFor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Settings screen: control scheme, skin, board terrain, theme and the three
 * audio volumes (master / music / SFX), all persisted via [repo]'s DataStore.
 * Volume changes preview live through [audio] while dragging and persist when
 * the gesture ends. Per-run choices (level, snake speed, board scale) live on
 * the Custom Game setup screen instead, so they are not duplicated here.
 *
 * The options are grouped into titled glass cards ([SettingsCard]) — Controls /
 * Appearance / Gameplay / Audio & feedback / Accessibility & help — so the long
 * list scans as a handful of topics instead of a flat column.
 */
@Composable
fun SettingsScreen(
    repo: SettingsRepository,
    audio: GameAudio,
    onBack: () -> Unit,
    onShowTutorial: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by repo.settings.collectAsState(
        initial = Settings(Level.Beginner, BoardScale.Classic, ControlScheme.Swipe),
    )
    val storedUnlockedSkins by repo.unlockedSkins().collectAsState(initial = emptySet())
    // The skins the player may actually select: the always-available ones plus any
    // earned and persisted. During the pre-release preview every skin is selectable.
    val unlockedSkins = remember(storedUnlockedSkins) {
        if (Skin.ALL_UNLOCKED_PREVIEW) {
            Skin.entries.mapTo(mutableSetOf()) { it.name }
        } else {
            Skin.defaultUnlocked.mapTo(mutableSetOf()) { it.name } + storedUnlockedSkins
        }
    }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
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
            SettingsCard(title = stringResource(R.string.settings_section_controls)) {
                ChoiceSection(
                    title = stringResource(R.string.settings_control_scheme),
                    options = ControlScheme.entries,
                    selected = settings.controlScheme,
                    label = { it.displayName },
                    onSelected = { scheme -> scope.launch { repo.setControlScheme(scheme) } },
                )

                // The swipe-distance threshold only applies to the Swipe scheme, so it
                // is surfaced just when that scheme is active.
                if (settings.controlScheme == ControlScheme.Swipe) {
                    SensitivitySection(
                        value = settings.swipeSensitivity,
                        onCommit = { scope.launch { repo.setSwipeSensitivity(it) } },
                    )
                }

                ChoiceSection(
                    title = stringResource(R.string.settings_back_behavior),
                    options = BackBehavior.entries,
                    selected = settings.backBehavior,
                    label = { it.displayName },
                    onSelected = { behavior -> scope.launch { repo.setBackBehavior(behavior) } },
                )
            }

            // Level, Snake speed and Board scale deliberately do NOT appear here:
            // they live on the Custom Game setup screen (same persisted preferences),
            // so Settings stays a home for the app-wide, non-per-run options.

            SettingsCard(title = stringResource(R.string.settings_section_appearance)) {
                SkinSection(
                    selected = settings.skin,
                    unlocked = unlockedSkins,
                    onSelected = { skin -> scope.launch { repo.setSkin(skin) } },
                )

                TerrainSection(
                    selected = settings.terrain,
                    skinPalette = paletteFor(settings.skin),
                    onSelected = { terrain -> scope.launch { repo.setTerrain(terrain) } },
                )

                ChoiceSection(
                    title = stringResource(R.string.settings_theme),
                    options = ThemeMode.entries,
                    selected = settings.themeMode,
                    label = { it.displayName },
                    onSelected = { themeMode -> scope.launch { repo.setThemeMode(themeMode) } },
                )

                ToggleSection(
                    title = stringResource(R.string.settings_crt_filter),
                    checked = settings.crtEnabled,
                    onCheckedChange = { enabled -> scope.launch { repo.setCrtEnabled(enabled) } },
                )
            }

            SettingsCard(title = stringResource(R.string.settings_section_gameplay)) {
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

                // Race a translucent replay of your best run in the same slot (Step 6.9.12).
                ToggleSection(
                    title = stringResource(R.string.settings_ghost_replay),
                    checked = settings.ghostReplayEnabled,
                    onCheckedChange = { enabled -> scope.launch { repo.setGhostReplayEnabled(enabled) } },
                )
            }

            SettingsCard(title = stringResource(R.string.settings_section_audio)) {
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
                    title = stringResource(R.string.settings_haptics),
                    checked = settings.hapticsEnabled,
                    onCheckedChange = { enabled -> scope.launch { repo.setHapticsEnabled(enabled) } },
                )
            }

            SettingsCard(title = stringResource(R.string.settings_section_access)) {
                ToggleSection(
                    title = stringResource(R.string.settings_reduce_motion),
                    checked = settings.reduceMotion,
                    onCheckedChange = { enabled -> scope.launch { repo.setReduceMotion(enabled) } },
                )

                // Replay the first-run tutorial on demand (Step 6.9.16).
                SnakeOutlinedButton(
                    onClick = onShowTutorial,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .widthIn(min = 200.dp)
                        .align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.settings_how_to_play))
                }
            }
        }
    }
}

/**
 * A titled glass card grouping related settings: the faint gradient fill and
 * primary-tinted rim of the `SnakeButtons` family, so the whole Settings screen
 * reads as one system with the menus.
 */
@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val fill = Brush.verticalGradient(
        listOf(scheme.onBackground.copy(alpha = 0.07f), scheme.onBackground.copy(alpha = 0.02f)),
    )
    val rim = Brush.verticalGradient(
        listOf(scheme.primary.copy(alpha = 0.45f), scheme.primary.copy(alpha = 0.12f)),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(shape)
            .background(fill)
            .border(BorderStroke(1.dp, rim), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
        )
        content()
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
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
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
 * Skin picker: a row of tappable preview cards. Each card shows a **live,
 * slithering mini snake** drawn through the real gameplay renderer (so Neon's
 * filament pulses, Aurora's hues flow and Ember's lava breathes exactly as they
 * do in play) over the skin's board gradient, plus the grow/shrink food
 * swatches. The selected card is outlined in the theme's primary colour.
 */
@Composable
private fun SkinSection(
    selected: Skin,
    unlocked: Set<String>,
    onSelected: (Skin) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.settings_skin),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val time = rememberPreviewClock()
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
                            locked = skin.name !in unlocked,
                            time = time,
                            onClick = { onSelected(skin) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkinCard(
    skin: Skin,
    selected: Boolean,
    locked: Boolean,
    time: Float,
    onClick: () -> Unit,
) {
    val palette = remember(skin) { paletteFor(skin) }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    // A locked card is dimmed, shows a lock badge and its unlock hint, and can't be
    // selected (tapping does nothing).
    val contentAlpha = if (locked) 0.4f else 1f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !locked, onClick = onClick)
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
        // The living preview: the in-game snake renderer, slithering in place.
        SnakeEmblem(
            palette = palette,
            time = time,
            waveAmplitude = 0.16f,
            cellFraction = 0.42f,
            contentAlpha = contentAlpha,
            modifier = Modifier.size(width = 116.dp, height = 44.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Swatch(palette.growMedium.copy(alpha = contentAlpha))
            Swatch(palette.shrinkMedium.copy(alpha = contentAlpha))
            if (locked) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.skin_locked),
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.padding(start = 4.dp).size(14.dp),
                )
            }
        }
        Text(
            text = skin.displayName,
            style = MaterialTheme.typography.labelMedium,
            // The card is always a dark gradient, so use a light caption in both
            // themes (theme-driven onSurface would be black-on-dark in light mode).
            color = if (selected) MaterialTheme.colorScheme.primary
            else androidx.compose.ui.graphics.Color.White.copy(alpha = contentAlpha),
            modifier = Modifier.padding(top = 8.dp),
        )
        if (locked) {
            Text(
                text = skin.unlock.requirementText,
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp).widthIn(max = 120.dp),
            )
        }
    }
}

/**
 * Board terrain picker: like the skin picker, a grid of tappable preview cards -
 * but each card is a **live miniature of the terrain's AGSL shader**, so the
 * choice shows the real animated floor rather than a static swatch. The Arcade
 * card renders the skin's own gradient from [skinPalette], matching what the
 * board actually shows when the skin-following floor is selected.
 */
@Composable
private fun TerrainSection(
    selected: BoardTerrain,
    skinPalette: SkinPalette,
    onSelected: (BoardTerrain) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.settings_terrain),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val time = rememberPreviewClock()
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoardTerrain.entries.chunked(2).forEach { rowTerrains ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowTerrains.forEach { terrain ->
                        TerrainCard(
                            terrain = terrain,
                            selected = terrain == selected,
                            skinPalette = skinPalette,
                            time = time,
                            onClick = { onSelected(terrain) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerrainCard(
    terrain: BoardTerrain,
    selected: Boolean,
    skinPalette: SkinPalette,
    time: Float,
    onClick: () -> Unit,
) {
    val layer = remember(terrain) { TerrainLayer(Shaders.menuBackdropSource(terrain)) }
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
            // Always dark, like the skin cards, so the light caption reads in both themes.
            .background(androidx.compose.ui.graphics.Color(0xFF0B0F14), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Canvas(
            modifier = Modifier
                .size(width = 116.dp, height = 58.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            layer.shader.setFloatUniform("origin", 0f, 0f)
            layer.shader.setFloatUniform("resolution", size.width, size.height)
            layer.shader.setFloatUniform("time", time)
            if (terrain == BoardTerrain.Arcade) {
                layer.shader.setColorUniform("topColor", skinPalette.boardTop.toArgb())
                layer.shader.setColorUniform("bottomColor", skinPalette.boardBottom.toArgb())
            } else {
                // A miniature grid pitch so cell-aligned features (e.g. the
                // Meadow checker) read at card scale.
                layer.shader.setFloatUniform("cellPx", size.width / 9f)
            }
            drawRect(brush = layer.brush)
        }
        Text(
            text = terrain.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * One slow, linear seconds-clock shared by the animated preview cards (the
 * slithering skin snakes and the terrain shaders), so every preview breathes in
 * the same rhythm.
 */
@Composable
private fun rememberPreviewClock(): Float {
    val transition = rememberInfiniteTransition(label = "previewClock")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = PREVIEW_LOOP_SECONDS,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = (PREVIEW_LOOP_SECONDS * 1000).toInt(), easing = LinearEasing),
        ),
        label = "previewTime",
    )
    return time
}

/** Preview clock length; long enough that the restart jump is a non-event. */
private const val PREVIEW_LOOP_SECONDS = 120f

@Composable
private fun Swatch(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

/**
 * The swipe sensitivity slider (0..100%). Local state tracks the drag for instant
 * feedback; the value is persisted only when the gesture ends to avoid a DataStore
 * write per pixel. 50% reproduces the game's tuned default swipe distance.
 */
@Composable
private fun SensitivitySection(
    value: Float,
    onCommit: (Float) -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { sliderValue = value }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.settings_swipe_sensitivity, (sliderValue * 100).roundToInt()),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onCommit(sliderValue) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ToggleSection(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
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
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
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
