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

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TAP) return

        val pending = goAsync()
        thread {
            try {
                val store = WaterStore(context)
                val to = store.addTap()
                val from = to - store.tapMl
                synchronized(animationLock) {
                    animateFill(context, store, from.toFloat(), to.toFloat())
                    if (from < store.goalMl && to >= store.goalMl) celebrate(context, store, to.toFloat()) else smile(context, store, to.toFloat())
                    refresh(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TAP = "com.example.water_tracker_v1.ACTION_TAP"
        private const val FILL_FRAMES = 16
        private const val FRAME_MS = 45L
        private const val TURN_FRAMES = 9
        private const val NUDGE_REST = 0.6f

        /** One animation at a time, so quick repeated taps play in order instead of tangling. */
        private val animationLock = Any()

        private fun ids(context: Context): Pair<AppWidgetManager, IntArray> {
            val manager = AppWidgetManager.getInstance(context)
            return manager to manager.getAppWidgetIds(ComponentName(context, WaterWidgetProvider::class.java))
        }

        /** Redraw all widgets at rest (the plain bottle), e.g. after a setting changed. */
        fun refresh(context: Context) {
            val store = WaterStore(context)
            push(context, store, Frame(store.todayMl().toFloat(), nudge = if (store.nudgePending) NUDGE_REST else 0f))
        }

        /**
         * The reminder: the bottle turns around to show the cat, the dotted target line draws across,
         * translucent water rises to it (the gap to drink), then drains as the cat looks worried, and the
         * bottle turns back. Ends in the resting nudge look.
         */
        fun pulse(context: Context) = synchronized(animationLock) {
            val store = WaterStore(context)
            val level = store.todayMl().toFloat()
            val hold = 44
            play(context, store, hold) { h, flip, _ ->
                val rise = ease(((h - 8) / 16f).coerceIn(0f, 1f))
                val drain = ((h - 30) / 12f).coerceIn(0f, 1f)
                Frame(
                    totalMl = level,
                    phase = h * 0.5f,
                    nudge = 0.35f + (NUDGE_REST - 0.35f) * drain,
                    lineProgress = (h / 8f).coerceIn(0f, 1f),
                    preview = rise * (1f - drain * drain),
                    face = if (h < 28) Face.NUDGE else Face.WORRY,
                    flip = flip,
                    blink = h in 14..15,
                )
            }
            refresh(context)
        }

        /** After a tap: the cat turns around, smiles for a moment, and turns back. */
        private fun smile(context: Context, store: WaterStore, level: Float) {
            play(context, store, 18) { h, flip, _ ->
                Frame(level, phase = h * 0.4f, face = Face.OK, flip = flip, blink = h in 9..10)
            }
        }

        /** One-time goal-reached: big grin and arms up, with the shine and sparkles, then the check badge. */
        private fun celebrate(context: Context, store: WaterStore, level: Float) {
            play(context, store, 40) { _, flip, t ->
                Frame(level, face = Face.HAPPY, flip = flip, celebrate = t)
            }
        }

        /** Turns the bottle to its face side, holds for [hold] frames, then turns it back. */
        private fun play(context: Context, store: WaterStore, hold: Int, build: (holdIndex: Int, flip: Float, progress: Float) -> Frame) {
            val total = TURN_FRAMES * 2 + hold
            var n = 0
            fun show(h: Int, flip: Float) {
                push(context, store, build(h, flip, ++n / total.toFloat()))
                Thread.sleep(FRAME_MS)
            }
            for (i in 1..TURN_FRAMES) show(0, 1f - ease(i / TURN_FRAMES.toFloat()))
            for (h in 0 until hold) show(h, 0f)
            for (i in 1..TURN_FRAMES) show(hold - 1, ease(i / TURN_FRAMES.toFloat()))
        }

        private fun ease(t: Float) = t * t * (3f - 2f * t)

        /** Frame-sequence animation: water eases up while the wave settles. */
        private fun animateFill(context: Context, store: WaterStore, from: Float, to: Float) {
            for (i in 1..FILL_FRAMES) {
                val t = i / FILL_FRAMES.toFloat()
                val eased = 1f - (1f - t).pow(3)
                push(context, store, Frame(from + (to - from) * eased, phase = t * 9f, waveAmp = 1f - t))
                Thread.sleep(FRAME_MS)
            }
        }

        private fun push(context: Context, store: WaterStore, frame: Frame) {
            val (manager, ids) = ids(context)
            if (ids.isEmpty()) return
            val pace = Pace.fraction(LocalTime.now())
            val bmp = BottleRenderer.render(context, store.goalMl, pace, frame)
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
