package com.developerjabberai.watertracker.domain

import android.content.Context
import com.developerjabberai.watertracker.data.WaterStore
import com.developerjabberai.watertracker.widget.WaterWidgetProvider
import java.time.LocalTime

/** Decides what the widget does when the phone is unlocked. */
object NudgeController {
    private const val COOLDOWN_MS = 3 * 60 * 1000L

    /** How far behind the target (ml) before the widget nudges. */
    private const val BEHIND_THRESHOLD_ML = 100

    /** Call on a background thread: may play the pulse animation (~3 s). */
    fun onUnlock(context: Context) {
        val store = WaterStore(context)
        val now = LocalTime.now()
        val total = store.todayMl()
        val behind = Pace.fraction(now) * store.goalMl - total

        if (!(Pace.isAwake(now) || store.anyHourForTesting(context)) || total >= store.goalMl || behind < BEHIND_THRESHOLD_ML) {
            store.nudgePending = false
            WaterWidgetProvider.refresh(context)
            return
        }

        store.nudgePending = true
        val nowMs = System.currentTimeMillis()
        if (nowMs - store.lastPulseMs >= COOLDOWN_MS) {
            store.lastPulseMs = nowMs
            WaterWidgetProvider.pulse(context)
        } else {
            WaterWidgetProvider.refresh(context)
        }
    }
}
