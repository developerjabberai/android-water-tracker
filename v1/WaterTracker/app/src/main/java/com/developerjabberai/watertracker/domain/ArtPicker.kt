package com.developerjabberai.watertracker.domain

import kotlin.random.Random

/**
 * Picks which character is on the widget today. It's random but fair: every block of [count] days shows each
 * character once, in a shuffled order, and the seam between blocks never repeats a character. So the pick is the
 * same all day, never the same two days in a row, and different on every phone (the [salt]).
 */
object ArtPicker {
    fun pick(epochDay: Long, salt: Int, count: Int): Int {
        if (count <= 1) return 0
        val block = Math.floorDiv(epochDay, count.toLong())
        val position = Math.floorMod(epochDay, count.toLong()).toInt()
        val order = shuffled(block, salt, count).toMutableList()
        // Only positions 0 and 1 are ever swapped, so the last element of every block stays as shuffled.
        if (order[0] == shuffled(block - 1, salt, count).last()) {
            val first = order[0]; order[0] = order[1]; order[1] = first
        }
        return order[position]
    }

    private fun shuffled(block: Long, salt: Int, count: Int) =
        (0 until count).shuffled(Random(block * 1_000_003L + salt))
}
