package com.example.water_tracker_v1.domain

import android.content.Context
import com.example.water_tracker_v1.data.WaterStore
import com.example.water_tracker_v1.widget.WaterWidgetProvider
import java.time.LocalTime

/** Decides what the widget does when the phone is unlocked. */
object NudgeController {
    private const val COOLDOWN_MS = 10 * 60 * 1000L

    /** Call on a background thread: may play the pulse animation (~3 s). */
    fun onUnlock(context: Context) {
        val store = WaterStore(context)
        val now = LocalTime.now()
        val awake = now >= store.wake && now <= store.sleep
        val total = store.todayMl()
        val behind = Pace.fraction(now, store.wake, store.sleep) * store.goalMl - total

        if (!awake || total >= store.goalMl || behind < store.nudgeMl) {
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
