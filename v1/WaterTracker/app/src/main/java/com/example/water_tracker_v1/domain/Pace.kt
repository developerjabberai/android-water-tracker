package com.example.water_tracker_v1.domain

import java.time.LocalTime

/** Front-loaded pace: fraction of the daily goal the user should have reached by [now]. */
object Pace {
    private const val CURVE = 1.6

    fun fraction(now: LocalTime, wake: LocalTime, sleep: LocalTime): Float {
        val start = wake.toSecondOfDay().toFloat()
        val end = sleep.toSecondOfDay().toFloat()
        if (end <= start) return 1f
        val t = ((now.toSecondOfDay() - start) / (end - start)).coerceIn(0f, 1f)
        return (1.0 - Math.pow(1.0 - t, CURVE)).toFloat()
    }
}
