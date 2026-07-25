package com.callbackdev.snake.ui.help

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callbackdev.snake.R
import com.callbackdev.snake.game.Achievement
import com.callbackdev.snake.game.AchievementTier
import com.callbackdev.snake.game.EndlessWaves
import com.callbackdev.snake.game.GameEngine
import com.callbackdev.snake.game.GameState
import com.callbackdev.snake.game.GrowthRate
import com.callbackdev.snake.game.LevelsMode
import com.callbackdev.snake.game.Mission
import com.callbackdev.snake.ui.components.ScreenHeader
import kotlin.math.roundToInt

/**
 * The in-app **Guide**: the rules in reference form, for the questions the tour
 * deliberately does not answer ("how much does a Shed actually cut?").
 *
 * Two rules keep it honest and maintenance-free:
 *
 * 1. **The numbers come from the model, never from prose.** Every figure below is
 *    interpolated from the constants the engine actually runs on ([GrowthRate],
 *    [GameState.MAX_RISK_FACTOR], [GameEngine.SHED_FRACTION], [EndlessWaves],
 *    [LevelsMode], ...). Re-tune the balance and this screen re-tunes itself - it
 *    cannot drift into lying, which is the usual fate of a hand-written manual.
 * 2. **It complements the tour, it does not repeat it.** The onboarding cards sell
 *    *why* to play; this is *how it works*, in collapsed sections a player opens
 *    only when they want the detail.
 */
