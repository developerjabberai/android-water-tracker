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
                // Claim the animation right away so a reminder pulse (or an earlier tap) mid-flight bails
                // out on its next frame instead of running to the end before this tap gets to show anything.
                val myToken = runToken.incrementAndGet()
                push(context, store, Frame(to.toFloat())) // instant feedback while we wait for the lock
                synchronized(animationLock) {
                    if (myToken != runToken.get()) return@synchronized
                    animateFill(context, store, from.toFloat(), to.toFloat(), myToken)
                    if (myToken != runToken.get()) return@synchronized
                    val grew = store.creditGoalIfMet()
                    if (grew != null) celebrate(context, store, to.toFloat(), grew.first, grew.second, myToken) else cheer(context, store, to.toFloat(), myToken)
                    if (myToken == runToken.get()) refresh(context)
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

        /**
         * Bumped every time a new animation should take over (a tap, or a fresh pulse). A running loop
         * checks it each frame and bails out as soon as it's no longer the current one, instead of running
         * to completion — otherwise a tap during the ~6.5s reminder pulse would sit unseen until the pulse
         * finished. addTap() itself already wrote the new total before any of this, so the count is never
         * lost; this only affects how quickly the widget shows it.
         */
        private val runToken = java.util.concurrent.atomic.AtomicInteger(0)

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
         * The reminder, built to be impossible to miss (about 6.5 s):
         * play (a happy little bounce and wobble, like it's glad to see you), then alert (the dotted
         * target line draws, the gap fills, ping rings and wobbles repeat).
         */
        fun pulse(context: Context) {
            val myToken = runToken.incrementAndGet()
            synchronized(animationLock) {
                if (myToken != runToken.get()) return@synchronized
                val store = WaterStore(context)
                val level = store.todayMl().toFloat()
                for (i in 1..PULSE_FRAMES) {
                    if (myToken != runToken.get()) return@synchronized
                    push(context, store, pulseFrame(i, level))
                    Thread.sleep(FRAME_MS)
                }
                refresh(context)
            }
        }

        private const val PLAY_END = 50
        private const val PULSE_FRAMES = 156

        private fun pulseFrame(i: Int, level: Float): Frame {
            return when {
                i <= PLAY_END -> {
                    // A happy little bounce and wobble on the real character, at its real level — no
                    // shape changes, just playful motion — instead of the old shake/burst/rebuild. Sized
                    // to actually read at a glance (this replaces an "impossible to miss" flash/burst
                    // effect, so it needs real amplitude, not a barely-there wobble).
                    val t = i / PLAY_END.toFloat()
                    val settle = 1f - t * 0.5f
                    val bounce = abs(sin(t * PI * 3.4)).toFloat()
                    val wag = sin(t * PI * 7).toFloat()
                    Frame(
                        totalMl = level,
                        hop = 46f * bounce * settle,
                        squash = 1f - 0.16f * bounce * settle,
                        rotate = 14f * wag * settle,
                        shakeX = 8f * wag * settle,
                    )
                }
                else -> {
                    val j = i - PLAY_END
                    val rise = ease(((j - 8) / 16f).coerceIn(0f, 1f))
                    val drain = ((j - 62) / 14f).coerceIn(0f, 1f)
                    var rotate = 0f; var hop = 0f
                    for (k in intArrayOf(0, 30, 60, 90)) {
                        if (j in k..(k + 12)) {
                            val tt = (j - k) / 12f
                            rotate = 8f * sin(2 * PI * 3 * tt).toFloat() * (1f - tt)
                            hop = 10f * abs(sin(2 * PI * 1.5 * tt)).toFloat() * (1f - tt)
                        }
                    }
                    Frame(
                        totalMl = level, phase = j * 0.5f, nudge = 0.6f,
                        lineProgress = ((j - 2) / 9f).coerceIn(0f, 1f),
                        preview = rise * (1f - drain * drain),
                        rotate = rotate, hop = hop,
                        sweat = ((j - 58) / 6f).coerceIn(0f, 1f) * (1f - ((j - 92) / 8f).coerceIn(0f, 1f)),
                    )
                }
            }
        }

        /** After a tap: a happy little bounce with a few sparkles. */
        private fun cheer(context: Context, store: WaterStore, level: Float, myToken: Int) {
            val n = 26
            for (i in 1..n) {
                if (myToken != runToken.get()) return
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
        private fun celebrate(context: Context, store: WaterStore, level: Float, creeperFrom: Int, creeperTo: Int, myToken: Int) {
            val n = 48
            for (i in 1..n) {
                if (myToken != runToken.get()) return
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
        private fun animateFill(context: Context, store: WaterStore, from: Float, to: Float, myToken: Int) {
            for (i in 1..FILL_FRAMES) {
                if (myToken != runToken.get()) return
                val t = i / FILL_FRAMES.toFloat()
                val eased = 1f - (1f - t).pow(3)
                push(context, store, Frame(from + (to - from) * eased, phase = t * 9f, waveAmp = 1f - t))
                Thread.sleep(FRAME_MS)
            }
        }

        private fun push(context: Context, store: WaterStore, frame: Frame) {
            val (manager, ids) = ids(context)
            if (ids.isEmpty()) return
            val pace = store.paceOverrideForTesting(context) ?: Pace.fraction(LocalTime.now())
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
