package com.brioni.snake.game

/**
 * The board's "floor": the animated backdrop the run is played on, selectable in
 * Settings independently of the [Skin]. A terrain is purely cosmetic - it swaps
 * the board-background shader (and the grid-line tint) while the snake, foods,
 * obstacles and tokens keep the active skin's look - so it lives in the pure
 * model only as an identifier + label; the concrete shaders are mapped in the UI
 * layer (`ui/game/Shaders` / `GameBoard.drawBoardBackground`).
 *
 * Terrains are stages, not protagonists: every one is kept calm (mid-to-dark,
 * softly saturated) and slowly animated so gameplay readability never suffers
 * under any skin. The enum order is the order shown in the Settings picker; all
 * are available from the start (no unlock gating, unlike skins). [Meadow] is the
 * out-of-the-box default (fresh installs land on the lawn, matching the brand
 * intro); [Arcade] is the skin-following floor the game originally shipped with.
 */
enum class BoardTerrain(val displayName: String) {
    /** A mowed lawn: two-tone grass checker, blade texture, drifting cloud shadows. Default. */
    Meadow("Meadow"),

    /** The skin's own board: its dark arcade gradient with drifting glows. */
    Arcade("Arcade"),

    /** Deep ocean floor: near-black blue, animated caustics, faint light shafts. */
    Abyss("Abyss"),

    /** Deep space: a twinkling star field over faint drifting nebula wisps. */
    Nebula("Nebula"),

    /** Night desert: layered dune ridges with crest glints and sparkling sand. */
    Dunes("Dunes"),

    /** Frozen lake: pale icy blue with bright crack veins, a drifting sheen and sparkles. */
    Glacier("Glacier"),
}
