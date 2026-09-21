package com.example.water_tracker_v1.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The cat's expressions. [NONE] is the plain bottle, which is how the widget rests. */
enum class Face { NONE, OK, HAPPY, WORRY, NUDGE }

/** Everything that can change between two frames of the widget. */
data class Frame(
    val totalMl: Float,
    val phase: Float = 0f,
    /** 0..1 wave strength, decays after a tap. */
    val waveAmp: Float = 0f,
    /** 0..1 how strongly the gap to the target is shaded. */
    val nudge: Float = 0f,
    /** 0..1 how much of the dotted target line is drawn, left to right. */
    val lineProgress: Float = 1f,
    /** 0..1 how far translucent "preview" water has risen from the current level to the target. */
    val preview: Float = 0f,
    /** 0..1 progress of the goal-reached shine and sparkles; negative = off. */
    val celebrate: Float = -1f,
    val face: Face = Face.NONE,
    /** 0 = the face side is toward you, 1 = the plain back. The bottle turns around between the two. */
    val flip: Float = 1f,
    val blink: Boolean = false,
)

/**
 * Draws the widget as a bitmap. Widgets can't run animations, so we render frames and push them.
 * Everything is drawn in a 168 x 168 space, scaled up to [SIZE].
 */
object BottleRenderer {
    const val SIZE = 420
    private const val U = SIZE / 168f

    // Bottle body spans x 38..130 and y 54..134; a full bottle fills up into the neck.
    private const val BODY_L = 38f
    private const val BODY_R = 130f
    private const val BOTTOM = 134f
    private const val FULL_TOP = 44f

    private val BG = Color.parseColor("#FFD966")
    private val INK = Color.parseColor("#12324A")
    private val WATER = Color.parseColor("#4DB1FF")
    private val WATER_LIGHT = Color.parseColor("#8DD2FF")
    private val CAP = Color.parseColor("#FF6F91")

    private var numberFont: Typeface = Typeface.DEFAULT_BOLD
    private var loaded = false

