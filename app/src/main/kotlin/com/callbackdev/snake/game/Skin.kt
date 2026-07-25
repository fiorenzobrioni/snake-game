package com.callbackdev.snake.game

/**
 * A visual theme for the board and snake. A skin is purely cosmetic — it changes
 * the palette and render style (rounded vs square cells, glow on/off, continuous
 * vs segmented body) but never the rules — so it lives in the pure model only as
 * an identifier + label; the concrete colours are mapped in the UI layer
 * (`ui/game/SkinPalette`).
 *
 * **Every skin is available from the start.** The score / Daily-streak unlock
 * gates that used to guard four of them were removed by design: the skins are
 * expression, not reward, and making a player grind for a look they can already
 * see only gets in the way of playing. The enum order is the order shown in the
 * Settings picker, with [Retro] (the default) first.
 */
enum class Skin(val displayName: String) {
    /** Warm, limited arcade palette that pairs with the CRT filter. Default skin. */
    Retro("Retro"),

    /** The original look: lime snake, green/warm foods on a dark gradient. */
    Classic("Classic"),

    /** High-contrast saturated neon on near-black, with boosted glow. */
    Neon("Neon"),

    /** 8-bit sprite styling: every piece a 5x5-pixel coin-op sprite tile. */
    Pixel("Pixel"),

    /** Cool aurora gradient with glow and a segmented body. */
    Aurora("Aurora"),

    /** Hot lava palette with intense glow and a segmented body. */
    Ember("Ember"),
}
