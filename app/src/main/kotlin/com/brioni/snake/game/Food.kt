package com.brioni.snake.game

import kotlin.random.Random

/**
 * The seven food types ported from v1.0.0. [cellSpan] is the side length of
 * the square the food occupies on the grid (1 for normal, 2 for "Mega").
 */
enum class FoodType(val cellSpan: Int) {
    Green(1),
    Red(1),
    Gold(1),
    Blue(1),
    MegaGreen(2),
    MegaRed(2),
    MegaGold(2),
}

/**
 * A food item on the board.
 *
 * @param position top-left cell of the occupied square.
 * @param type     visual/identity of the food.
 * @param growth   how many segments the snake gains (also drives the score:
 *                 `growth * 10`). Blue food rolls a random growth in 2..24.
 */
data class Food(
    val position: Position,
    val type: FoodType,
    val growth: Int,
) {
    val span: Int get() = type.cellSpan

    /** Every cell this food covers. */
    fun cells(): List<Position> = buildList {
        for (i in 0 until span) {
            for (j in 0 until span) {
                add(Position(position.x + i, position.y + j))
            }
        }
    }

    /** True when [cell] lies inside this food's occupied square. */
    fun occupies(cell: Position): Boolean =
        cell.x >= position.x && cell.x < position.x + span &&
            cell.y >= position.y && cell.y < position.y + span
}

/** Type + growth template produced by a spawn roll, before a cell is chosen. */
data class FoodSpec(val type: FoodType, val growth: Int)

/**
 * The probability table from v1.0.0 (`SpawnFood`): a roll in 0..99 maps to a
 * food type and its growth. Blue food's growth is a uniform 2..24.
 */
object FoodTable {
    fun roll(random: Random): FoodSpec = when (random.nextInt(100)) {
        in 0 until 3 -> FoodSpec(FoodType.MegaGold, 24)
        in 3 until 10 -> FoodSpec(FoodType.MegaRed, 16)
        in 10 until 20 -> FoodSpec(FoodType.MegaGreen, 8)
        in 20 until 30 -> FoodSpec(FoodType.Blue, random.nextInt(2, 25))
        in 30 until 45 -> FoodSpec(FoodType.Gold, 6)
        in 45 until 70 -> FoodSpec(FoodType.Red, 4)
        else -> FoodSpec(FoodType.Green, 2)
    }
}
