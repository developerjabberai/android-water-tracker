package com.example.water_tracker_v1.domain

import java.time.LocalTime

/** Front-loaded pace: fraction of the daily goal the user should have reached by now. */
object Pace {
    val WAKE: LocalTime = LocalTime.of(7, 0)
    val SLEEP: LocalTime = LocalTime.of(23, 0)
    private const val CURVE = 1.6

    fun fraction(now: LocalTime): Float {
        val start = WAKE.toSecondOfDay().toFloat()
        val end = SLEEP.toSecondOfDay().toFloat()
        val t = ((now.toSecondOfDay() - start) / (end - start)).coerceIn(0f, 1f)
        return (1.0 - Math.pow(1.0 - t, CURVE)).toFloat()
    }

    fun isAwake(now: LocalTime) = now >= WAKE && now <= SLEEP
}
