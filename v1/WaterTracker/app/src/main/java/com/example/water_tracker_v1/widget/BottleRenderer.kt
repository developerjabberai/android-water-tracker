package com.example.water_tracker_v1.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
    private val MARKER = Color.parseColor("#FF7043")
    private val TEXT = Color.parseColor("#12324A")

    /**
     * @param totalMl  amount to draw (may be mid-animation)
     * @param paceFrac fraction of goal the user should have reached now
     * @param waveAmp  0..1 wave strength, decays after a tap
     */
    fun render(totalMl: Float, goalMl: Int, paceFrac: Float, phase: Float, waveAmp: Float): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.color = BG
        c.drawRoundRect(RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), 56f, 56f, p)

        // 2 L goal -> two 1 L bottles; 3 L / 4 L -> one big bottle.
        val bottles = if (goalMl <= 2000) 2 else 1
        val cap = goalMl.toFloat() / bottles
        val top = 40f
        val bottom = SIZE - 96f
        val bodyW = if (bottles == 2) 120f else 190f
        val gap = SIZE / (bottles + 1f)

        for (i in 0 until bottles) {
            val cx = gap * (i + 1)
            val fill = ((totalMl - i * cap) / cap).coerceIn(0f, 1f)
            val bottle = bottlePath(cx, top, bottom, bodyW)
            drawWater(c, p, bottle, cx, top, bottom, bodyW, fill, phase + i * 1.3f, waveAmp)

            p.style = Paint.Style.STROKE; p.strokeWidth = 7f; p.color = GLASS
            c.drawPath(bottle, p)

            // Pace marker sits in whichever bottle contains the pace point.
            val paceMl = paceFrac * goalMl
            val local = (paceMl - i * cap) / cap
            if (local in 0f..1f) {
                val y = levelY(top, bottom, local)
                p.color = MARKER; p.strokeWidth = 6f; p.style = Paint.Style.STROKE
                c.drawLine(cx - bodyW / 2 - 14f, y, cx + bodyW / 2 + 14f, y, p)
                p.style = Paint.Style.FILL
                val tri = Path().apply {
                    moveTo(cx - bodyW / 2 - 14f, y); lineTo(cx - bodyW / 2 - 34f, y - 13f); lineTo(cx - bodyW / 2 - 34f, y + 13f); close()
                }
                c.drawPath(tri, p)
            }
        }

        p.style = Paint.Style.FILL; p.color = TEXT
        p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 50f
        c.drawText("%.2f / %.0f L".format(totalMl / 1000f, goalMl / 1000f), SIZE / 2f, SIZE - 38f, p)
        return bmp
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
            val a = (6f + 10f * amp) * (if (layer == 0) 0.7f else 1f)
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
