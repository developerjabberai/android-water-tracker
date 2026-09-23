package com.developerjabberai.watertracker.widget

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
import com.developerjabberai.watertracker.data.WaterStore
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

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
    /** 0..1 progress of the goal-reached shine; negative = off. */
    val celebrate: Float = -1f,
    /** 0..1 progress of a small sparkle burst; negative = off. */
    val sparkle: Float = -1f,
    /** Degrees the character leans, about its feet. */
    val rotate: Float = 0f,
    /** Vertical squash and stretch about its feet (1 = normal). */
    val squash: Float = 1f,
    /** How far the character has hopped up, in pixels. */
    val hop: Float = 0f,
    /** 0..1 sweat drop while worried. */
    val sweat: Float = 0f,
    /** Which of the four characters; negative means today's pick. */
    val art: Int = -1,
    /** Creeper growth 1..10 (may be fractional while it grows); 0 hides it; negative means the saved level. */
    val creeper: Float = -1f,
    /** Progress 0..1 of the blast: shockwave rings and a burst of pieces that flies out and is pulled back. Negative = off. */
    val blast: Float = -1f,
    /** Progress 0..1 of a single ping ring around the bottle. Negative = off. */
    val ring: Float = -1f,
    /** 0..1 strength of a colour flash over the whole card. */
    val flash: Float = 0f,
    val flashColor: Int = Color.WHITE,
    /** Size of the character (1 = normal), grown from the feet. */
    val scale: Float = 1f,
    /** 0..1 how visible the character is; 0 while it is blasted apart. */
    val opacity: Float = 1f,
    /** Sideways shake in pixels. */
    val shakeX: Float = 0f,
)

/**
 * Draws the widget as a bitmap. Widgets can't run animations, so we render frames and push them.
 * The character comes from [ArtLibrary]; we keep its outline, face and gloss exactly as drawn and swap its
 * blue body for our own water, which rises with what you've drunk.
 */
object BottleRenderer {
    const val SIZE = 420

    // Where the character stands on the card, in pixels.
    private const val ART_MAX_W = 270f
    private const val ART_MAX_H = 300f
    private const val ART_TOP = 12f

    /** Each character has its own background colour, so the widget's colour changes with the day's character. */
    private val BACKGROUNDS = intArrayOf(
        Color.parseColor("#FFD966"), // sunny yellow
        Color.parseColor("#FFB995"), // peach
        Color.parseColor("#FFB3CF"), // pink
        Color.parseColor("#C9B6FF"), // lavender
    )
    private val INK = Color.parseColor("#12324A")
    private val GLASS = Color.parseColor("#EAF4FF")
    private val WATER_LIGHT = Color.parseColor("#9BCCF9")

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

    fun render(context: Context, goalMl: Int, paceFrac: Float, f: Frame): Bitmap {
        loadFont(context)
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        val artIndex = if (f.art >= 0) f.art else WaterStore(context).artToday()
        val card = RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat())
        p.color = BACKGROUNDS[artIndex.coerceIn(0, BACKGROUNDS.size - 1)]
        c.drawRoundRect(card, 70f, 70f, p)
        if (f.flash > 0f) {
            p.color = f.flashColor; p.alpha = (255 * f.flash.coerceIn(0f, 1f)).toInt()
            c.drawRoundRect(card, 70f, 70f, p)
            p.alpha = 255
        }

        val art = ArtLibrary.get(context, artIndex)
        val s = min(ART_MAX_W / art.outline.width(), ART_MAX_H / art.outline.height())
        val cx = SIZE / 2f
        val feetY = ART_TOP + art.outline.height() * s

        // Soft shadow that shrinks as the character hops.
        p.style = Paint.Style.FILL; p.color = INK
        p.alpha = (50 * (1f - (f.hop / 90f).coerceIn(0f, 0.6f))).toInt()
        val shadowW = art.outline.width() * s * 0.36f * (1f - (f.hop / 200f).coerceIn(0f, 0.4f))
        c.drawOval(RectF(cx - shadowW, feetY - 2f, cx + shadowW, feetY + 14f), p)
        p.alpha = 255

