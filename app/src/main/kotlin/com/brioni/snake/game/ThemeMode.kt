package com.brioni.snake.game

/**
 * How the app chooses between its light and dark colour schemes. [System] follows
 * the device setting; [Light]/[Dark] force one regardless. Persisted in settings
 * (by name) like the other presentation choices.
 */
enum class ThemeMode(val displayName: String) {
    Light("Light"),
    Dark("Dark"),
    System("System"),
}
