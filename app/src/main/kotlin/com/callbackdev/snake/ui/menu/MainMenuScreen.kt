package com.callbackdev.snake.ui.menu

import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.callbackdev.snake.BuildConfig
import com.callbackdev.snake.R
import com.callbackdev.snake.data.SettingsRepository
import com.callbackdev.snake.game.Mission
import com.callbackdev.snake.game.Skin
import com.callbackdev.snake.ui.components.MenuIconButton
import com.callbackdev.snake.ui.components.MenuIcons
import com.callbackdev.snake.ui.components.MenuTile
import com.callbackdev.snake.ui.components.SnakeButton
import com.callbackdev.snake.ui.game.SnakeEmblem
import com.callbackdev.snake.ui.game.paletteFor
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Master switch for the debug-only "unlock all themes" shortcut. Kept `false` for now so
 * the button is hidden and inert even in debug APKs; flip to `true` (and run a debug
 * build) to bring it back when grinding the unlock conditions becomes tedious.
 */
private const val SHOW_DEBUG_UNLOCK_SKINS = false

/**
 * The app's landing screen, laid out as a "game launcher": the brand (a skin-
 * coloured wordmark + snake emblem) is the hero in the top region, while every
 * action is grouped into a compact cluster anchored at the bottom (thumb reach).
 * Actions read by type - the primary Play, a "modes" shelf and a "progress"
 * shelf - with Settings / Credits demoted to small overflow icons in the top
 * corner. Pure navigation host; gameplay state lives in the game ViewModel. The
 * animated AGSL backdrop is provided by the App shell (shared across menus).
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

    Box(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // BRAND region: absorbs the free space so the control cluster stays
            // bottom-anchored and fully visible without scrolling.
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Measure the laid-out wordmark so the emblem below matches its width.
                var titleSize by remember { mutableStateOf(IntSize.Zero) }
                Text(
                    text = stringResource(R.string.game_title),
                    style = titleStyle,
                    modifier = Modifier.scale(titleScale),
                    onTextLayout = { titleSize = it.size },
                )
                // A static, in-game-accurate snake (drawn through the gameplay
                // renderer) beneath the wordmark, matching the active skin. It is
                // drawn a touch wider than the word so it reads as a little longer
                // than the title - it sits better graphically than an exact match.
                if (titleSize.width > 0) {
                    val density = LocalDensity.current
                    val emblemWidth = with(density) { (titleSize.width * EMBLEM_WIDTH_FACTOR).toDp() }
                    val emblemHeight = with(density) { (titleSize.height * 0.46f).toDp() }
                    SnakeEmblem(
                        palette = palette,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .size(width = emblemWidth, height = emblemHeight),
                    )
                }
            }

            // CONTROLS cluster: grouped by type, anchored at the bottom.
            MissionsStrip(repo = repo, modifier = Modifier.fillMaxWidth())

            SnakeButton(
                onClick = onPlay,
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.menu_play), style = MaterialTheme.typography.titleMedium)
            }

            // Modes shelf.
            Row(
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MenuTile(
                    onClick = onCustom,
                    icon = MenuIcons.Tune,
                    label = stringResource(R.string.menu_custom_game),
                    modifier = Modifier.weight(1f),
                )
                MenuTile(
                    onClick = onDaily,
                    icon = MenuIcons.Calendar,
                    label = stringResource(R.string.menu_daily),
                    modifier = Modifier.weight(1f),
                )
                MenuTile(
                    onClick = onRandom,
                    icon = MenuIcons.Dice,
                    label = stringResource(R.string.menu_random),
                    modifier = Modifier.weight(1f),
                )
            }

            // Progress shelf.
            Row(
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MenuTile(
                    onClick = onRecords,
                    icon = MenuIcons.Trophy,
                    label = stringResource(R.string.menu_records),
                    modifier = Modifier.weight(1f),
                )
                MenuTile(
                    onClick = onAchievements,
                    icon = MenuIcons.Medal,
                    label = stringResource(R.string.menu_achievements),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Overflow: low-emphasis system entries pinned in the top corner.
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Debug-only shortcut: unlock every skin so they can be tried without
            // grinding the unlock conditions. Wrapped in BuildConfig.DEBUG so it is
            // compiled out of release APKs / the store bundle, and additionally gated by
            // SHOW_DEBUG_UNLOCK_SKINS so it stays hidden even in debug builds for now.
            if (BuildConfig.DEBUG && SHOW_DEBUG_UNLOCK_SKINS) {
                val scope = rememberCoroutineScope()
                MenuIconButton(
                    onClick = {
                        scope.launch { repo.addUnlockedSkins(Skin.entries.map { it.name }) }
                    },
                    icon = Icons.Filled.Build,
                    contentDescription = stringResource(R.string.menu_debug_unlock_themes),
                )
            }
            MenuIconButton(
                onClick = onSettings,
                icon = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.menu_settings),
            )
            MenuIconButton(
                onClick = onCredits,
                icon = Icons.Filled.Info,
                contentDescription = stringResource(R.string.menu_credits),
            )
        }
    }
}

/**
 * A slim, always-visible "Today's Missions" strip (Step 6.9.5): the day's
 * completion count plus a tick/circle pip per rotating goal, so a single run has
 * a sense of purpose without eating vertical space. Tapping it opens a small
 * dialog listing each goal's description and status (so the names are readable on
 * demand without growing the menu). Completion is read back from
 * [SettingsRepository].
 */
