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
import kotlin.math.pow

class WaterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val store = WaterStore(context)
        push(context, manager, ids, store, store.todayMl().toFloat(), 0f, 0f)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TAP) return

        val pending = goAsync()
        thread {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, WaterWidgetProvider::class.java))
                val store = WaterStore(context)
                val from = store.todayMl().toFloat()
                val to = store.addTap().toFloat()
                animate(context, manager, ids, store, from, to)
            } finally {
                pending.finish()
            }
        }
    }

    /** Frame-sequence animation: water eases up while the wave settles. */
    private fun animate(context: Context, manager: AppWidgetManager, ids: IntArray, store: WaterStore, from: Float, to: Float) {
        for (i in 1..FRAMES) {
            val t = i / FRAMES.toFloat()
            val eased = 1f - (1f - t).pow(3)
            val level = from + (to - from) * eased
            push(context, manager, ids, store, level, phase = t * 9f, waveAmp = 1f - t)
            Thread.sleep(FRAME_MS)
        }
        push(context, manager, ids, store, to, 0f, 0f)
    }

    private fun push(context: Context, manager: AppWidgetManager, ids: IntArray, store: WaterStore, level: Float, phase: Float, waveAmp: Float) {
        val pace = Pace.fraction(LocalTime.now(), store.wake, store.sleep)
        val bmp = BottleRenderer.render(level, store.goalMl, pace, phase, waveAmp)
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

    companion object {
        /** Redraw all widgets, e.g. after a setting changed. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WaterWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val store = WaterStore(context)
            WaterWidgetProvider().push(context, manager, ids, store, store.todayMl().toFloat(), 0f, 0f)
        }

        const val ACTION_TAP = "com.example.water_tracker_v1.ACTION_TAP"
        private const val FRAMES = 16
        private const val FRAME_MS = 45L
    }
}
