package com.developerjabberai.watertracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtPickerTest {
    private val salt = 424242

    @Test
    fun sameDayAlwaysGivesTheSameCharacter() {
        repeat(20) { assertEquals(ArtPicker.pick(20_000L, salt, 4), ArtPicker.pick(20_000L, salt, 4)) }
    }

    @Test
    fun picksStayInRange() {
        for (day in 20_000L until 20_400L) assertTrue(ArtPicker.pick(day, salt, 4) in 0..3)
    }

    @Test
    fun neverRepeatsTheSameCharacterTwoDaysRunning() {
        for (salt in listOf(1, 424242, 987654321)) {
            val days = (20_000L until 20_800L).map { ArtPicker.pick(it, salt, 4) }
            assertEquals("salt $salt", 0, days.zipWithNext().count { (a, b) -> a == b })
        }
    }

    @Test
    fun everyFourDaysShowAllCharacters() {
        for (start in 20_000L until 20_200L) {
            val block = (start until start + 4).map { ArtPicker.pick(it, salt, 4) }.toSet()
            // Any four consecutive days may straddle two blocks, but a whole aligned block has all four.
            if (start % 4 == 0L) assertEquals(setOf(0, 1, 2, 3), block)
        }
    }

    @Test
    fun everyCharacterGetsUsed() {
        val used = (20_000L until 20_100L).map { ArtPicker.pick(it, salt, 4) }.toSet()
        assertEquals(setOf(0, 1, 2, 3), used)
    }
}
