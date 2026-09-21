package com.developerjabberai.watertracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CreeperTest {
    @Test
    fun startsInTheMiddle() = assertEquals(5, Creeper.START)

    @Test
    fun growsOneLevelWhenTheGoalIsMet() = assertEquals(6, Creeper.afterGoalMet(5))

    @Test
    fun stopsGrowingAtTheTop() = assertEquals(10, Creeper.afterGoalMet(10))

    @Test
    fun aCreditedDayDoesNotShrinkIt() = assertEquals(7, Creeper.afterDayEnds(7, metGoal = true, missedDaysBetween = 0))

    @Test
    fun aMissedDayShrinksItByOne() = assertEquals(4, Creeper.afterDayEnds(5, metGoal = false, missedDaysBetween = 0))

    @Test
    fun everyIdleDayShrinksItToo() {
        // yesterday missed plus two whole days with no activity
        assertEquals(2, Creeper.afterDayEnds(5, metGoal = false, missedDaysBetween = 2))
        // yesterday met, but two idle days since
        assertEquals(3, Creeper.afterDayEnds(5, metGoal = true, missedDaysBetween = 2))
    }

    @Test
    fun neverDropsBelowTheBottom() = assertEquals(1, Creeper.afterDayEnds(2, metGoal = false, missedDaysBetween = 30))

    @Test
    fun hasTenNamedLevels() {
        assertEquals(10, Creeper.NAMES.size)
        assertEquals("Steady", Creeper.name(Creeper.START))
        assertEquals("Full bloom", Creeper.name(99)) // out of range is clamped
    }
}