    /** Bundled Nunito (variable weight) so the widget looks the same on every phone. */
    private fun loadFont(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            numberFont = Typeface.Builder(context.assets, "fonts/Nunito.ttf")
                .setFontVariationSettings("'wght' 800")
                .build() ?: numberFont
        }
    }

    private val bottlePath = Path().apply {
        val a = Math.toDegrees(atan2(-33.0, 8.0)).toFloat() // where the shoulder meets the neck, about -76 degrees
        moveTo(64f, 55f)
        lineTo(64f, 38f); quadTo(64f, 30f, 72f, 30f)
        lineTo(96f, 30f); quadTo(104f, 30f, 104f, 38f)
        lineTo(104f, 55f)
        arcTo(RectF(62f, 54f, 130f, 122f), a, -a)
        lineTo(130f, 100f)
        arcTo(RectF(62f, 66f, 130f, 134f), 0f, 90f)
        lineTo(72f, 134f)
        arcTo(RectF(38f, 66f, 106f, 134f), 90f, 90f)
        lineTo(38f, 88f)
        arcTo(RectF(38f, 54f, 106f, 122f), 180f, -a)
        close()
    }

    fun render(context: Context, goalMl: Int, paceFrac: Float, f: Frame): Bitmap {
        loadFont(context)
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        c.save()
        c.scale(U, U)
        p.color = BG
        c.drawRoundRect(RectF(0f, 0f, 168f, 168f), 28f, 28f, p)

        // 2 L goal -> two 1 L bottles; 3 L / 4 L -> one big bottle with a tick per litre.
        val bottles = if (goalMl <= 2000) 2 else 1
        val cap = goalMl.toFloat() / bottles
        val s = if (bottles == 2) 0.66f else 1f
        val ty = 68f - 82f * s
        val active = min(bottles - 1, (f.totalMl / cap).toInt()) // the bottle currently being filled wears the face

        for (i in 0 until bottles) {
            val cx = if (bottles == 2) (if (i == 0) 50f else 118f) else 84f
            val tx = cx - 84f * s
            val fill = ((f.totalMl - i * cap) / cap).coerceIn(0f, 1f)
            val paceLocal = (paceFrac * goalMl - i * cap) / cap
            val hasFace = i == active && f.face != Face.NONE

            p.style = Paint.Style.FILL; p.color = INK; p.alpha = 55
            c.drawOval(RectF(cx - 40f * s, ty + BOTTOM * s + 1f, cx + 40f * s, ty + BOTTOM * s + 1f + 10f * s), p)
            p.alpha = 255

            c.save()
            c.translate(tx, ty)
            c.scale(s, s)
            if (hasFace) { // the turn-around: squash about the bottle's centre
                val sx = abs(cos(f.flip * PI)).toFloat().coerceAtLeast(0.03f)
                c.translate(84f, 0f); c.scale(sx, 1f); c.translate(-84f, 0f)
            }
            val showFace = hasFace && f.flip < 0.5f
            if (showFace && f.face == Face.HAPPY) drawArms(c, p)
            drawBottle(
                c, p, fill, f, paceLocal,
                ticks = if (bottles == 1) goalMl / 1000 else 0,
                shine = if (i == active) f.celebrate else -1f,
                goalReached = f.totalMl >= goalMl,
            )
            if (showFace) drawFace(c, p, f.face, f.blink)
            c.restore()
        }

        if (f.celebrate in 0f..1f) drawSparkles(c, p, f.celebrate)
        if (f.totalMl >= goalMl) drawGoalBadge(c, p)
        c.restore()

        drawAmount(c, p, f.totalMl)
        return bmp
    }

    private fun levelY(frac: Float) = BOTTOM - (BOTTOM - FULL_TOP) * frac

    private fun drawBottle(
        c: Canvas, p: Paint, fill: Float, f: Frame, paceLocal: Float,
        ticks: Int, shine: Float, goalReached: Boolean,
    ) {
        p.style = Paint.Style.FILL; p.color = Color.WHITE
        c.drawPath(bottlePath, p)

        c.save()
        c.clipPath(bottlePath)
        if (fill > 0f) drawWave(c, p, levelY(fill), f.phase, f.waveAmp, WATER, 255)
        if (!goalReached) drawGhost(c, p, fill, paceLocal, f)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.strokeCap = Paint.Cap.ROUND; p.color = INK
        for (k in 1 until ticks) {
            val y = levelY(k.toFloat() / ticks)
            c.drawLine(BODY_L, y, BODY_L + 11f, y, p)
        }
        if (shine in 0f..1f) drawShine(c, p, shine)
        c.restore()

        p.style = Paint.Style.STROKE; p.strokeWidth = 4.5f; p.strokeJoin = Paint.Join.ROUND; p.color = INK
        c.drawPath(bottlePath, p)
        val capRect = RectF(66f, 20f, 102f, 34f)
        p.style = Paint.Style.FILL; p.color = CAP
        c.drawRoundRect(capRect, 6f, 6f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = INK
        c.drawRoundRect(capRect, 6f, 6f, p)
        p.strokeJoin = Paint.Join.MITER; p.strokeCap = Paint.Cap.BUTT
    }

    private fun drawWave(c: Canvas, p: Paint, y: Float, phase: Float, waveAmp: Float, color: Int, alpha: Int) {
        val amp = 0.6f + 5f * waveAmp
        val path = Path().apply {
            moveTo(BODY_L, BOTTOM + 4f)
            var x = BODY_L
            while (x <= BODY_R) {
                lineTo(x, y + amp * sin(((x - BODY_L) / (BODY_R - BODY_L) * 4 * PI + phase).toDouble()).toFloat())
                x += 2f
            }
            lineTo(BODY_R, BOTTOM + 4f); close()
        }
        p.style = Paint.Style.FILL; p.color = color; p.alpha = alpha
        c.drawPath(path, p)
        p.alpha = 255
    }

    /** Dotted line where the water should be by now, with a soft band showing the gap when behind. */
    private fun drawGhost(c: Canvas, p: Paint, fill: Float, paceLocal: Float, f: Frame) {
        if (paceLocal <= 0f || (paceLocal >= 1f && fill >= 1f)) return
        val yGhost = levelY(paceLocal.coerceAtMost(1f))
        val yWater = levelY(fill)
        if (yGhost < yWater) {
            p.style = Paint.Style.FILL; p.color = INK; p.alpha = (22 + 60 * f.nudge).toInt()
            c.drawRect(BODY_L, yGhost, BODY_R, yWater, p)
            p.alpha = 255
            if (f.preview > 0f) {
                // Translucent water rising from the current level toward the target line.
                val yTop = yWater + (yGhost - yWater) * f.preview
                drawWaveBetween(c, p, yTop, yWater, f.phase * 3)
            }
        }
        val end = BODY_L + (BODY_R - BODY_L) * f.lineProgress.coerceIn(0f, 1f)
        if (end > BODY_L) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.strokeCap = Paint.Cap.ROUND; p.color = INK
            p.pathEffect = DashPathEffect(floatArrayOf(1f, 6f), 0f)
            c.drawLine(BODY_L, yGhost, end, yGhost, p)
            p.pathEffect = null; p.strokeCap = Paint.Cap.BUTT
        }
    }

    private fun drawWaveBetween(c: Canvas, p: Paint, yTop: Float, yBottom: Float, phase: Float) {
        val path = Path().apply {
            moveTo(BODY_L, yBottom + 2f)
            var x = BODY_L
            while (x <= BODY_R) {
                lineTo(x, yTop + 2.2f * sin(((x - BODY_L) / (BODY_R - BODY_L) * 4 * PI + phase).toDouble()).toFloat())
                x += 2f
            }
            lineTo(BODY_R, yBottom + 2f); close()
        }
        p.style = Paint.Style.FILL; p.color = WATER_LIGHT; p.alpha = 235
        c.drawPath(path, p)
        p.alpha = 255
    }

    private fun drawShine(c: Canvas, p: Paint, t: Float) {
        val sweep = (t / 0.55f).coerceIn(0f, 1f)
        if (sweep >= 1f) return
        val x = BODY_L - 20f + (BODY_R - BODY_L + 40f) * sweep
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            x - 14f, 0f, x + 14f, 0f,
            intArrayOf(Color.TRANSPARENT, Color.argb(190, 255, 255, 255), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP,
        )
        c.drawRect(BODY_L, 20f, BODY_R, BOTTOM, p)
        p.shader = null
    }

    private fun drawArms(c: Canvas, p: Paint) {
        val left = Path().apply { moveTo(40f, 96f); quadTo(24f, 94f, 22f, 74f) }
        val right = Path().apply { moveTo(128f, 96f); quadTo(144f, 94f, 146f, 74f) }
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 10f; p.color = INK
        c.drawPath(left, p); c.drawPath(right, p)
        p.strokeWidth = 4.5f; p.color = Color.WHITE
        c.drawPath(left, p); c.drawPath(right, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun drawFace(c: Canvas, p: Paint, face: Face, blink: Boolean) {
        p.pathEffect = null; p.shader = null
        fun eye(cx: Float, cy: Float, r: Float, hx: Float, hy: Float, hr: Float) {
            if (blink) {
                p.style = Paint.Style.STROKE; p.strokeWidth = 3.5f; p.strokeCap = Paint.Cap.ROUND; p.color = INK
                c.drawLine(cx - 5f, cy, cx + 5f, cy, p)
                return
            }
            p.style = Paint.Style.FILL; p.color = INK; c.drawCircle(cx, cy, r, p)
            p.color = Color.WHITE; c.drawCircle(cx + hx, cy + hy, hr, p)
        }
        fun cheeks() {
            p.style = Paint.Style.FILL; p.color = Color.argb(190, 255, 111, 145)
            c.drawCircle(56f, 108f, 5f, p); c.drawCircle(112f, 108f, 5f, p)
        }
        fun whiskers() {
            p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.strokeCap = Paint.Cap.ROUND
            p.color = INK; p.alpha = 180
            c.drawLine(44f, 106f, 53f, 106f, p); c.drawLine(44f, 112f, 53f, 112f, p)
            c.drawLine(115f, 106f, 124f, 106f, p); c.drawLine(115f, 112f, 124f, 112f, p)
            p.alpha = 255
        }
        fun stroke(w: Float) { p.style = Paint.Style.STROKE; p.strokeWidth = w; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.color = INK }

        when (face) {
            Face.NONE -> Unit
            Face.OK -> {
                eye(66f, 98f, 4.4f, -1.2f, -1.4f, 1.4f); eye(102f, 98f, 4.4f, -1.2f, -1.4f, 1.4f)
                cheeks()
                stroke(3.5f)
                c.drawPath(Path().apply { moveTo(73f, 108f); quadTo(78.5f, 114f, 84f, 108f); quadTo(89.5f, 114f, 95f, 108f) }, p)
                whiskers()
            }
            Face.HAPPY -> {
                stroke(3.6f)
                c.drawPath(Path().apply { moveTo(59f, 101f); quadTo(66f, 91f, 73f, 101f) }, p)
                c.drawPath(Path().apply { moveTo(95f, 101f); quadTo(102f, 91f, 109f, 101f) }, p)
                cheeks()
                val mouth = Path().apply {
                    moveTo(72f, 107f); lineTo(96f, 107f); quadTo(94f, 121f, 84f, 121f); quadTo(74f, 121f, 72f, 107f); close()
                }
                p.style = Paint.Style.FILL_AND_STROKE; p.strokeWidth = 2f; p.color = INK
                c.drawPath(mouth, p)
                p.style = Paint.Style.FILL; p.color = CAP
                c.drawOval(RectF(78.5f, 113f, 89.5f, 119f), p)
                whiskers()
            }
            Face.WORRY -> {
                stroke(3.2f)
                c.drawLine(56f, 90f, 74f, 85f, p); c.drawLine(94f, 85f, 112f, 90f, p)
                eye(66f, 99f, 4.4f, -1.2f, -1.4f, 1.4f); eye(102f, 99f, 4.4f, -1.2f, -1.4f, 1.4f)
                stroke(3.5f)
                c.drawPath(Path().apply { moveTo(73f, 113f); quadTo(78.5f, 107f, 84f, 113f); quadTo(89.5f, 119f, 95f, 113f) }, p)
                val drop = Path().apply { moveTo(122f, 72f); quadTo(116f, 81f, 122f, 84f); quadTo(128f, 81f, 122f, 72f); close() }
                p.style = Paint.Style.FILL; p.color = WATER_LIGHT; c.drawPath(drop, p)
                stroke(2f); c.drawPath(drop, p)
                whiskers()
            }
            Face.NUDGE -> {
                eye(66f, 97f, 7.5f, 2.5f, -3.4f, 2.6f); eye(102f, 97f, 7.5f, 2.5f, -3.4f, 2.6f)
                cheeks()
                p.style = Paint.Style.FILL; p.color = INK
                c.drawOval(RectF(79.8f, 107.4f, 88.2f, 114.6f), p)
                whiskers()
            }
        }
        p.strokeCap = Paint.Cap.BUTT; p.strokeJoin = Paint.Join.MITER
    }

    private fun drawSparkles(c: Canvas, p: Paint, t: Float) {
        // (x, y in the 168 space, start offset in 0..0.5)
        val sparkles = listOf(
            Triple(20f, 38f, 0.10f), Triple(148f, 32f, 0.22f), Triple(154f, 100f, 0.30f),
            Triple(18f, 112f, 0.05f), Triple(84f, 12f, 0.36f),
        )
        for ((x, y, start) in sparkles) {
            val local = ((t - start) / 0.5f).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue
            val r = 9f * sin(local * PI.toFloat())
            val cy = y - 5f * local
            val star = Path().apply {
                moveTo(x, cy - r); quadTo(x, cy, x + r, cy); quadTo(x, cy, x, cy + r)
                quadTo(x, cy, x - r, cy); quadTo(x, cy, x, cy - r); close()
            }
            p.style = Paint.Style.FILL; p.color = Color.WHITE; c.drawPath(star, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = INK; c.drawPath(star, p)
        }
    }

    /** Small check badge in the corner once the goal is reached. */
    private fun drawGoalBadge(c: Canvas, p: Paint) {
        p.style = Paint.Style.FILL; p.color = WATER; c.drawCircle(144f, 24f, 13f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = INK; c.drawCircle(144f, 24f, 13f, p)
        p.strokeWidth = 3.4f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.color = Color.WHITE
        c.drawPath(Path().apply { moveTo(138f, 24f); lineTo(142f, 28f); lineTo(150f, 19f) }, p)
        p.strokeCap = Paint.Cap.BUTT; p.strokeJoin = Paint.Join.MITER
    }

    /** Big number with a smaller unit: "1.5 L", or "700 ml" below one litre. */
    private fun drawAmount(c: Canvas, p: Paint, totalMl: Float) {
        val (number, unit) = if (totalMl >= 1000f) {
            "%.2f".format(totalMl / 1000f).trimEnd('0').trimEnd('.') to "L"
        } else {
            totalMl.toInt().toString() to "ml"
        }
        p.style = Paint.Style.FILL; p.color = INK; p.textAlign = Paint.Align.LEFT
        p.typeface = numberFont; p.textSize = 62f
        val numW = p.measureText(number)
        p.textSize = 38f
        val unitW = p.measureText(unit)
        val gap = 8f
        val x = (SIZE - (numW + gap + unitW)) / 2f
        val baseline = SIZE - 28f
        p.textSize = 62f
        c.drawText(number, x, baseline, p)
        p.textSize = 38f; p.alpha = 175
        c.drawText(unit, x + numW + gap, baseline, p)
        p.alpha = 255
    }
}
