package com.ivor.ivormusic.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * The expressive shapes in this file are the real Material 3 Expressive shape
 * definitions, pulled from the official catalog: [MaterialShapes] as published
 * in androidx.compose.material3 (the M3E shape sheet - Cookie, Sunny,
 * SoftBurst, Puffy and friends), each a [RoundedPolygon] converted to an
 * android [Path] for bitmap masking. No hand-rolled approximations: the
 * geometry here is exactly what the M3E catalog ships.
 *
 * M3 Expressive puts its most adventurous shapes where they cost nothing:
 * "photography cropping, personalized avatar masking, and other non-interactive
 * elements". That is exactly the right split for a widget, because the
 * interactive half cannot follow. A Glance click target gets a ripple drawn by
 * the framework as a rectangle over the view's bounds, and there is no ripple
 * override in Glance 1.1 - so a scalloped *button* would flash a hard square on
 * every tap, which is worse than a plain one. Controls therefore use the
 * Material components that ship their own matching ripple, and the album art
 * carries the shape language instead.
 *
 * Shapes are generated as [Path]s rather than authored as vector XML because
 * they are used to mask bitmaps, not only to fill backgrounds, and a path that
 * defines both keeps the cover and its placeholder identical.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal object WidgetShapes {

    /** Points per revolution. Plenty for a cover that is at most ~200dp wide. */
    private const val SAMPLES = 360

    /**
     * Render an official M3E [RoundedPolygon] as an android [Path] at [size]
     * pixels. The catalog shapes are normalized to the (0,0)-(1,1) square, so
     * this is a straight scale.
     */
    private fun official(polygon: RoundedPolygon, size: Float): Path {
        val path = polygon.toPath(Path())
        path.transform(Matrix().apply { setScale(size, size) })
        return path
    }

    /**
     * The official M3E Cookie (12 soft scallops) - the house shape for album
     * art. It reads as deliberate at a glance and still shows the whole cover,
     * which a heavy crop would not.
     */
    fun cookie(size: Float): Path = official(MaterialShapes.Cookie12Sided, size)

    /**
     * The official M3E 4-sided cookie - the squarish expressive shape: four
     * soft lobes reading as a rounded square. For the vinyl sleeve, where a
     * rounder cut would uncover the sleeve's shadow strip in the scallops.
     */
    fun cookie4Sided(size: Float): Path = official(MaterialShapes.Cookie4Sided, size)

    /**
     * The fraction of its box that the 4-sided cookie actually fills
     * vertically. The catalog authors the lobes inside the normalized square,
     * so the sleeve box is scaled by the inverse to make the shape's visual
     * height match the record's diameter exactly (locked 1:1).
     */
    fun cookie4SidedFillFraction(): Float {
        val b = MaterialShapes.Cookie4Sided.calculateBounds(approximate = false)
        return (b[3] - b[1]).coerceAtLeast(0.01f)
    }

    /** Four fat lobes. Used small, where a subtle scallop would read as a circle. */
    fun clover(size: Float, amplitude: Float = 0.13f): Path =
        polar(size) { angle -> (1f + amplitude * cos(4.0 * angle)).toFloat() }

    /**
     * The official M3E Sunny - the catalog's 8-point sun with rounded points.
     * The expressive hero shape: play buttons, anything that should pop.
     */
    fun sunny(size: Float): Path = official(MaterialShapes.Sunny, size)

    /**
     * The official M3E Flower - rounded petals, symmetric by construction.
     * A clean, bold shape for the play button at widget size.
     */
    fun flower(size: Float): Path = official(MaterialShapes.Flower, size)

    /**
     * The official M3E Bun - a soft, rounded shape, symmetric by construction.
     * A clean shape for the next button that cannot read malskewed.
     */
    fun bun(size: Float): Path = official(MaterialShapes.Bun, size)

    /**
     * The official M3E SoftBurst - a 10-point softly rounded starburst. For
     * the record's center label.
     */
    fun softBurst(size: Float): Path = official(MaterialShapes.SoftBurst, size)

    /**
     * The official M3E Puffy - the catalog's inflated, bulging-sided shape.
     * The catalog authors it vertically compressed (x0.742) by design; the
     * widget's next button should read evenly scaled, so the compression is
     * undone here and the shape re-fit to the square before use.
     *
     * The correction is measured from the shape's TRUE visual bounds
     * ([RoundedPolygon.calculateBounds] with approximate=false, which solves
     * the actual curves). Measuring the android Path's bounds instead includes
     * the Bezier control points, whose overshoot is asymmetric - that is what
     * left the button visibly taller than wide.
     */
    fun puffy(size: Float): Path {
        val polygon = MaterialShapes.Puffy
        // True visual bounds of the catalog shape (not control points).
        val b = polygon.calculateBounds(approximate = false)
        val w = b[2] - b[0]
        val h = b[3] - b[1]
        val cx = (b[0] + b[2]) / 2f
        val cy = (b[1] + b[3]) / 2f
        val path = polygon.toPath(Path())
        // 1:1 - stretch the short axis out to the long one, about the center,
        // undoing the catalog's vertical compression in visual space.
        val longSide = max(w, h)
        path.transform(
            Matrix().apply {
                if (h < w) setScale(1f, w / h, cx, cy) else setScale(h / w, 1f, cx, cy)
            }
        )
        // Re-fit the un-squished shape to the requested square. The corrected
        // true bounds are longSide x longSide about (cx, cy).
        val s = size / longSide
        path.transform(
            Matrix().apply {
                setScale(s, s)
                postTranslate(-(cx - longSide / 2f) * s, -(cy - longSide / 2f) * s)
            }
        )
        return path
    }

    /** A superellipse - squarer than a rounded rect, with no corner to catch. */
    fun squircle(size: Float, exponent: Float = 4f): Path {
        val radius = size / 2f
        val path = Path()
        for (i in 0 until SAMPLES) {
            val angle = 2.0 * Math.PI * i / SAMPLES
            val cosine = cos(angle)
            val sine = sin(angle)
            // Signed power keeps the sign of the axis while rounding the corner.
            val x = radius * signedPow(cosine, 2f / exponent)
            val y = radius * signedPow(sine, 2f / exponent)
            val px = radius + x.toFloat()
            val py = radius + y.toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    fun circle(size: Float): Path = Path().apply {
        addOval(RectF(0f, 0f, size, size), Path.Direction.CW)
    }

    fun roundedRect(width: Float, height: Float, radius: Float): Path = Path().apply {
        addRoundRect(RectF(0f, 0f, width, height), radius, radius, Path.Direction.CW)
    }

    private fun polar(size: Float, radiusAt: (Double) -> Float): Path {
        val center = size / 2f
        // Normalise so the widest point of the lobe touches the edge rather
        // than overflowing it - otherwise the scallops get clipped flat.
        var peak = 0f
        for (i in 0 until SAMPLES) {
            peak = max(peak, radiusAt(2.0 * Math.PI * i / SAMPLES))
        }
        val scale = center / peak
        val path = Path()
        for (i in 0 until SAMPLES) {
            val angle = 2.0 * Math.PI * i / SAMPLES
            val r = radiusAt(angle) * scale
            val px = center + (r * cos(angle)).toFloat()
            val py = center + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    private fun signedPow(value: Double, exponent: Float): Double =
        if (value < 0) -((-value).pow(exponent.toDouble())) else value.pow(exponent.toDouble())
}

/**
 * The shape an album cover is cut into. Named rather than passed as a path so a
 * widget declares its identity and the drawing stays in one place.
 */
internal enum class ArtworkShape {
    COOKIE,
    COOKIE4SIDED,
    CLOVER,
    SQUIRCLE,
    CIRCLE,
    ROUNDED;

    fun path(size: Float): Path = when (this) {
        COOKIE -> WidgetShapes.cookie(size)
        COOKIE4SIDED -> WidgetShapes.cookie4Sided(size)
        CLOVER -> WidgetShapes.clover(size)
        SQUIRCLE -> WidgetShapes.squircle(size)
        CIRCLE -> WidgetShapes.circle(size)
        ROUNDED -> WidgetShapes.roundedRect(size, size, size * 0.16f)
    }
}

/**
 * Cut [src] to [shape], centre-cropped square.
 *
 * The mask is baked into the bitmap rather than clipped at draw time because
 * Glance can only round an image through the launcher's outline provider, which
 * does rectangles and nothing else - and does not exist at all below API 31.
 */
internal fun maskedArtwork(src: Bitmap, shape: ArtworkShape, sizePx: Int): Bitmap {
    val side = sizePx.coerceAtLeast(1)
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)

    // Centre-crop the source into the square before masking, so a non-square
    // cover is not squashed into the shape.
    val scale = side.toFloat() / minOf(src.width, src.height)
    val matrix = android.graphics.Matrix().apply {
        setScale(scale, scale)
        postTranslate(
            (side - src.width * scale) / 2f,
            (side - src.height * scale) / 2f,
        )
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(matrix)
        }
    }
    canvas.drawPath(shape.path(side.toFloat()), paint)
    return out
}

/** The same silhouette in one flat colour, for the no-artwork placeholder. */
internal fun shapeTile(shape: ArtworkShape, sizePx: Int, color: Int): Bitmap {
    val side = sizePx.coerceAtLeast(1)
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    canvas.drawPath(shape.path(side.toFloat()), paint)
    return out
}

/** Fill an arbitrary [Path] (authored at [sizePx]) in one flat colour. For expressive button shapes. */
internal fun filledPath(path: Path, sizePx: Int, color: Int): Bitmap {
    val side = sizePx.coerceAtLeast(1)
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    canvas.drawPath(path, paint)
    return out
}
