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
) {
    /** Lets us find the body's left edge at any height, for the litre tick marks. */
    val bodyRegion: Region = Region().apply {
        setPath(layers[bodyLayer].path, Region(0, 0, size.toInt(), size.toInt()))
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
        }
        return Art(json.getDouble("size").toFloat(), layers, outline, body, bodyColor, bodyLayer = 1)
    }
}
