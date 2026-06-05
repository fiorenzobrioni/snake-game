package com.brioni.snake.game

/**
 * A visual theme for the board and snake. A skin is purely cosmetic — it changes
 * the palette and render style (rounded vs square cells, glow on/off) but never
 * the rules — so it lives in the pure model only as an identifier + label; the
 * concrete colours are mapped in the UI layer (`ui/game/SkinPalette`).
 *
 * All skins are available immediately; the selection is persisted in settings.
 */
enum class Skin(val displayName: String) {
    /** The original look: lime snake, green/warm foods on a dark gradient. */
    Classic("Classic"),

    /** High-contrast saturated neon on near-black, with boosted glow. */
    Neon("Neon"),

    /** Warm, limited arcade palette that pairs with the CRT filter. */
    Retro("Retro"),

    /** Flat, square, glow-free pixel-art styling. */
    Pixel("Pixel"),
}
