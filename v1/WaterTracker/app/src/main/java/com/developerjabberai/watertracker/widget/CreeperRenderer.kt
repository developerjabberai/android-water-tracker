package com.developerjabberai.watertracker.widget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * The creeper: a vine that winds up the bottle, ten growth levels from wilted to full bloom. The levels are a
 * table of looks that blend into each other, so growing from one level to the next animates smoothly.
 * Sizes are relative to the bottle's width, so it fits every character.
 */
object CreeperRenderer {
    private class Look(
        val height: Float,   // how far up the bottle it has climbed, 0..1
        val turns: Float,    // how many times it winds side to side
        val leaves: Float,
        val buds: Float,
        val flowers: Float,
        val stem: Int,
        val leaf: Int,
    )

    private fun look(h: Float, turns: Float, leaves: Int, buds: Int, flowers: Int, stem: String, leaf: String) =
        Look(h, turns, leaves.toFloat(), buds.toFloat(), flowers.toFloat(), Color.parseColor(stem), Color.parseColor(leaf))

    private val LOOKS = listOf(
        look(0.10f, 0.3f, 1, 0, 0, "#8B6B4A", "#A98A63"),  // 1 wilted
        look(0.22f, 0.6f, 2, 0, 0, "#B5A24A", "#D3C265"),  // 2 struggling
        look(0.34f, 0.9f, 4, 0, 0, "#7DBA5A", "#9AD174"),  // 3 recovering
        look(0.46f, 1.1f, 6, 0, 0, "#4FB255", "#6CC46F"),  // 4 sprout
        look(0.58f, 1.3f, 9, 0, 0, "#3FA34D", "#5CC46B"),  // 5 steady (default)
        look(0.70f, 1.5f, 12, 0, 0, "#3FA34D", "#5CC46B"), // 6 climbing
        look(0.82f, 1.7f, 16, 0, 0, "#36A04A", "#4FC060"), // 7 lush
        look(0.90f, 1.9f, 17, 4, 0, "#36A04A", "#4FC060"), // 8 budding
        look(0.96f, 2.0f, 18, 2, 4, "#36A04A", "#4FC060"), // 9 blooming
        look(1.00f, 2.1f, 20, 0, 8, "#2F9A45", "#4FC060"), // 10 full bloom
    )

    private val INK = Color.parseColor("#12324A")
    private val BUD = Color.parseColor("#FF8FB0")
    private val PETAL = Color.parseColor("#FF6F91")
    private val HEART = Color.parseColor("#FFF3B0")

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun lookFor(level: Float): Look {
        val l = level.coerceIn(1f, 10f) - 1f
        val i = l.toInt().coerceAtMost(8)
        val t = l - i
        val a = LOOKS[i]
        val b = LOOKS[i + 1]
        return Look(
            lerp(a.height, b.height, t), lerp(a.turns, b.turns, t), lerp(a.leaves, b.leaves, t),
            lerp(a.buds, b.buds, t), lerp(a.flowers, b.flowers, t),
            ColorUtils.blendARGB(a.stem, b.stem, t), ColorUtils.blendARGB(a.leaf, b.leaf, t),
        )
    }

    /**
     * Draws the vine in the character's own coordinates. Call once before the character (the whole vine, so it
     * grows up behind the bottle) and once after it with [front] = true, which redraws just the side edges over the
     * bottle so the vine looks wound around it without ever covering the face.
     */
    fun draw(c: Canvas, p: Paint, art: Art, level: Float, front: Boolean) {
        if (level < 1f) return
        val look = lookFor(level)
        val w = art.body.width()
        val cx = art.body.centerX()
        val bottom = art.body.bottom - w * 0.02f
        val span = art.body.height() * 1.02f
        val amp = w * 0.63f
        val steps = 64
        val pts = Array(steps + 1) { i ->
            val t = i / steps.toFloat()
            PointF(cx - amp * cos(2 * PI * look.turns * t).toFloat(), bottom - span * look.height * t)
        }

        c.save()
        if (front) {
            val band = w * 0.24f
            val edges = Path().apply {
                addRect(art.body.left - w * 0.6f, 0f, art.body.left + band, art.size, Path.Direction.CW)
                addRect(art.body.right - band, 0f, art.body.right + w * 0.6f, art.size, Path.Direction.CW)
            }
            c.clipPath(edges)
        }

        val stem = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1..steps) lineTo(pts[i].x, pts[i].y)
        }
        p.shader = null; p.pathEffect = null
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = w * 0.058f; p.color = INK
        c.drawPath(stem, p)
        p.strokeWidth = w * 0.032f; p.color = look.stem
        c.drawPath(stem, p)

        fun at(t: Float) = pts[(t * steps).toInt().coerceIn(0, steps)]

        // Leaves alternate sides along the vine; the last one grows in as the count rises.
        val leafCount = ceil(look.leaves).toInt()
        for (k in 0 until leafCount) {
            val grow = (look.leaves - k).coerceIn(0f, 1f)
            val pt = at((k + 1) / (leafCount + 1f) * 0.98f)
            val side = if (k % 2 == 0) 1f else -1f
            c.save()
            c.translate(pt.x + side * w * 0.05f, pt.y - w * 0.012f)
            c.rotate(if (side > 0) -35f else 215f)
            val oval = RectF(-w * 0.088f * grow, -w * 0.045f * grow, w * 0.088f * grow, w * 0.045f * grow)
            p.style = Paint.Style.FILL; p.color = look.leaf
            c.drawOval(oval, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.011f; p.color = INK
            c.drawOval(oval, p)
            c.restore()
        }

        val budCount = ceil(look.buds).toInt()
        for (k in 0 until budCount) {
            val grow = (look.buds - k).coerceIn(0f, 1f)
            val pt = at(0.45f + 0.5f * k / budCount.coerceAtLeast(1))
            val x = pt.x + (if (k % 2 == 1) 1 else -1) * w * 0.07f
            p.style = Paint.Style.FILL; p.color = BUD
            c.drawCircle(x, pt.y, w * 0.04f * grow, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.010f; p.color = INK
            c.drawCircle(x, pt.y, w * 0.04f * grow, p)
        }

        val flowerCount = ceil(look.flowers).toInt()
        for (k in 0 until flowerCount) {
            val grow = (look.flowers - k).coerceIn(0f, 1f)
            val pt = at(0.35f + 0.62f * k / flowerCount.coerceAtLeast(1))
            val fx = pt.x + (if (k % 2 == 1) 1 else -1) * w * 0.085f
            val fy = pt.y - w * 0.02f
            val petal = w * 0.034f * grow
            for (a in 0 until 5) {
                val ang = Math.toRadians((a * 72 - 90).toDouble())
                val px = fx + w * 0.045f * grow * cos(ang).toFloat()
                val py = fy + w * 0.045f * grow * sin(ang).toFloat()
                p.style = Paint.Style.FILL; p.color = PETAL; c.drawCircle(px, py, petal, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.008f; p.color = INK; c.drawCircle(px, py, petal, p)
            }
            p.style = Paint.Style.FILL; p.color = HEART; c.drawCircle(fx, fy, w * 0.03f * grow, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.008f; p.color = INK; c.drawCircle(fx, fy, w * 0.03f * grow, p)
        }
        p.style = Paint.Style.FILL; p.strokeCap = Paint.Cap.BUTT; p.strokeJoin = Paint.Join.MITER
        c.restore()
    }
}
