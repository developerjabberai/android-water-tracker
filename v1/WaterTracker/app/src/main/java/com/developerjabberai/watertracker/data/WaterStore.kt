package com.developerjabberai.watertracker.data

import android.content.Context
import com.developerjabberai.watertracker.domain.ArtPicker
import com.developerjabberai.watertracker.widget.ArtLibrary
import kotlin.random.Random
import java.time.LocalDate

/** Local storage. Today's total rolls over at midnight; past days are kept for the 7-day view. */
class WaterStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("water", Context.MODE_PRIVATE)

    var goalMl: Int
        get() = prefs.getInt("goal", 2000)
        set(v) = prefs.edit().putInt("goal", v).apply()

    /** What one tap on the widget logs: half a glass (100 ml) or a whole glass (200 ml). */
    var tapMl: Int
        get() = prefs.getInt("tap", HALF_GLASS_ML)
        set(v) = prefs.edit().putInt("tap", v).apply()

    /** True while the widget is showing its "you're behind" look. Cleared by a tap or a new day. */
    var nudgePending: Boolean
        get() { rollOverIfNeeded(); return prefs.getBoolean("nudge_pending", false) }
        set(v) = prefs.edit().putBoolean("nudge_pending", v).apply()

    var lastPulseMs: Long
        get() = prefs.getLong("last_pulse", 0L)
        set(v) = prefs.edit().putLong("last_pulse", v).apply()

    /** A per-install random number so different phones don't all show the same character on the same day. */
    private val artSalt: Int
        get() {
            var salt = prefs.getInt("art_salt", 0)
            if (salt == 0) {
                salt = Random.nextInt(1, Int.MAX_VALUE)
                prefs.edit().putInt("art_salt", salt).apply()
            }
            return salt
        }

    /** Today's character (0-3): a random pick that stays the same all day. "art_override" is a testing hook. */
    fun artToday(): Int {
        val forced = prefs.getInt("art_override", -1)
        if (forced in 0 until ArtLibrary.COUNT) return forced
        return ArtPicker.pick(java.time.LocalDate.now().toEpochDay(), artSalt, ArtLibrary.COUNT)
    }

    /** Set once the first-run welcome has been completed or skipped. */
    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()

    fun todayMl(): Int {
        rollOverIfNeeded()
        return prefs.getInt("today", 0)
    }

    fun addTap(): Int {
        val total = todayMl() + tapMl
        prefs.edit().putInt("today", total).putBoolean("nudge_pending", false).apply()
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
        e.putString("date", today).putInt("today", 0).putBoolean("nudge_pending", false).apply()
    }

    companion object {
        const val GLASS_ML = 200
        const val HALF_GLASS_ML = GLASS_ML / 2
    }
}