@Composable
private fun MissionsStrip(repo: SettingsRepository, modifier: Modifier = Modifier) {
    val epochDay = remember { LocalDate.now().toEpochDay() }
    val missions = remember(epochDay) { Mission.forDay(epochDay) }
    val done by repo.completedMissionsForDay(epochDay).collectAsState(initial = emptySet())
    val completedCount = missions.count { it.id in done }
    var showDetails by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
            .clickable { showDetails = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.missions_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            missions.forEach { mission ->
                MissionPip(
                    done = mission.id in done,
                    modifier = Modifier.padding(start = 8.dp).size(14.dp),
                )
            }
            Text(
                text = stringResource(R.string.missions_progress, completedCount, missions.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 12.dp),
            )
            // A subtle affordance that the strip opens the full list.
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp).size(18.dp),
            )
        }
    }

    if (showDetails) {
        MissionsDialog(
            missions = missions,
            done = done,
            onDismiss = { showDetails = false },
        )
    }
}

/**
 * A compact branded dialog listing the day's missions with their descriptions and
 * a tick / circle per goal. Opened from [MissionsStrip] so the goal names are
 * readable without permanently growing the menu.
 */
@Composable
private fun MissionsDialog(
    missions: List<Mission>,
    done: Set<String>,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // The same glassy rim the buttons and tiles wear, so the popup reads native.
    val rim = Brush.verticalGradient(
        listOf(scheme.primary.copy(alpha = 0.55f), scheme.primary.copy(alpha = 0.15f)),
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.surface)
                .border(BorderStroke(1.dp, rim), RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = stringResource(R.string.missions_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.secondary,
            )
            missions.forEach { mission ->
                val isDone = mission.id in done
                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MissionPip(done = isDone, modifier = Modifier.size(16.dp))
                    Text(
                        text = mission.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDone) scheme.onSurface
                        else scheme.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

/**
 * A drawn mission pip: a filled accent disc with a dark tick when [done], a
 * faint ring otherwise — replacing the old "✓" / "○" text glyphs, whose weight
 * and baseline shifted with the system font.
 */
@Composable
private fun MissionPip(done: Boolean, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
    Canvas(modifier = modifier) {
        val w = size.width
        if (done) {
            drawCircle(fill)
            // The tick, in the same dark ink the filled buttons use.
            val tick = Path().apply {
                moveTo(w * 0.28f, w * 0.52f)
                lineTo(w * 0.44f, w * 0.70f)
                lineTo(w * 0.74f, w * 0.34f)
            }
            drawPath(
                path = tick,
                color = androidx.compose.ui.graphics.Color(0xFF0A0E10),
                style = Stroke(width = w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        } else {
            drawCircle(idle, radius = w * 0.44f, style = Stroke(width = w * 0.12f))
        }
    }
}

/** The menu emblem is drawn this much wider than the wordmark (a little longer). */
private const val EMBLEM_WIDTH_FACTOR = 1.18f
