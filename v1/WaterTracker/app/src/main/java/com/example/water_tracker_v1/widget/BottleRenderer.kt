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
import kotlin.math.sin

/** Draws the widget as a bitmap. Widgets can't run animations, so we render frames and push them. */
object BottleRenderer {
    const val SIZE = 420

    private val BG = Color.parseColor("#EAF6FB")
    private val GLASS = Color.parseColor("#B7D7E8")
    private val WATER = Color.parseColor("#2E9BF0")
    private val WATER_BACK = Color.parseColor("#7CC3F7")
    private val TEXT = Color.parseColor("#12324A")
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

    /**
     * @param totalMl  amount to draw (may be mid-animation)
     * @param paceFrac fraction of goal the user should have reached now
     * @param waveAmp  0..1 wave strength, decays after a tap
     * @param nudge    0..1 how strongly to highlight the gap to the target (0 = normal)
     * @param lineProgress 0..1 how much of the dotted target line is drawn (left to right)
     * @param celebrate 0..1 progress of the goal-reached shine and sparkles (negative = off)
     * @param preview  0..1 how far translucent "preview" water has risen from the current level to the target
     */
    fun render(context: Context, totalMl: Float, goalMl: Int, paceFrac: Float, phase: Float, waveAmp: Float, nudge: Float = 0f,
        lineProgress: Float = 1f, preview: Float = 0f, celebrate: Float = -1f,
    ): Bitmap {
        loadFont(context)
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.color = BG
        c.drawRoundRect(RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), 56f, 56f, p)

        // 2 L goal -> two 1 L bottles; 3 L / 4 L -> one big bottle.
        val bottles = if (goalMl <= 2000) 2 else 1
        val cap = goalMl.toFloat() / bottles
        val top = 40f
        val bottom = SIZE - 104f
        val bodyW = if (bottles == 2) 120f else 190f
        val gap = SIZE / (bottles + 1f)

        for (i in 0 until bottles) {
            val cx = gap * (i + 1)
            val fill = ((totalMl - i * cap) / cap).coerceIn(0f, 1f)
            val bottle = bottlePath(cx, top, bottom, bodyW)
            drawWater(c, p, bottle, cx, top, bottom, bodyW, fill, phase + i * 1.3f, waveAmp)

            if (nudge > 0f) {
                p.style = Paint.Style.STROKE; p.strokeWidth = 8f + 16f * nudge; p.color = WATER
                p.alpha = (110 * nudge).toInt()
                c.drawPath(bottle, p)
                p.alpha = 255
            }
            p.style = Paint.Style.STROKE; p.strokeWidth = 7f; p.color = GLASS
            c.drawPath(bottle, p)

            if (totalMl < goalMl) drawGhostLevel(c, p, bottle, cx, top, bottom, bodyW, fill, (paceFrac * goalMl - i * cap) / cap, nudge, lineProgress, preview, phase)
        }

