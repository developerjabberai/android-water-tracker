package com.developerjabberai.watertracker.data

import android.content.Context
import com.developerjabberai.watertracker.domain.ArtPicker
import com.developerjabberai.watertracker.domain.Creeper
import com.developerjabberai.watertracker.widget.ArtLibrary
import kotlin.random.Random
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Local storage. Today's total rolls over at midnight; past days are kept for the 7-day view. */
class WaterStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("water", Context.MODE_PRIVATE)

    var goalMl: Int
        get() = prefs.getInt("goal", DEFAULT_GOAL_ML)
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

    /** How grown the creeper is, 1..10. Any days that have ended since it was last looked at are applied first. */
    fun creeperLevel(): Int {
        rollOverIfNeeded()
        return prefs.getInt("creeper", Creeper.START).coerceIn(Creeper.MIN, Creeper.MAX)
    }

    /**
     * Call after logging water. The first time each day the total reaches the goal, the creeper grows a level.
     * Returns (old, new) level when that just happened, otherwise null.
     */
    fun creditGoalIfMet(): Pair<Int, Int>? {
        rollOverIfNeeded()
        val today = LocalDate.now().toString()
        if (prefs.getInt("today", 0) < goalMl) return null
        if (prefs.getString("creeper_credit", null) == today) return null
        val old = creeperLevel()
        val new = Creeper.afterGoalMet(old)
        prefs.edit().putInt("creeper", new).putString("creeper_credit", today).apply()
        return old to new
    }

    /** Testing only: lets a debuggable build fire the reminder at any hour. Always false in release builds. */
    fun anyHourForTesting(context: Context): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
            prefs.getBoolean("test_any_hour", false)

    /** Testing only: overrides the widget's pace marker instead of computing it from the clock. Always ignored in release builds. */
    fun paceOverrideForTesting(context: Context): Float? {
        if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return null
        val v = prefs.getFloat("pace_override", -1f)
        return if (v in 0f..1f) v else null
    }

    /** So we only ask for the notification permission once. */
    var notificationsAsked: Boolean
        get() = prefs.getBoolean("notifications_asked", false)
        set(v) = prefs.edit().putBoolean("notifications_asked", v).apply()

    /** When we last showed Google's review card (0 = never), so we can wait before ever asking again. */
    var lastReviewAskMs: Long
        get() = prefs.getLong("last_review_ask", 0L)
        set(v) = prefs.edit().putLong("last_review_ask", v).apply()

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
        if (stored != null) {
            e.putInt("day_$stored", prefs.getInt("today", 0))
            val idleDays = (ChronoUnit.DAYS.between(LocalDate.parse(stored), LocalDate.now()) - 1).toInt()
            val metThatDay = prefs.getString("creeper_credit", null) == stored
            val level = prefs.getInt("creeper", Creeper.START)
            e.putInt("creeper", Creeper.afterDayEnds(level, metThatDay, idleDays))
        }
        e.putString("date", today).putInt("today", 0).putBoolean("nudge_pending", false).apply()
    }

    companion object {
        const val GLASS_ML = 200
        /** Daily goal choices, in glasses (200 ml each): 1, 1.4, 2, 2.4, 3, 3.4 and 4 litres. */
        val GOAL_GLASSES = listOf(5, 7, 10, 12, 15, 17, 20)
        const val DEFAULT_GOAL_ML = 7 * GLASS_ML
        const val HALF_GLASS_ML = GLASS_ML / 2
    }
}
