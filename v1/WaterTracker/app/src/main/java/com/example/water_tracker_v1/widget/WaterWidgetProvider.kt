package com.example.water_tracker_v1.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.water_tracker_v1.R
import com.example.water_tracker_v1.data.WaterStore
import com.example.water_tracker_v1.domain.Pace
import java.time.LocalTime
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class WaterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TAP) return

        val pending = goAsync()
        thread {
            try {
                val store = WaterStore(context)
                val from = store.todayMl().toFloat()
                val to = store.addTap().toFloat()
                animateFill(context, store, from, to)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TAP = "com.example.water_tracker_v1.ACTION_TAP"
        private const val FILL_FRAMES = 16
        private const val FRAME_MS = 45L
        private const val PULSE_FRAMES = 60
        private const val PULSE_BEATS = 3
        private const val NUDGE_REST = 0.6f

        private fun ids(context: Context): Pair<AppWidgetManager, IntArray> {
            val manager = AppWidgetManager.getInstance(context)
            return manager to manager.getAppWidgetIds(ComponentName(context, WaterWidgetProvider::class.java))
        }

        /** Redraw all widgets at rest, e.g. after a setting changed. */
        fun refresh(context: Context) {
            val store = WaterStore(context)
            push(context, store, store.todayMl().toFloat(), 0f, 0f, restingNudge(store))
        }

        /** Three soft beats of the gap highlight; ends in the resting nudge look. */
        fun pulse(context: Context) {
            val store = WaterStore(context)
            val level = store.todayMl().toFloat()
            for (i in 1..PULSE_FRAMES) {
                val t = i / PULSE_FRAMES.toFloat()
                val beat = NUDGE_REST + (1f - NUDGE_REST) * sin(2 * PI * PULSE_BEATS * t).toFloat()
                push(context, store, level, 0f, 0f, beat.coerceIn(0f, 1f))
                Thread.sleep(FRAME_MS)
            }
            push(context, store, level, 0f, 0f, NUDGE_REST)
        }

        private fun restingNudge(store: WaterStore) = if (store.nudgePending) NUDGE_REST else 0f

        /** Frame-sequence animation: water eases up while the wave settles. */
        private fun animateFill(context: Context, store: WaterStore, from: Float, to: Float) {
            for (i in 1..FILL_FRAMES) {
                val t = i / FILL_FRAMES.toFloat()
                val eased = 1f - (1f - t).pow(3)
                push(context, store, from + (to - from) * eased, phase = t * 9f, waveAmp = 1f - t, nudge = 0f)
                Thread.sleep(FRAME_MS)
            }
            push(context, store, to, 0f, 0f, 0f)
        }

        private fun push(context: Context, store: WaterStore, level: Float, phase: Float, waveAmp: Float, nudge: Float) {
            val (manager, ids) = ids(context)
            if (ids.isEmpty()) return
            val pace = Pace.fraction(LocalTime.now(), store.wake, store.sleep)
            val bmp = BottleRenderer.render(context, level, store.goalMl, pace, phase, waveAmp, nudge)
            val views = RemoteViews(context.packageName, R.layout.widget_water).apply {
                setImageViewBitmap(R.id.widget_image, bmp)
                val tap = Intent(context, WaterWidgetProvider::class.java).setAction(ACTION_TAP)
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getBroadcast(context, 0, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                )
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
