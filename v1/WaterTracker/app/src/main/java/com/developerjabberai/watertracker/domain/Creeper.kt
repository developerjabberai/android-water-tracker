package com.developerjabberai.watertracker.domain

/**
 * The creeper is a streak meter with ten growth levels. It starts at [START], grows one level on each day the goal
 * is met, and shrinks one level for each day it is missed (never below [MIN]). Only meeting the whole goal counts.
 */
object Creeper {
    const val MIN = 1
    const val MAX = 10
    const val START = 5

    val NAMES = listOf(
        "Wilted", "Struggling", "Recovering", "Sprout", "Steady",
        "Climbing", "Lush", "Budding", "Blooming", "Full bloom",
    )

    fun name(level: Int) = NAMES[level.coerceIn(MIN, MAX) - 1]

    /** The moment the goal is first reached on a day. */
    fun afterGoalMet(level: Int) = (level + 1).coerceAtMost(MAX)

    /**
     * When a day ends. [metGoal] says whether the day that just ended was credited, and [missedDaysBetween] counts
     * whole days with no activity at all since (for example, if the app wasn't opened for a while).
     */
    fun afterDayEnds(level: Int, metGoal: Boolean, missedDaysBetween: Int): Int {
        val drop = (if (metGoal) 0 else 1) + missedDaysBetween.coerceAtLeast(0)
        return (level - drop).coerceAtLeast(MIN)
    }
}
