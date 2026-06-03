package com.brioni.snake.game

/**
 * The five board-size presets, sized for a phone in **portrait**.
 *
 * The frozen v1.0.0 desktop build used 3:2 landscape boards; on a tall phone
 * those left large empty bands. These presets are portrait (~7:10) so the board
 * fills the play area, and they scale from chunky/easy (Pocket) to fine/hard
 * (Infinite) with cell counts tuned to stay comfortably tappable.
 */
enum class BoardSize(
    val displayName: String,
    val width: Int,
    val height: Int,
) {
    Pocket("Pocket", 14, 20),
    Classic("Classic", 18, 26),
    Grand("Grand", 22, 32),
    Colossal("Colossal", 27, 38),
    Infinite("Infinite", 32, 46);

    /** Total number of cells on the board. */
    val cellCount: Int get() = width * height

    /** Human-readable label, e.g. "Classic (18×26)". */
    val label: String get() = "$displayName ($width×$height)"
}
