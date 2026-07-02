package com.brioni.snake.game

/**
 * The board's "floor": the animated backdrop the run is played on, selectable in
 * Settings independently of the [Skin]. A terrain is purely cosmetic - it swaps
 * the board-background shader (and the grid-line tint) while the snake, foods,
 * obstacles and tokens keep the active skin's look - so it lives in the pure
 * model only as an identifier + label; the concrete shaders are mapped in the UI
 * layer (`ui/game/Shaders` / `GameBoard.drawBoardBackground`).
 *
 * Terrains are stages, not protagonists: every one is kept dark, desaturated and
 * slowly animated so gameplay readability never suffers under any skin. The enum
 * order is the order shown in the Settings picker; all are available from the
 * start (no unlock gating, unlike skins).
 */
enum class BoardTerrain(val displayName: String) {
    /** The skin's own board: its gradient with drifting glows. Default. */
    Default("Default"),

    /** A mowed lawn: two-tone grass checker, blade texture, drifting cloud shadows. */
    Meadow("Meadow"),

    /** Deep ocean floor: near-black blue, animated caustics, faint light shafts. */
    Abyss("Abyss"),

    /** Deep space: a twinkling star field over faint drifting nebula wisps. */
    Nebula("Nebula"),

    /** Night desert: layered dune ridges with crest glints and sparkling sand. */
    Dunes("Dunes"),

    /** Dark circuit board: grid-aligned traces with pulses travelling along them. */
    Circuit("Circuit"),
}
