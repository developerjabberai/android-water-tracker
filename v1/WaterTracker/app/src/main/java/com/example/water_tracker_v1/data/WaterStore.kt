package com.example.water_tracker_v1.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime

/** Local storage. Today's total rolls over at midnight; past days are kept for the 7-day view. */
class WaterStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("water", Context.MODE_PRIVATE)

    var goalMl: Int
        get() = prefs.getInt("goal", 2000)
        set(v) = prefs.edit().putInt("goal", v).apply()

    var glassMl: Int
        get() = prefs.getInt("glass", 250)
        set(v) = prefs.edit().putInt("glass", v).apply()

    var wake: LocalTime
        get() = LocalTime.ofSecondOfDay(prefs.getInt("wake", 7 * 3600).toLong())
        set(v) = prefs.edit().putInt("wake", v.toSecondOfDay()).apply()

    var sleep: LocalTime
        get() = LocalTime.ofSecondOfDay(prefs.getInt("sleep", 23 * 3600).toLong())
        set(v) = prefs.edit().putInt("sleep", v.toSecondOfDay()).apply()

    val tapMl: Int get() = glassMl / 2

    fun todayMl(): Int {
        rollOverIfNeeded()
        return prefs.getInt("today", 0)
    }

    fun addTap(): Int {
        val total = todayMl() + tapMl
        prefs.edit().putInt("today", total).apply()
        return total
    }

    fun historyMl(days: Int = 7): List<Pair<LocalDate, Int>> {
        val today = LocalDate.now()
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            d to if (back == 0) todayMl() else prefs.getInt("day_$d", 0)
        }
    }

    private fun rollOverIfNeeded() {
        val today = LocalDate.now().toString()
        val stored = prefs.getString("date", null)
        if (stored == today) return
        val e = prefs.edit()
        if (stored != null) e.putInt("day_$stored", prefs.getInt("today", 0))
        e.putString("date", today).putInt("today", 0).apply()
    }
}
