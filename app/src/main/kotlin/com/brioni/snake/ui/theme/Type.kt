package com.brioni.snake.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.brioni.snake.R

/**
 * Orbitron (SIL OFL 1.1) — a clean, modern, geometric display family — drives
 * the display / headline / title roles for a polished "gaming" identity. Body
 * and label roles keep the Material default sans so longer text stays legible.
 */
val Orbitron = FontFamily(
    Font(R.font.orbitron_regular, FontWeight.Normal),
    Font(R.font.orbitron_bold, FontWeight.Bold),
)

private val Default = Typography()

private fun TextStyle.branded() = copy(fontFamily = Orbitron, letterSpacing = 0.5.sp)

val SnakeTypography = Default.copy(
    displayLarge = Default.displayLarge.branded(),
    displayMedium = Default.displayMedium.branded(),
    displaySmall = Default.displaySmall.branded(),
    headlineLarge = Default.headlineLarge.branded(),
    headlineMedium = Default.headlineMedium.branded(),
    headlineSmall = Default.headlineSmall.branded(),
    titleLarge = Default.titleLarge.branded(),
    titleMedium = Default.titleMedium.branded(),
    titleSmall = Default.titleSmall.branded(),
)