@Composable
fun HowToPlayScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.guide_title),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.guide_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                modifier = Modifier.padding(bottom = 6.dp),
            )

            // --- The basics ---------------------------------------------------
            GuideSection(title = stringResource(R.string.guide_basics_title)) {
                Bullet(stringResource(R.string.guide_basics_goal))
                Bullet(stringResource(R.string.guide_basics_death))
                Bullet(stringResource(R.string.guide_basics_grace))
                Bullet(stringResource(R.string.guide_basics_floor, GameEngine.MIN_SNAKE_LENGTH))
            }

            // --- Length, risk and Shed: the heart of the game -----------------
            GuideSection(title = stringResource(R.string.guide_length_title)) {
                Bullet(stringResource(R.string.guide_length_growth))
                // The growth dial, straight out of the enum.
                GrowthRate.entries.forEach { rate ->
                    KeyValue(
                        key = "${rate.ordinal + 1}. ${rate.displayName}",
                        value = if (rate.isOn) {
                            stringResource(
                                R.string.guide_length_growth_row,
                                rate.baseIntervalTicks,
                                rate.scoreFactorLabel,
                            )
                        } else {
                            stringResource(R.string.guide_length_growth_off)
                        },
                    )
                }
                Bullet(stringResource(R.string.guide_length_growth_scaling))
                Bullet(
                    stringResource(
                        R.string.guide_length_risk,
                        (GameState.RISK_FULL_FILL * 100).roundToInt(),
                        GameState.MAX_RISK_FACTOR.roundToInt(),
                    ),
                )
                Bullet(stringResource(R.string.guide_length_risk_hud))
                Bullet(
                    stringResource(
                        R.string.guide_length_shed,
                        GameEngine.ABILITY_CHARGE_FULL,
                        GameEngine.ABILITY_COMBO_BONUS_AT,
                        (GameEngine.SHED_FRACTION * 100).roundToInt(),
                    ),
                )
                Bullet(stringResource(R.string.guide_length_shed_payout))
            }

            // --- Food ---------------------------------------------------------
            GuideSection(title = stringResource(R.string.guide_food_title)) {
                Bullet(stringResource(R.string.guide_food_grow))
                Bullet(stringResource(R.string.guide_food_shrink))
                Bullet(
                    stringResource(
                        R.string.guide_food_shrink_cap,
                        (GameEngine.MAX_SHRINK_FRACTION * 100).roundToInt(),
                    ),
                )
                Bullet(stringResource(R.string.guide_food_maxi))
                Bullet(stringResource(R.string.guide_food_mystery))
                Bullet(stringResource(R.string.guide_food_vanish, GameEngine.VANISH_FOOD_MS / 1000))
            }

            // --- Power-ups and hazards ---------------------------------------
            GuideSection(title = stringResource(R.string.guide_specials_title)) {
                Bullet(stringResource(R.string.guide_specials_intro))
                // Named, not drawn: the tour's legend already shows the real tokens,
                // so here the words do the work and stay consistent with the HUD's
                // effect chips (which use these very strings).
                KeyValue(stringResource(R.string.effect_lightning), stringResource(R.string.guide_specials_lightning))
                KeyValue(stringResource(R.string.effect_star), stringResource(R.string.guide_specials_star))
                KeyValue(stringResource(R.string.effect_freeze), stringResource(R.string.guide_specials_freeze))
                KeyValue(stringResource(R.string.onboarding_jackpot), stringResource(R.string.guide_specials_jackpot))
                KeyValue(stringResource(R.string.effect_quake), stringResource(R.string.guide_specials_quake))
                KeyValue(stringResource(R.string.onboarding_explosion), stringResource(R.string.guide_specials_explosion))
                KeyValue(stringResource(R.string.effect_snail), stringResource(R.string.guide_specials_snail))
                Bullet(stringResource(R.string.guide_specials_hazards_off))
            }

            // --- Modes --------------------------------------------------------
            GuideSection(title = stringResource(R.string.guide_modes_title)) {
                Bullet(stringResource(R.string.guide_modes_endless))
                Bullet(
                    stringResource(
                        R.string.guide_modes_time_attack,
                        GameState.TIME_ATTACK_MS / 1000,
                        GameState.FEVER_MS / 1000,
                        GameState.FEVER_SCORE_FACTOR,
                    ),
                )
                Bullet(
                    stringResource(
                        R.string.guide_modes_campaign,
                        LevelsMode.LEVEL_COUNT,
                        LevelsMode.LEVEL_FOOD_GOAL,
                        LevelsMode.START_LIVES,
                    ),
                )
                Bullet(stringResource(R.string.guide_modes_zen))
            }

            // --- Endless waves ------------------------------------------------
            GuideSection(title = stringResource(R.string.guide_waves_title)) {
                Bullet(
                    stringResource(
                        R.string.guide_waves_intro,
                        EndlessWaves.FIRST_START_MS / 1000,
                        EndlessWaves.PERIOD_MS / 1000,
                        EndlessWaves.DURATION_MS / 1000,
                    ),
                )
                KeyValue(
                    key = stringResource(R.string.wave_feast),
                    value = stringResource(R.string.guide_waves_feast, EndlessWaves.FEAST_FOOD_COUNT),
                )
                KeyValue(
                    key = stringResource(R.string.wave_drought),
                    value = stringResource(R.string.guide_waves_drought, EndlessWaves.DROUGHT_FOOD_COUNT),
                )
                KeyValue(
                    key = stringResource(R.string.wave_hailstorm),
                    value = stringResource(R.string.guide_waves_hail, EndlessWaves.HAIL_HEAD_CLEARANCE),
                )
            }

            // --- Scoring ------------------------------------------------------
            GuideSection(title = stringResource(R.string.guide_score_title)) {
                Bullet(
                    stringResource(
                        R.string.guide_score_formula,
                        GameEngine.GROW_POINTS_PER_SEGMENT,
                        GameEngine.MAX_COMBO,
                    ),
                )
                Bullet(stringResource(R.string.guide_score_combo))
                Bullet(stringResource(R.string.guide_score_shrink))
                Bullet(stringResource(R.string.guide_score_records))
            }

            // --- Controls -----------------------------------------------------
            GuideSection(title = stringResource(R.string.guide_controls_title)) {
                Bullet(stringResource(R.string.guide_controls_schemes))
                Bullet(stringResource(R.string.guide_controls_reversal))
                Bullet(stringResource(R.string.guide_controls_pause))
                Bullet(stringResource(R.string.guide_controls_back))
            }

            // --- Progress -----------------------------------------------------
            GuideSection(title = stringResource(R.string.guide_progress_title)) {
                Bullet(
                    stringResource(
                        R.string.guide_progress_achievements,
                        Achievement.entries.size,
                        AchievementTier.entries.size,
                    ),
                )
                Bullet(stringResource(R.string.guide_progress_missions, Mission.DAILY_COUNT))
                Bullet(stringResource(R.string.guide_progress_daily))
                Bullet(stringResource(R.string.guide_progress_ghost))
            }

            Text(
                text = stringResource(R.string.guide_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}

/**
 * One collapsible chapter: a glass card whose header toggles it open. Collapsed by
 * default so the screen opens as a short table of contents rather than a wall of
 * text - a reference is browsed, not read top to bottom.
 */
@Composable
private fun GuideSection(
    title: String,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "guideChevron",
    )
    Column(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(scheme.onBackground.copy(alpha = 0.07f), scheme.onBackground.copy(alpha = 0.02f)),
                ),
            )
            .border(
                BorderStroke(1.dp, scheme.primary.copy(alpha = if (expanded) 0.45f else 0.20f)),
                RoundedCornerShape(14.dp),
            )
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
                modifier = Modifier.weight(1f),
            )
            // A drawn chevron that swings down as the section opens.
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = rotation },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground.copy(alpha = 0.6f),
                )
            }
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                content()
            }
        }
    }
}

/** A bullet line of body copy. */
@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** A labelled row for the tables (a growth step, a special, a wave). */
@Composable
private fun KeyValue(key: String, value: String) {
    Row(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.width(92.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
            modifier = Modifier.weight(1f),
        )
    }
}
