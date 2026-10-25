package com.ivor.ivormusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.ui.theme.kodaColorScheme
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Vinyl: a record half out of its sleeve, turning while the music plays, with
 * a tonearm that comes down onto it and lifts off again. The sleeve is the
 * cover; play and next sit beside it. No card: it stands on the wallpaper.
 *
 * This is a CLASSIC RemoteViews widget, not a Glance widget, and that is the
 * whole point of this round. Glance composes to a fixed dp size chosen from
 * the launcher's reported size, and One UI's content-size % scales that
 * report, so every Glance-era vinyl build shrank at 110% — buckets (Build
 * #132) and MIN/MAX (Build #133) only changed which reported number was
 * trusted. A classic shell has no reported size in it at all:
 *
 * - The root is match_parent, so the host measures it at the true cell box.
 * - Every child is 0dp plus a layout_weight, so every split is a fraction
 *   of the host box, computed by the launcher at layout time.
 * - The artwork is baked bitmaps drawn with fitCenter / adjustViewBounds,
 *   so the launcher scales the design to the box instead of the widget
 *   doing dp math against a number Samsung can scale.
 *
 * The % slider therefore cannot shrink this widget: there is no size in
 * the layout for it to scale. The one place a reported size is still read
 * is the provider's choice between the narrow and wide layout, by aspect
 * ratio only — and a uniform % scale cannot change an aspect ratio, so
 * even that choice is %-immune.
 *
 * LOCKED RATIO: one design, the 4x2 side-column layout. It does not
 * reflow (the Build #131 buttons-below variant is gone). At its native
 * ratio the design fills the box exactly: the row is stage 1705 / gap 40 /
 * buttons 460 weights, and the stage bitmap's own aspect is the stage
 * geometry, so the fit rect IS the box. Resized to 4x1 or 2x2 it gets
 * very small, exactly as specified — that is the locked ratio working,
 * not a bug. On cells wider than the design ratio the union sits right
 * of centre (the wide variant pins the art to the cell's right edge and
 * left-aligns the buttons, keeping the internal gap at the design gap
 * instead of letting it grow with the cell); nothing distorts.
 *
 * SPIN IS KEPT: the spinning label is a ProgressBar whose bounds are the
 * record carve — weight fractions of the art rect, and the art rect is
 * the adjustViewBounds box of the stage bitmap — so the rotate drawable
 * (pivot 50/50 of its bounds) turns about the record centre at every
 * size and ratio, exactly as the Glance version did. Paused, the parked
 * label bitmap sits in the same carve.
 *
 * GEOMETRY (fractions of the stage rect, from Cookie4Sided true bounds,
 * fill ~0.94): stage width = 1 + 0.75 x 0.94 = 1.705 heights; design
 * width = 1.705 + 0.04 + 0.46 = 2.205 heights. Inside the stage rect:
 * sleeve 0.586 wide; record carve left 0.311 / width 0.551 / right 0.138;
 * vertical top 0.03 / height 0.94 / bottom 0.03. The XML weights encode
 * the 0.94 fill; the bitmap is rendered from the runtime fill, and the
 * two agree to well under a pixel at widget sizes.
 */
class VinylWidgetReceiver : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        scope.launch {
            try {
                VinylClassic.updateAll(context.applicationContext)
            } catch (e: Exception) {
                KLog.w("VinylWidget", "onUpdate failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        // A resize can flip the narrow/wide variant; re-render for it.
        val pending = goAsync()
        scope.launch {
            try {
                VinylClassic.updateAll(context.applicationContext)
            } catch (e: Exception) {
                KLog.w("VinylWidget", "onAppWidgetOptionsChanged failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}

internal object VinylClassic {

    /** Design width in stage-height units; the narrow/wide split point. */
    private const val DESIGN_W = 2.205f

    /** Translucent disc, as before: a fancy coloured vinyl, not a solid. */
    private const val VINYL_DISC_ALPHA = 0.64f

    /** Disc darkening, as before: the selected colour, darker, same hue. */
    private const val DISC_DARKEN = 0.75f

    /** Glyph ink: near-black or white, whichever contrasts best (WCAG). */
    private const val INK_DARK = 0xFF1A1A1Au
    private const val INK_LIGHT = 0xFFFFFFFFu

    /** Stage bitmap height in px; width follows the stage geometry. */
    private const val STAGE_PX = 1000

    /** Button bitmap side in px; the next shape is baked at 0.95 of it. */
    private const val BUTTON_PX = 512

    /** Label/spindle mask bitmap side in px (sources are 512). */
    private const val MASK_PX = 512

    suspend fun updateAll(context: Context) {
        PlayerWidgetHost.ensureSeeded(context)
        val ui = PlayerWidgetHost.state.value
        val ids = runCatching {
            AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, VinylWidgetReceiver::class.java),
            )
        }.getOrNull() ?: IntArray(0)
        if (ids.isEmpty()) return
        val rendered = render(context, ui)
        val manager = AppWidgetManager.getInstance(context)
        for (id in ids) {
            try {
                manager.updateAppWidget(id, rendered.forId(context, isWide(context, id)))
            } catch (e: Exception) {
                KLog.w("VinylWidget", "Update failed for $id: ${e.message}")
            }
        }
    }

    /**
     * One render for every instance: the bitmaps do not depend on the
     * cell, only the layout variant does, and that is picked per id.
     * The variant choice reads the reported aspect only — a uniform %
     * scale leaves it unchanged.
     */
    private fun render(context: Context, ui: PlayerWidgetUi): Rendered {
        val snap = ui.snapshot
        val prefs = ThemePreferences(context)
        val dark = KodaWidgetTheme.isDark(context)
        val scheme = kodaColorScheme(
            context,
            dark,
            prefs.colorPalette.value,
            prefs.amoledTheme.value,
            prefs.paletteStyle.value,
        )
        val surface = scheme.surface.toArgb()
        val onSurface = scheme.onSurface.toArgb()
        val recordColor = if (dark) scheme.background.toArgb() else onSurface
        val light = if (dark) onSurface else surface
        val buttonColor = KodaWidgetTheme.vinylButtonArgb(context, snap.artworkColor)
        val labelColor = snap.artworkLabelColor ?: scheme.primaryContainer.toArgb()
        val stage = stageBitmap(
            context = context,
            artwork = ui.artwork,
            artworkColor = snap.artworkColor,
            recordColor = recordColor,
            light = light,
            accent = snap.artworkColor ?: scheme.primary.toArgb(),
            secondaryContainer = scheme.secondaryContainer.toArgb(),
            onSecondaryContainer = scheme.onSecondaryContainer.toArgb(),
            isPlaying = snap.isPlaying,
        )
        return Rendered(
            stage = stage,
            play = buttonBitmap(context, play = true, isPlaying = snap.isPlaying, color = buttonColor),
            next = buttonBitmap(context, play = false, isPlaying = false, color = buttonColor),
            label = maskBitmap(context, R.drawable.vinyl_label_marks, labelColor),
            spindle = maskBitmap(context, R.drawable.vinyl_spindle, recordColor),
            labelColor = labelColor,
            spinning = snap.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )
    }

    private class Rendered(
        val stage: Bitmap,
        val play: Bitmap,
        val next: Bitmap,
        val label: Bitmap,
        val spindle: Bitmap,
        val labelColor: Int,
        val spinning: Boolean,
    ) {
        fun forId(context: Context, wide: Boolean): RemoteViews {
            val views = RemoteViews(
                context.packageName,
                if (wide) R.layout.widget_vinyl_wide else R.layout.widget_vinyl_narrow,
            )
            views.setImageViewBitmap(R.id.vinyl_stage_art, stage)
            views.setImageViewBitmap(R.id.vinyl_btn_play, play)
            views.setImageViewBitmap(R.id.vinyl_btn_next, next)
            views.setImageViewBitmap(R.id.vinyl_label_parked, label)
            views.setImageViewBitmap(R.id.vinyl_spindle, spindle)
            views.setViewVisibility(
                R.id.vinyl_label_spin,
                if (spinning) View.VISIBLE else View.GONE,
            )
            views.setViewVisibility(
                R.id.vinyl_label_parked,
                if (spinning) View.GONE else View.VISIBLE,
            )
            if (spinning) {
                // Tint the turning print the sticker colour. setColorStateList
                // on RemoteViews needs API 31, which the spin itself needs.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setColorStateList(
                        R.id.vinyl_label_spin,
                        "setIndeterminateTintList",
                        ColorStateList.valueOf(labelColor),
                    )
                }
            }
            views.setOnClickPendingIntent(R.id.vinyl_tap_sleeve, openAppIntent(context))
            views.setOnClickPendingIntent(R.id.vinyl_tap_record, toggleIntent(context))
            views.setOnClickPendingIntent(R.id.vinyl_btn_play, toggleIntent(context))
            views.setOnClickPendingIntent(R.id.vinyl_btn_next, nextIntent(context))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setContentDescription(R.id.vinyl_btn_play, "Play or pause")
                views.setContentDescription(R.id.vinyl_btn_next, "Next track")
                views.setContentDescription(R.id.vinyl_tap_sleeve, "Open Koda")
            }
            return views
        }
    }

    /**
     * The stage: sleeve + disc + grooves + arm, composited into one
     * bitmap whose aspect IS the stage geometry, so adjustViewBounds
     * gives the launcher the true art rect and everything aligned to
     * it (label carve, taps) tracks the artwork at every size.
     */
    private fun stageBitmap(
        context: Context,
        artwork: Bitmap?,
        artworkColor: Int?,
        recordColor: Int,
        light: Int,
        accent: Int,
        secondaryContainer: Int,
        onSecondaryContainer: Int,
        isPlaying: Boolean,
    ): Bitmap {
        val fill = WidgetShapes.cookie4SidedFillFraction()
        // Stage width in height units: sleeve box (1.0) + half-record
        // protrusion (fill / 2) + arm reach (record / 4).
        val stageW = 1f + fill / 2f + fill / 4f
        val width = (STAGE_PX * stageW).roundToInt()
        val height = STAGE_PX
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val record = fill * height
        val armReach = record / 4f
        val recordLeft = width - armReach - record
        val recordTop = (height - record) / 2f
        val recordRect = RectF(recordLeft, recordTop, recordLeft + record, recordTop + record)
        val armRect = RectF(recordLeft, recordTop, width.toFloat(), recordTop + record)
        val sleeveRect = RectF(0f, 0f, height.toFloat(), height.toFloat())

        // Record first, sleeve over it in the overlap, arm on top — the
        // same stacking the Glance version drew.
        val discBase = if (artworkColor != null) {
            darken(artworkColor, DISC_DARKEN)
        } else {
            recordColor
        }
        val discColor = withAlpha(discBase, VINYL_DISC_ALPHA)
        drawMask(canvas, context, R.drawable.vinyl_disc, discColor, recordRect)
        drawMask(canvas, context, R.drawable.vinyl_grooves, light, recordRect)
        if (artwork != null) {
            val sleeve = maskedArtwork(artwork, ArtworkShape.COOKIE4SIDED, height)
            canvas.drawBitmap(sleeve, null, sleeveRect, null)
        } else {
            val tile = shapeTile(ArtworkShape.COOKIE4SIDED, height, secondaryContainer)
            canvas.drawBitmap(tile, null, sleeveRect, null)
            val markSize = (height / 2.6f).roundToInt()
            val mark = maskBitmap(context, R.drawable.ic_playback_notification, onSecondaryContainer)
            val m = (height - markSize) / 2f
            canvas.drawBitmap(
                mark,
                null,
                RectF(m, m, m + markSize, m + markSize),
                null,
            )
        }
        val armPose = if (isPlaying) "on" else "off"
        val armRes = { layer: String ->
            context.resources.getIdentifier(
                "vinyl_arm_${armPose}_$layer",
                "drawable",
                context.packageName,
            )
        }
        drawMask(canvas, context, armRes("shadow"), recordColor, armRect)
        drawMask(canvas, context, armRes("metal"), light, armRect)
        drawMask(canvas, context, armRes("accent"), accent, armRect)
        return out
    }

    /**
     * A transport button baked to one bitmap: expressive shape in the
     * button colour, glyph inked by WCAG contrast against that colour.
     * Play is Sunny; next is Flower at 0.95, centred, as before.
     */
    private fun buttonBitmap(
        context: Context,
        play: Boolean,
        isPlaying: Boolean,
        color: Int,
    ): Bitmap {
        val shapeSize = if (play) BUTTON_PX else (BUTTON_PX * 0.95f).roundToInt()
        val path = if (play) {
            WidgetShapes.sunny(shapeSize.toFloat())
        } else {
            WidgetShapes.flower(shapeSize.toFloat())
        }
        val out = Bitmap.createBitmap(BUTTON_PX, BUTTON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val pad = (BUTTON_PX - shapeSize) / 2f
        canvas.save()
        canvas.translate(pad, pad)
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            },
        )
        canvas.restore()
        val glyphRes = when {
            play && isPlaying -> R.drawable.ic_media_pause
            play -> R.drawable.ic_media_play
            else -> R.drawable.ic_media_next
        }
        val ink = glyphInk(color)
        val glyphSize = (shapeSize * 0.42f).roundToInt()
        val glyph = maskBitmap(context, glyphRes, ink)
        val g = (BUTTON_PX - glyphSize) / 2f
        canvas.drawBitmap(
            glyph,
            null,
            RectF(g, g, g + glyphSize, g + glyphSize),
            null,
        )
        return out
    }

    /** A mask drawable (white shape, transparent elsewhere) tinted to [tint]. */
    private fun maskBitmap(
        context: Context,
        @DrawableRes res: Int,
        tint: Int,
    ): Bitmap {
        val drawable = ContextCompat.getDrawable(context, res)?.mutate()
            ?: return Bitmap.createBitmap(MASK_PX, MASK_PX, Bitmap.Config.ARGB_8888)
        DrawableCompat.setTint(drawable, tint)
        val out = Bitmap.createBitmap(MASK_PX, MASK_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        drawable.setBounds(0, 0, MASK_PX, MASK_PX)
        drawable.draw(canvas)
        return out
    }

    /** Draw a tinted mask into [rect] (stretched, like FillBounds). */
    private fun drawMask(
        canvas: Canvas,
        context: Context,
        @DrawableRes res: Int,
        tint: Int,
        rect: RectF,
    ) {
        if (res == 0) return
        val drawable = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        DrawableCompat.setTint(drawable, tint)
        drawable.setBounds(
            rect.left.roundToInt(),
            rect.top.roundToInt(),
            rect.right.roundToInt(),
            rect.bottom.roundToInt(),
        )
        drawable.draw(canvas)
    }

    /** Near-black or white, whichever has the higher WCAG contrast ratio. */
    private fun glyphInk(buttonColor: Int): Int {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        val lum = 0.2126 * channel(Color.red(buttonColor)) +
            0.7152 * channel(Color.green(buttonColor)) +
            0.0722 * channel(Color.blue(buttonColor))
        val contrastWhite = 1.05 / (lum + 0.05)
        val contrastDark = (lum + 0.05) / 0.06
        return if (contrastWhite >= contrastDark) INK_LIGHT.toInt() else INK_DARK.toInt()
    }

    /** HSV value scaled, hue and saturation untouched. */
    private fun darken(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
        return Color.HSVToColor(Color.alpha(color), hsv)
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(context.packageName)
        return PendingIntent.getActivity(
            context,
            "vinyl_open".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun broadcastIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, WidgetButtonReceiver::class.java).apply {
            putExtra(WidgetActionActivity.EXTRA_ACTION, action)
        }
        return PendingIntent.getBroadcast(
            context,
            "vinyl_$action".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun toggleIntent(context: Context): PendingIntent =
        broadcastIntent(context, WidgetActionActivity.ACTION_TOGGLE)

    private fun nextIntent(context: Context): PendingIntent =
        broadcastIntent(context, WidgetActionActivity.ACTION_NEXT)

    /**
     * Wide exactly when the reported cell aspect exceeds the design
     * ratio. The SIZES entries carry the % scaling, but a uniform scale
     * cancels out of a ratio, so this choice is %-immune; unknown
     * options default to narrow, which is the 4x2 case.
     */
    private fun isWide(context: Context, appWidgetId: Int): Boolean {
        val aspect = reportedAspect(context, appWidgetId) ?: return false
        return aspect > DESIGN_W
    }

    private fun reportedAspect(context: Context, appWidgetId: Int): Float? {
        val options = runCatching {
            AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        }.getOrNull() ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<SizeF>(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
            )
            val first = sizes?.firstOrNull()
            if (first != null && first.height > 0f) {
                return first.width / first.height
            }
        }
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        // Same rule Glance's own estimateSizes fallback uses: portrait
        // takes minWidth x maxHeight, landscape maxWidth x minHeight.
        val portrait = context.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_PORTRAIT
        val w = if (portrait) minW else maxW
        val h = if (portrait) maxH else minH
        if (w > 0 && h > 0) return w.toFloat() / h.toFloat()
        return null
    }
}
