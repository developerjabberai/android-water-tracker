package com.developerjabberai.watertracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.developerjabberai.watertracker.R
import com.developerjabberai.watertracker.data.WaterStore
import com.developerjabberai.watertracker.domain.Pace
import java.time.LocalTime
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
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
                val to = store.addTap()
                val from = to - store.tapMl
                synchronized(animationLock) {
                    animateFill(context, store, from.toFloat(), to.toFloat())
                    val grew = store.creditGoalIfMet()
                    if (grew != null) celebrate(context, store, to.toFloat(), grew.first, grew.second) else cheer(context, store, to.toFloat())
                    refresh(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TAP = "com.developerjabberai.watertracker.ACTION_TAP"
        private const val FILL_FRAMES = 16
        private const val FRAME_MS = 45L
        private const val NUDGE_REST = 0.6f

        /** One animation at a time, so quick repeated taps play in order instead of tangling. */
        private val animationLock = Any()

        private fun ids(context: Context): Pair<AppWidgetManager, IntArray> {
            val manager = AppWidgetManager.getInstance(context)
            return manager to manager.getAppWidgetIds(ComponentName(context, WaterWidgetProvider::class.java))
        }

        /** Redraw all widgets at rest, e.g. after a setting changed or a new day started. */
        fun refresh(context: Context) {
            val store = WaterStore(context)
            push(context, store, Frame(store.todayMl().toFloat(), nudge = if (store.nudgePending) NUDGE_REST else 0f))
        }

        /**
         * The reminder: the character wobbles for attention, the dotted target line draws across, translucent
         * water rises to it (the gap to drink), then drains as a sweat drop appears. Ends in the resting nudge look.
         */
        fun pulse(context: Context) = synchronized(animationLock) {
            val store = WaterStore(context)
            val level = store.todayMl().toFloat()
            val n = 64
            for (i in 1..n) {
                val t = i / n.toFloat()
                val rise = ease(((i - 12) / 16f).coerceIn(0f, 1f))
                val drain = ((i - 38) / 14f).coerceIn(0f, 1f)
                val wobble = (1f - t / 0.5f).coerceAtLeast(0f)
                push(
                    context, store,
                    Frame(
                        totalMl = level,
                        phase = i * 0.5f,
                        nudge = 0.35f + (NUDGE_REST - 0.35f) * drain,
                        lineProgress = ((i - 4) / 9f).coerceIn(0f, 1f),
                        preview = rise * (1f - drain * drain),
                        rotate = 6f * sin(2 * PI * 4 * t).toFloat() * wobble,
                        hop = 5f * abs(sin(2 * PI * 2 * t)).toFloat() * wobble,
                        sweat = (((i - 34) / 6f).coerceIn(0f, 1f)) * (1f - ((i - 56) / 8f).coerceIn(0f, 1f)),
                    ),
                )
                Thread.sleep(FRAME_MS)
            }
            refresh(context)
        }

        /** After a tap: a happy little bounce with a few sparkles. */
        private fun cheer(context: Context, store: WaterStore, level: Float) {
            val n = 26
            for (i in 1..n) {
                val t = i / n.toFloat()
                val settle = 1f - t
                push(
                    context, store,
                    Frame(
                        level,
                        sparkle = t,
                        hop = 18f * abs(sin(2 * PI * t)).toFloat() * settle,
                        squash = 1f + 0.05f * sin(4 * PI * t).toFloat() * settle,
                    ),
                )
                Thread.sleep(FRAME_MS)
            }
        }

        /** Goal reached for the day: big hops with the shine and sparkles, while the creeper grows a level. */
        private fun celebrate(context: Context, store: WaterStore, level: Float, creeperFrom: Int, creeperTo: Int) {
            val n = 48
            for (i in 1..n) {
                val t = i / n.toFloat()
                val settle = 1f - t * 0.7f
                val bounce = abs(sin(3 * PI * t)).toFloat()
                push(
                    context, store,
                    Frame(
                        level,
                        celebrate = t,
                        hop = 34f * bounce * settle,
                        squash = 1f - 0.06f * (1f - bounce) * settle,
                        creeper = creeperFrom + (creeperTo - creeperFrom) * ease(((t - 0.15f) / 0.6f).coerceIn(0f, 1f)),
                    ),
                )
                Thread.sleep(FRAME_MS)
            }
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
