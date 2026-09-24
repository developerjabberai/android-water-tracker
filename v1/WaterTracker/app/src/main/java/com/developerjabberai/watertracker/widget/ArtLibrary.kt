package com.developerjabberai.watertracker.widget

import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import androidx.core.graphics.PathParser
import org.json.JSONObject

/** How a layer of the artwork is painted. Only the blue body and its darker shading react to the water level. */
enum class LayerKind { BODY, SHADE, OTHER }

class ArtLayer(
    val path: Path,
    val color: Int,
    val shader: Shader?,
    val kind: LayerKind,
)

/**
 * One character, as a stack of flat shapes in paint order. Layer 0 is the navy outline silhouette (whiskers and ears
 * included), layer 1 the blue body; the rest are the face, gloss and details.
 */
class Art(
    val size: Float,
    val layers: List<ArtLayer>,
    val outline: RectF,
    val body: RectF,
    val bodyColor: Int,
    val bodyLayer: Int,
    /** Indices of layers not drawn at all (the character's own baked-in cheek blush, which we drop). */
    val hidden: Set<Int> = emptySet(),
) {
    /** Lets us find the body's left edge at any height, for the litre tick marks. */
    val bodyRegion: Region = Region().apply {
        setPath(layers[bodyLayer].path, Region(0, 0, size.toInt(), size.toInt()))
    }

    /**
     * The full ink silhouette (layer 0: outline, ears, whiskers) as a region — this is the character's
     * true visible outer edge. It's a ring shape (hollow in the middle, since the body/face show through),
     * so on its own it can't tell "inside the character" from "inside the empty cavity"; callers that want
     * that should test bodyRegion.contains(...) || outlineRegion.contains(...).
     */
    val outlineRegion: Region = Region().apply {
        setPath(layers[0].path, Region(0, 0, size.toInt(), size.toInt()))
    }
}

/** Loads the expression JSON files from assets (made from `v1/design/expressions` by `svg_to_json.py`). */
object ArtLibrary {
    const val COUNT = 4
    private val cache = arrayOfNulls<Art>(COUNT)

    fun get(context: Context, index: Int): Art = synchronized(cache) {
        val i = index.coerceIn(0, COUNT - 1)
        cache[i] ?: load(context, i).also { cache[i] = it }
    }

    private fun load(context: Context, index: Int): Art {
        val text = context.assets.open("art/expression-${index + 1}.json").bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        val raw = json.getJSONArray("layers")
        val inkColor = Color.parseColor(raw.getJSONObject(0).getString("fill"))
        val bodyColor = Color.parseColor(raw.getJSONObject(1).getString("fill"))
        val bodyHue = FloatArray(3).also { Color.colorToHSV(bodyColor, it) }[0]

        val layers = ArrayList<ArtLayer>(raw.length())
        var outline = RectF()
        var body = RectF()
        val bounds = ArrayList<RectF>(raw.length())
        for (i in 0 until raw.length()) {
            val o = raw.getJSONObject(i)
            val path = PathParser.createPathFromPathData(o.getString("d"))
            val alpha = o.optDouble("alpha", 1.0).toFloat()
            val grad = o.optJSONObject("grad")
            var color = 0
            var shader: Shader? = null
            var kind = LayerKind.OTHER
            if (grad != null) {
                val stops = grad.getJSONArray("stops")
                val colors = IntArray(stops.length())
                val positions = FloatArray(stops.length())
                for (s in 0 until stops.length()) {
                    val st = stops.getJSONArray(s)
                    positions[s] = st.getDouble(0).toFloat()
                    val c = Color.parseColor(st.getString(1))
                    colors[s] = Color.argb((st.getDouble(2) * alpha * 255).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
                }
                shader = LinearGradient(
                    grad.getDouble("x1").toFloat(), grad.getDouble("y1").toFloat(),
                    grad.getDouble("x2").toFloat(), grad.getDouble("y2").toFloat(),
                    colors, positions, Shader.TileMode.CLAMP,
                )
            } else {
                val c = Color.parseColor(o.getString("fill"))
                color = Color.argb((alpha * 255).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
                val hsv = FloatArray(3).also { Color.colorToHSV(c, it) }
                kind = when {
                    c == bodyColor && alpha >= 0.99f -> LayerKind.BODY
                    c != inkColor && alpha >= 0.99f && hsv[1] > 0.5f && hsv[2] > 0.5f && kotlin.math.abs(hsv[0] - bodyHue) < 20f -> LayerKind.SHADE
                    else -> LayerKind.OTHER
                }
            }
            val b = RectF().also { path.computeBounds(it, true) }
            if (i == 0) outline = b
            if (i == 1) body = b
            layers += ArtLayer(path, color, shader, kind)
            bounds += b
        }
        val size = json.getDouble("size").toFloat()
        val hidden = findCheekBlush(layers, bounds, size)
        return Art(size, layers, outline, body, bodyColor, bodyLayer = 1, hidden = hidden)
    }

    /**
     * Every character's source art bakes in a pink "blush" circle on each cheek, which we don't want on the
     * widget. Rather than hand-picking layer indices per character (fragile if the art is regenerated), find
     * them by shape: a pair of same-colour, roughly-square (circular) layers, mirrored left/right of centre,
     * sitting in the lower half of the face.
     */
    private fun findCheekBlush(layers: List<ArtLayer>, bounds: List<RectF>, size: Float): Set<Int> {
        val centerX = size / 2f
        val candidates = layers.indices.filter { i ->
            val b = bounds[i]
            val w = b.width(); val h = b.height()
            // 0.58, not 0.5: a plain lower-half cut also catches the eyes (also ink, oval and
            // symmetric), which sit just below the vertical midpoint and would false-match each other.
            // 0.4, not a tighter "circular" cut: Android's Path.computeBounds() measures some of these
            // curve-heavy cheek shapes noticeably wider than tall (verified up to 0.36 on real art), so a
            // stricter cutoff silently drops a real cheek and leaves the other one undetected.
            layers[i].kind == LayerKind.OTHER && w > 0 && h > 0 &&
                kotlin.math.abs(w - h) / maxOf(w, h) < 0.4f &&
                b.centerY() > size * 0.58f // clearly in the cheek/mouth area, not the eyes or a cap/bow
        }
        for (i in candidates) {
            for (j in candidates) {
                if (j <= i) continue
                val a = bounds[i]; val b = bounds[j]
                val sameSize = kotlin.math.abs(a.width() - b.width()) / maxOf(a.width(), b.width()) < 0.15f
                val sameHeight = kotlin.math.abs(a.centerY() - b.centerY()) < a.height() * 0.3f
                val mirrored = kotlin.math.abs((a.centerX() - centerX) + (b.centerX() - centerX)) < a.width() * 0.3f
                val onOppositeSides = (a.centerX() - centerX) * (b.centerX() - centerX) < 0f
                val sameColor = layers[i].color == layers[j].color
                if (sameSize && sameHeight && mirrored && onOppositeSides && sameColor) return setOf(i, j)
            }
        }
        return emptySet()
    }
}