        if (celebrate in 0f..1f) drawCelebration(c, p, celebrate, bottles, gap, top, bottom, bodyW)
        if (totalMl >= goalMl) drawGoalBadge(c, p)
        drawAmount(c, p, totalMl)
        return bmp
    }

    /** A soft diagonal shine sweeps across each bottle, then sparkles pop around them. */
    private fun drawCelebration(c: Canvas, p: Paint, t: Float, bottles: Int, gap: Float, top: Float, bottom: Float, bodyW: Float) {
        val sweep = (t / 0.55f).coerceIn(0f, 1f)
        if (sweep < 1f) {
            for (i in 0 until bottles) {
                val cx = gap * (i + 1)
                c.save()
                c.clipPath(bottlePath(cx, top, bottom, bodyW))
                val x = cx - bodyW + (bodyW * 2.2f) * sweep
                p.style = Paint.Style.FILL
                p.shader = LinearGradient(
                    x - 40f, 0f, x + 40f, 0f,
                    intArrayOf(Color.TRANSPARENT, Color.argb(170, 255, 255, 255), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP,
                )
                c.drawRect(cx - bodyW, top, cx + bodyW, bottom, p)
                p.shader = null
                c.restore()
            }
        }
        // (x, y as fractions of the widget, start offset in 0..0.5)
        val sparkles = listOf(
            Triple(0.14f, 0.20f, 0.10f), Triple(0.86f, 0.24f, 0.22f), Triple(0.22f, 0.62f, 0.32f),
            Triple(0.80f, 0.58f, 0.05f), Triple(0.50f, 0.10f, 0.28f), Triple(0.92f, 0.44f, 0.40f),
            Triple(0.08f, 0.42f, 0.18f),
        )
        p.style = Paint.Style.FILL; p.color = WATER
        for ((fx, fy, start) in sparkles) {
            val local = ((t - start) / 0.5f).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue
            val r = 26f * sin(local * PI.toFloat())
            p.alpha = (255 * sin(local * PI.toFloat())).toInt()
            val cx = fx * SIZE
            val cy = fy * SIZE - 12f * local
            val star = Path().apply {
                moveTo(cx, cy - r); quadTo(cx, cy, cx + r, cy); quadTo(cx, cy, cx, cy + r)
                quadTo(cx, cy, cx - r, cy); quadTo(cx, cy, cx, cy - r); close()
            }
            c.drawPath(star, p)
        }
        p.alpha = 255
    }

    /** Small check badge in the corner once the goal is reached. */
    private fun drawGoalBadge(c: Canvas, p: Paint) {
        val cx = SIZE - 52f
        val cy = 52f
        p.style = Paint.Style.FILL; p.color = WATER
        c.drawCircle(cx, cy, 30f, p)
        p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.strokeWidth = 7f
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        c.drawPath(Path().apply { moveTo(cx - 12f, cy + 1f); lineTo(cx - 3f, cy + 10f); lineTo(cx + 13f, cy - 9f) }, p)
        p.strokeCap = Paint.Cap.BUTT; p.strokeJoin = Paint.Join.MITER
    }

    /** Big number with a smaller unit: "1.5 L", or "750 ml" below one litre. */
    private fun drawAmount(c: Canvas, p: Paint, totalMl: Float) {
        val (number, unit) = if (totalMl >= 1000f) {
            "%.2f".format(totalMl / 1000f).trimEnd('0').trimEnd('.') to "L"
        } else {
            totalMl.toInt().toString() to "ml"
        }
        p.style = Paint.Style.FILL; p.color = TEXT; p.textAlign = Paint.Align.LEFT
        p.typeface = numberFont; p.textSize = 66f
        val numW = p.measureText(number)
        p.typeface = numberFont; p.textSize = 42f
        val unitW = p.measureText(unit)
        val gap = 8f
        val x = (SIZE - (numW + gap + unitW)) / 2f
        val baseline = SIZE - 30f
        p.typeface = numberFont; p.textSize = 66f
        c.drawText(number, x, baseline, p)
        p.typeface = numberFont; p.textSize = 42f; p.alpha = 170
        c.drawText(unit, x + numW + gap, baseline, p)
        p.alpha = 255
    }

    /** Faint dotted line where the water should be by now, with a soft band showing the gap when behind. */
    private fun drawGhostLevel(
        c: Canvas, p: Paint, bottle: Path, cx: Float, top: Float, bottom: Float,
        bodyW: Float, fill: Float, paceLocal: Float, nudge: Float,
        lineProgress: Float, preview: Float, phase: Float,
    ) {
        if (paceLocal <= 0f || (paceLocal >= 1f && fill >= 1f)) return
        val yGhost = levelY(top, bottom, paceLocal.coerceAtMost(1f))
        val yWater = levelY(top, bottom, fill)
        val left = cx - bodyW / 2
        val right = cx + bodyW / 2
        c.save()
        c.clipPath(bottle)
        if (yGhost < yWater) {
            p.style = Paint.Style.FILL; p.color = WATER; p.alpha = (40 + 110 * nudge).toInt()
            c.drawRect(left, yGhost, right, yWater, p)
            if (preview > 0f) {
                // Translucent water rising from the current level toward the target line.
                val yTop = yWater + (yGhost - yWater) * preview
                val path = Path().apply {
                    moveTo(left, yWater + 4f)
                    var x = left
                    while (x <= right) {
                        lineTo(x, yTop + 5f * sin(((x - left) / bodyW * 2 * PI + phase * 3).toDouble()).toFloat())
                        x += 6f
                    }
                    lineTo(right, yWater + 4f); close()
                }
                p.color = WATER_BACK; p.alpha = 210
                c.drawPath(path, p)
            }
        }
        val lineEnd = left + bodyW * lineProgress.coerceIn(0f, 1f)
        if (lineEnd > left) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 5f + 4f * nudge; p.strokeCap = Paint.Cap.ROUND
            p.color = WATER; p.alpha = (180 + 75 * nudge).toInt()
            p.pathEffect = DashPathEffect(floatArrayOf(2f, 12f), 0f)
            c.drawLine(left, yGhost, lineEnd, yGhost, p)
            p.pathEffect = null; p.alpha = 255; p.strokeCap = Paint.Cap.BUTT
        }
        c.restore()
    }

    private fun levelY(top: Float, bottom: Float, frac: Float): Float {
        // Water lives in the body (below the neck), so map 0..1 onto the body only.
        val bodyTop = top + (bottom - top) * 0.28f
        return bottom - (bottom - bodyTop) * frac
    }

    private fun drawWater(
        c: Canvas, p: Paint, bottle: Path, cx: Float, top: Float, bottom: Float,
        bodyW: Float, fill: Float, phase: Float, amp: Float,
    ) {
        if (fill <= 0f) return
        val y = levelY(top, bottom, fill)
        val left = cx - bodyW / 2
        val right = cx + bodyW / 2
        c.save()
        c.clipPath(bottle)
        p.style = Paint.Style.FILL
        for ((layer, color) in listOf(WATER_BACK, WATER).withIndex()) {
            val a = (1.5f + 13f * amp) * (if (layer == 0) 0.7f else 1f)
            val shift = if (layer == 0) 1.7f else 0f
            val path = Path().apply {
                moveTo(left, bottom)
                var x = left
                while (x <= right) {
                    val yy = y + a * sin(((x - left) / bodyW * 2 * PI + phase + shift).toDouble()).toFloat()
                    lineTo(x, yy)
                    x += 6f
                }
                lineTo(right, bottom); close()
            }
            p.color = color
            c.drawPath(path, p)
        }
        c.restore()
    }

    private fun bottlePath(cx: Float, top: Float, bottom: Float, bodyW: Float): Path {
        val neckW = bodyW * 0.42f
        val bodyTop = top + (bottom - top) * 0.28f
        val body = Path().apply { addRoundRect(RectF(cx - bodyW / 2, bodyTop, cx + bodyW / 2, bottom), 40f, 40f, Path.Direction.CW) }
        val neck = Path().apply { addRoundRect(RectF(cx - neckW / 2, top, cx + neckW / 2, bodyTop + 30f), 10f, 10f, Path.Direction.CW) }
        return Path(body).apply { op(neck, Path.Op.UNION) }
    }
}
