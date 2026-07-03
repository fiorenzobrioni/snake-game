package com.brioni.snake.ui.game

import androidx.compose.ui.graphics.Color
import com.brioni.snake.game.EffectKind
import com.brioni.snake.game.FoodEffect

/**
 * Vivid, skin-independent accent colours for the Phase 6.2 specials. Power-ups
 * read as universal icons, so they keep a consistent identity across skins
 * (rather than tinting with the palette): the HUD timer chips and the on-board
 * symbols share these so a colour always means the same thing.
 */
object SpecialVisuals {

    val LightningColor = Color(0xFFFFD600)
    val SnailColor = Color(0xFFFFA000)
    val StarColor = Color(0xFF80DEEA)
    val FreezeColor = Color(0xFF66CCFF)
    val JackpotColor = Color(0xFFFFC400)
    val QuakeColor = Color(0xFFFF7043)
    val ExplosionColor = Color(0xFFFF5252)
    val TimeBonusColor = Color(0xFF66BB6A)
    val TimePenaltyColor = Color(0xFFEF5350)
    val ExtraLifeColor = Color(0xFFFF4081)

    /** Time Attack Fever Time: the sustained amber heat of the finale. */
    val FeverColor = Color(0xFFFF9100)

    /** Endless speed-tier surge: the golden flare of a pace step. */
    val SurgeColor = Color(0xFFFFD740)

    /** Mid-run record broken: a bright celebratory green. */
    val RecordColor = Color(0xFF69F0AE)

    /** Accent for a special food, chosen from its effect. */
    fun accent(effect: FoodEffect): Color = when (effect) {
        is FoodEffect.Haste -> LightningColor
        is FoodEffect.Slow -> SnailColor
        is FoodEffect.Ghost -> StarColor
        is FoodEffect.Freeze -> FreezeColor
        is FoodEffect.Jackpot -> JackpotColor
        is FoodEffect.Quake -> QuakeColor
        is FoodEffect.Burst -> ExplosionColor
        is FoodEffect.TimeBonus -> TimeBonusColor
        is FoodEffect.TimePenalty -> TimePenaltyColor
        is FoodEffect.ExtraLife -> ExtraLifeColor
        else -> Color(0xFFB388FF)
    }

    /** Accent for a running timed effect (HUD chip). */
    fun accent(kind: EffectKind): Color = when (kind) {
        EffectKind.Haste -> LightningColor
        EffectKind.Slow -> SnailColor
        EffectKind.Ghost -> StarColor
        EffectKind.Freeze -> FreezeColor
        EffectKind.Quake -> QuakeColor
    }
}