        val fill = (f.totalMl / goalMl).coerceIn(0f, 1f)
        val goalReached = f.totalMl >= goalMl
        val litres = (goalMl / 1000).coerceAtLeast(1)
        // Creeper is disabled for now (looked congested on the widget); level-tracking still runs quietly in the background.

        val centerY = feetY - art.outline.height() * s * 0.45f
        if (f.blast in 0f..1f) drawShockwave(c, p, cx + f.shakeX, centerY, f.blast)
        if (f.ring in 0f..1f) drawPing(c, p, cx, centerY, f.ring)

        if (f.opacity > 0f) {
            val layer = c.saveLayerAlpha(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), (255 * f.opacity.coerceIn(0f, 1f)).toInt())
            c.save()
            c.translate(cx + f.shakeX, feetY - f.hop)
            c.rotate(f.rotate)
            c.scale(f.scale, f.scale)
            c.scale(1f, f.squash)
            c.scale(s, s)
            c.translate(-(art.outline.centerX()), -art.outline.bottom)
            drawArt(c, p, art, fill, paceFrac, litres, goalReached, f)
            if (f.sweat > 0f) drawSweat(c, p, art, f.sweat)
            c.restore()
            c.restoreToCount(layer)
        }
        if (f.blast in 0f..1f) drawPieces(c, p, cx + f.shakeX, centerY, f.blast)

        if (f.sparkle in 0f..1f) drawSparkles(c, p, f.sparkle, small = true)
        if (f.celebrate in 0f..1f) drawSparkles(c, p, f.celebrate, small = false)
        if (goalReached) drawGoalBadge(c, p)

        drawAmount(c, p, f.totalMl)
        return bmp
    }

    /** Two expanding rings, like a shockwave. */
    private fun drawShockwave(c: Canvas, p: Paint, x: Float, y: Float, b: Float) {
        p.style = Paint.Style.STROKE; p.shader = null; p.pathEffect = null
        for ((delay, color) in listOf(0f to Color.WHITE, 0.16f to Color.parseColor("#FF6F91"))) {
            val t = ((b - delay) / (1f - delay)).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) continue
            p.strokeWidth = 30f * (1f - t) + 4f
            p.color = color; p.alpha = (255 * (1f - t)).toInt()
            c.drawCircle(x, y, 40f + 250f * t, p)
        }
        p.alpha = 255
    }

    /** A single soft ring that pings outward, to draw the eye again and again. */
    private fun drawPing(c: Canvas, p: Paint, x: Float, y: Float, t: Float) {
        p.style = Paint.Style.STROKE; p.shader = null; p.pathEffect = null
        p.strokeWidth = 14f * (1f - t) + 3f
        p.color = Color.WHITE; p.alpha = (230 * (1f - t)).toInt()
        c.drawCircle(x, y, 60f + 170f * t, p)
        p.alpha = 255
    }

    private class Piece(val angle: Double, val speed: Float, val size: Float, val kind: Int)

    private val pieces: List<Piece> = kotlin.random.Random(7).let { r ->
        List(34) { i -> Piece(i * (2 * PI / 34) + r.nextDouble(-0.12, 0.12), r.nextFloat() * 110f + 110f, r.nextFloat() * 8f + 6f, i % 4) }
    }

    /** The bottle bursts into droplets, sparkles, leaves and petals that fly out, then are pulled back in. */
    private fun drawPieces(c: Canvas, p: Paint, x: Float, y: Float, b: Float) {
        val out = Math.pow(sin(PI * b), 0.8).toFloat()      // out and back
        val fade = 1f - ((b - 0.85f) / 0.15f).coerceIn(0f, 1f)
        for (piece in pieces) {
            val px = x + (kotlin.math.cos(piece.angle) * piece.speed * out).toFloat()
            val py = y + (sin(piece.angle) * piece.speed * out).toFloat()
            val r = piece.size * (0.6f + 0.6f * out)
            p.shader = null
            p.alpha = (255 * fade).toInt()
            when (piece.kind) {
                0 -> { p.style = Paint.Style.FILL; p.color = WATER_LIGHT; c.drawCircle(px, py, r, p)
                       p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = INK; c.drawCircle(px, py, r, p) }
                1 -> { val star = Path().apply {
                           moveTo(px, py - r * 1.7f); quadTo(px, py, px + r * 1.7f, py); quadTo(px, py, px, py + r * 1.7f)
                           quadTo(px, py, px - r * 1.7f, py); quadTo(px, py, px, py - r * 1.7f); close() }
                       p.style = Paint.Style.FILL; p.color = Color.WHITE; c.drawPath(star, p)
                       p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = INK; c.drawPath(star, p) }
                2 -> { p.style = Paint.Style.FILL; p.color = Color.parseColor("#5CC46B")
                       c.save(); c.rotate((piece.angle * 57.3).toFloat(), px, py)
                       c.drawOval(RectF(px - r * 1.5f, py - r * 0.8f, px + r * 1.5f, py + r * 0.8f), p)
                       p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = INK
                       c.drawOval(RectF(px - r * 1.5f, py - r * 0.8f, px + r * 1.5f, py + r * 0.8f), p); c.restore() }
                else -> { p.style = Paint.Style.FILL; p.color = Color.parseColor("#FF6F91"); c.drawCircle(px, py, r * 0.9f, p)
                          p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = INK; c.drawCircle(px, py, r * 0.9f, p) }
            }
        }
        p.alpha = 255
    }

    /** Water sits between the body's bottom and just below its top. */
    private fun levelY(art: Art, frac: Float): Float {
        val top = art.body.top + art.body.height() * 0.13f
        return art.body.bottom - (art.body.bottom - top) * frac
    }

    private fun drawArt(c: Canvas, p: Paint, art: Art, fill: Float, paceFrac: Float, litres: Int, goalReached: Boolean, f: Frame) {
        val waterY = levelY(art, fill)
        for ((i, layer) in art.layers.withIndex()) {
            when (layer.kind) {
                LayerKind.BODY -> {
                    fillLayer(c, p, layer, GLASS)
                    c.save()
                    c.clipPath(layer.path)
                    if (fill > 0f) drawWave(c, p, art, waterY, f.phase, f.waveAmp, art.bodyColor, 255)
                    if (i == art.bodyLayer) {
                        drawTicks(c, p, art, litres)
                        if (f.celebrate in 0f..1f) drawShine(c, p, art, f.celebrate)
                    }
                    c.restore()
                }
                LayerKind.SHADE -> if (fill > 0f) {
                    // The character's darker blue shading only makes sense on water.
                    c.save()
                    c.clipPath(layer.path)
                    c.clipRect(0f, waterY, art.size, art.size)
                    fillLayer(c, p, layer, null)
                    c.restore()
                }
                LayerKind.OTHER -> fillLayer(c, p, layer, null)
            }
        }
        // Drawn last, on top of ears/whiskers/highlights (which would otherwise paint over it since they
        // come after the water body in the layer stack), but still clipped to the full outline so it never
        // spills past the character's silhouette.
        if (!goalReached) {
            drawGhost(c, p, art, fill, paceFrac, f)
        }
    }

    private fun fillLayer(c: Canvas, p: Paint, layer: ArtLayer, override: Int?) {
        p.style = Paint.Style.FILL
        p.pathEffect = null
        if (override != null) {
            p.shader = null; p.color = override
        } else if (layer.shader != null) {
            p.color = Color.WHITE; p.shader = layer.shader
        } else {
            p.shader = null; p.color = layer.color
        }
        c.drawPath(layer.path, p)
        p.shader = null
    }

    private fun drawWave(c: Canvas, p: Paint, art: Art, y: Float, phase: Float, waveAmp: Float, color: Int, alpha: Int) {
        val amp = 4f + 38f * waveAmp
        val left = art.outline.left - 20f
        val right = art.outline.right + 20f
        val bottom = art.outline.bottom + 20f
        val path = Path().apply {
            moveTo(left, bottom)
            var x = left
            while (x <= right) {
                lineTo(x, y + amp * sin(((x - left) / (right - left) * 4 * PI + phase).toDouble()).toFloat())
                x += 14f
            }
            lineTo(right, bottom); close()
        }
        p.style = Paint.Style.FILL; p.shader = null; p.color = color; p.alpha = alpha
        c.drawPath(path, p)
        p.alpha = 255
    }

    private val FLAG = Color.parseColor("#F0483C")

    /**
     * Dotted line where the water should be by now, with a small flag marking it as a target
     * (clearer at a glance than shading, which read as an unexplained blue smudge).
     */
    private fun drawGhost(c: Canvas, p: Paint, art: Art, fill: Float, paceFrac: Float, f: Frame) {
        if (paceFrac <= 0f) return
        val yGhost = levelY(art, paceFrac.coerceAtMost(1f))
        val yWater = levelY(art, fill)
        val left = art.body.left
        val right = art.body.right
        if (yGhost < yWater && f.preview > 0f) {
            // Translucent water rising toward the target, shown only during the reminder animation.
            val yTop = yWater + (yGhost - yWater) * f.preview
            val path = Path().apply {
                moveTo(left, yWater + 10f)
                var x = left
                while (x <= right) {
                    lineTo(x, yTop + 14f * sin(((x - left) / (right - left) * 4 * PI + f.phase * 3).toDouble()).toFloat())
                    x += 14f
                }
                lineTo(right, yWater + 10f); close()
            }
            p.style = Paint.Style.FILL; p.shader = null; p.color = WATER_LIGHT; p.alpha = 235
            c.drawPath(path, p)
            p.alpha = 255
        }
        val end = left + (right - left) * f.lineProgress.coerceIn(0f, 1f)
        if (end > left) {
            p.style = Paint.Style.STROKE; p.shader = null; p.strokeWidth = 20f; p.strokeCap = Paint.Cap.ROUND; p.color = FLAG
            p.pathEffect = DashPathEffect(floatArrayOf(4f, 44f), 0f)
            c.drawLine(left, yGhost, end, yGhost, p)
            p.pathEffect = null; p.strokeCap = Paint.Cap.BUTT
            drawFlag(c, p, left + (right - left) * 0.86f, yGhost, (right - left) * 0.16f, f.lineProgress)
        }
    }

    /**
     * A small pennant straddling the target line (no separate pole, so it can't poke up into the neck or cap
     * regardless of how high the target sits) — reads as a goal marker rather than a stray dash.
     */
    private fun drawFlag(c: Canvas, p: Paint, x: Float, y: Float, size: Float, appear: Float) {
        if (appear <= 0f) return
        val w = size * appear.coerceIn(0f, 1f)
        val pennant = Path().apply {
            moveTo(x, y - size * 0.42f)
            lineTo(x - w, y)
            lineTo(x, y + size * 0.42f)
            close()
        }
        p.pathEffect = null; p.shader = null
        p.style = Paint.Style.FILL; p.color = FLAG
        c.drawPath(pennant, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = size * 0.1f; p.strokeJoin = Paint.Join.ROUND; p.color = INK
        c.drawPath(pennant, p)
        p.strokeJoin = Paint.Join.MITER
    }

    /** A tick at each litre, starting at the body's left edge, so the one bottle still tells 2 L from 3 L from 4 L. */
    private fun drawTicks(c: Canvas, p: Paint, art: Art, litres: Int) {
        if (litres < 2) return
        p.style = Paint.Style.STROKE; p.shader = null; p.strokeWidth = 24f; p.strokeCap = Paint.Cap.ROUND
        p.color = INK; p.alpha = 190
        for (k in 1 until litres) {
            val y = levelY(art, k.toFloat() / litres)
            var x = art.body.left.toInt()
            val limit = art.body.centerX().toInt()
            while (x < limit && !art.bodyRegion.contains(x, y.toInt())) x += 6
            if (x < limit) c.drawLine(x + 22f, y, x + 22f + 110f, y, p)
        }
        p.alpha = 255; p.strokeCap = Paint.Cap.BUTT
    }

    private fun drawShine(c: Canvas, p: Paint, art: Art, t: Float) {
        val sweep = (t / 0.55f).coerceIn(0f, 1f)
        if (sweep >= 1f) return
        val w = art.body.width()
        val x = art.body.left - 150f + (w + 300f) * sweep
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            x - 110f, 0f, x + 110f, 0f,
            intArrayOf(Color.TRANSPARENT, Color.argb(200, 255, 255, 255), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP,
        )
        c.drawRect(art.body.left - 20f, art.body.top - 20f, art.body.right + 20f, art.body.bottom + 20f, p)
        p.shader = null
    }

    private fun drawSweat(c: Canvas, p: Paint, art: Art, alpha: Float) {
        val x = art.body.right + 10f
        val y = art.body.top + art.body.height() * 0.18f
        val drop = Path().apply {
            moveTo(x, y); quadTo(x - 70f, y + 100f, x, y + 140f); quadTo(x + 70f, y + 100f, x, y)
            close()
        }
        p.style = Paint.Style.FILL; p.shader = null; p.color = WATER_LIGHT; p.alpha = (255 * alpha).toInt()
        c.drawPath(drop, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 16f; p.color = INK; p.alpha = (255 * alpha).toInt()
        c.drawPath(drop, p)
        p.alpha = 255
    }

    private fun drawSparkles(c: Canvas, p: Paint, t: Float, small: Boolean) {
        // (x, y in pixels on the card, start offset in 0..0.5)
        val spots = if (small) {
            listOf(Triple(78f, 120f, 0.00f), Triple(348f, 96f, 0.10f), Triple(360f, 210f, 0.20f))
        } else {
            listOf(
                Triple(52f, 96f, 0.10f), Triple(368f, 84f, 0.22f), Triple(378f, 232f, 0.30f),
                Triple(44f, 246f, 0.05f), Triple(210f, 26f, 0.36f),
            )
        }
        val radius = if (small) 15f else 22f
        for ((x, y, start) in spots) {
            val local = ((t - start) / 0.5f).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue
            val r = radius * sin(local * PI.toFloat())
            val cy = y - 12f * local
            val star = Path().apply {
                moveTo(x, cy - r); quadTo(x, cy, x + r, cy); quadTo(x, cy, x, cy + r)
                quadTo(x, cy, x - r, cy); quadTo(x, cy, x, cy - r); close()
            }
            p.style = Paint.Style.FILL; p.shader = null; p.color = Color.WHITE; c.drawPath(star, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 3.5f; p.color = INK; c.drawPath(star, p)
        }
    }

    /** Small check badge in the corner once the goal is reached. */
    private fun drawGoalBadge(c: Canvas, p: Paint) {
        val x = SIZE - 52f
        val y = 52f
        p.style = Paint.Style.FILL; p.shader = null; p.color = Color.parseColor("#4BA9FC"); c.drawCircle(x, y, 32f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 7f; p.color = INK; c.drawCircle(x, y, 32f, p)
        p.strokeWidth = 8f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.color = Color.WHITE
        c.drawPath(Path().apply { moveTo(x - 14f, y); lineTo(x - 4f, y + 10f); lineTo(x + 15f, y - 11f) }, p)
        p.strokeCap = Paint.Cap.BUTT; p.strokeJoin = Paint.Join.MITER
    }

    /** Big number with a smaller unit: "1.5 L", or "700 ml" below one litre. */
    private fun drawAmount(c: Canvas, p: Paint, totalMl: Float) {
        val (number, unit) = if (totalMl >= 1000f) {
            "%.2f".format(totalMl / 1000f).trimEnd('0').trimEnd('.') to "L"
        } else {
            totalMl.toInt().toString() to "ml"
        }
        p.style = Paint.Style.FILL; p.shader = null; p.color = INK; p.textAlign = Paint.Align.LEFT
        p.typeface = numberFont; p.textSize = 62f
        val numW = p.measureText(number)
        p.textSize = 38f
        val unitW = p.measureText(unit)
        val gap = 8f
        val x = (SIZE - (numW + gap + unitW)) / 2f
        val baseline = SIZE - 24f
        p.textSize = 62f
        c.drawText(number, x, baseline, p)
        p.textSize = 38f; p.alpha = 175
        c.drawText(unit, x + numW + gap, baseline, p)
        p.alpha = 255
    }
}
