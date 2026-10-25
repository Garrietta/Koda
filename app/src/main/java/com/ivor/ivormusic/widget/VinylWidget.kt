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
import android.os.SystemClock
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
 * SPIN IS OWNED, NOT HOST-ANIMATED: the label is a DETERMINATE
 * ProgressBar whose progress drawable is the rotate drawable, so progress
 * 0..3600 IS the angle 0..360 degrees. A ticker in VinylClassic advances
 * the angle while playing (one revolution per 1800ms, a record at
 * 33rpm); pausing stops the ticker, so the label freezes exactly where
 * it is, and only a new track resets it to 0. The old indeterminate
 * ProgressBar could not do this: its angle lived in the host, so every
 * pause swapped to the parked label at 0 and every resume restarted at 0.
 * Pre-S keeps the old parked label (the spin tint needs API 31).
 *
 * LAYERING: the stage is two bitmaps, not one. The disc layer (disc +
 * grooves) draws first, then the carve (label + spindle), then the top
 * layer (sleeve + arm). The sleeve therefore occludes the label's left
 * half — the disc sits BEHIND the sleeve, so no disc element can ever
 * paint over the album art. The tonearm never crossed the sleeve edge
 * anyway (verified against the arm PNGs), but its draw is clipped to
 * the sleeve edge as a structural guard.
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
        // Push a guaranteed-inflatable shell FIRST, synchronously, before
        // any rendering work: if the full render below throws for any
        // reason (bad snapshot, bad artwork, a hostile device state), the
        // widget is still ADDED — empty but tappable — instead of One UI
        // showing "Couldn't add widget." The full render replaces the
        // shell milliseconds later, so a healthy add never shows it.
        for (id in appWidgetIds) {
            runCatching {
                appWidgetManager.updateAppWidget(id, VinylClassic.staticViews(context, id))
            }.onFailure {
                KLog.w("VinylWidget", "Static shell push failed for $id: ${it.message}")
            }
        }
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
        // Same static-first rule as onUpdate: the add/resize can never
        // fail for lack of content.
        runCatching {
            appWidgetManager.updateAppWidget(appWidgetId, VinylClassic.staticViews(context, appWidgetId))
        }.onFailure {
            KLog.w("VinylWidget", "Static shell push failed for $appWidgetId: ${it.message}")
        }
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

    /** Label rotation: one revolution per 1800ms, a record at 33rpm. */
    private const val SPIN_PERIOD_MS = 1800L

    /** Progress range for the determinate spin bar: tenths of a degree. */
    private const val SPIN_MAX = 3600

    /** Ticker cadence while playing: 50ms = 10 degrees per tick. */
    private const val SPIN_TICK_MS = 50L

    /**
     * The owned spin angle. The host used to own it (indeterminate
     * ProgressBar), which is why pause snapped to 0 and resume restarted
     * at 0: the app could neither see nor set the host's angle. Now the
     * app owns it: it advances only while playing, freezes on pause, and
     * resets only when the track key changes. Guarded by [spinLock]:
     * updateAll runs on more than one scope.
     */
    private val spinLock = Any()
    private val spinScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var spinJob: Job? = null
    private var spinTrackKey: String? = null
    @Volatile
    private var spinAngleDeg: Float = 0f

    /** Current angle for a render; resets to 0 only on a track change. */
    private fun spinAngleFor(snap: PlayerWidgetSnapshot): Float {
        val key = snap.mediaId?.takeIf { it.isNotBlank() }
            ?: snap.artworkUri?.toString()?.takeIf { it.isNotBlank() }
            ?: snap.title?.takeIf { it.isNotBlank() }
        if (key != null) {
            synchronized(spinLock) {
                if (key != spinTrackKey) {
                    spinTrackKey = key
                    spinAngleDeg = 0f
                }
            }
        }
        return spinAngleDeg
    }

    /** Start the ticker while playing, stop it otherwise. Angle survives. */
    private fun syncSpinTicker(context: Context, snap: PlayerWidgetSnapshot) {
        synchronized(spinLock) {
            if (snap.hasMedia && snap.isPlaying) {
                if (spinJob == null) {
                    val appContext = context.applicationContext
                    spinJob = spinScope.launch {
                        var last = SystemClock.uptimeMillis()
                        while (isActive) {
                            delay(SPIN_TICK_MS)
                            val now = SystemClock.uptimeMillis()
                            spinAngleDeg =
                                (spinAngleDeg + (now - last) * 360f / SPIN_PERIOD_MS) % 360f
                            last = now
                            pushSpinProgress(appContext)
                        }
                    }
                }
            } else {
                spinJob?.cancel()
                spinJob = null
            }
        }
    }

    private fun stopSpinTicker() {
        synchronized(spinLock) {
            spinJob?.cancel()
            spinJob = null
        }
    }

    /**
     * One-int partial push: only the label's progress moves, so a tick
     * costs a few bytes instead of a bitmap. Tint and bitmaps are set by
     * the full render; ticks never touch them.
     */
    private fun pushSpinProgress(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = runCatching {
            manager.getAppWidgetIds(ComponentName(context, VinylWidgetReceiver::class.java))
        }.getOrNull() ?: return
        if (ids.isEmpty()) {
            // Widget removed while playing: no next publish will stop us.
            stopSpinTicker()
            return
        }
        val progress = angleToProgress(spinAngleDeg)
        for (id in ids) {
            val wide = runCatching { isWide(context, id) }.getOrDefault(false)
            val views = RemoteViews(
                context.packageName,
                if (wide) R.layout.widget_vinyl_wide else R.layout.widget_vinyl_narrow,
            )
            views.setProgressBar(R.id.vinyl_label_spin, SPIN_MAX, progress, false)
            runCatching { manager.updateAppWidget(id, views) }
        }
    }

    private fun angleToProgress(angleDeg: Float): Int =
        (angleDeg * 10f).roundToInt().coerceIn(0, SPIN_MAX)

    suspend fun updateAll(context: Context) {
        // Seeding must never take the update down with it: a failed seed
        // still renders (empty state, placeholder tile) instead of pushing
        // nothing, which is what One UI reports as "Couldn't add widget."
        runCatching { PlayerWidgetHost.ensureSeeded(context) }
            .onFailure { KLog.w("VinylWidget", "Seed failed, rendering empty: ${it.message}") }
        val ui = PlayerWidgetHost.state.value
        val ids = runCatching {
            AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, VinylWidgetReceiver::class.java),
            )
        }.getOrNull() ?: IntArray(0)
        if (ids.isEmpty()) {
            stopSpinTicker()
            return
        }
        val manager = AppWidgetManager.getInstance(context)
        // The spin angle is owned here, not by the host: reset it only on
        // a track change, before the render, so a full render never moves
        // the label on a play/pause transition.
        val angleDeg = spinAngleFor(ui.snapshot)
        // Render once for every instance, but a render failure must not
        // leave any id without content: fall back to the static shell.
        val rendered = try {
            render(context, ui, angleDeg)
        } catch (e: Exception) {
            KLog.w("VinylWidget", "Render failed, pushing static shell: ${e.message}")
            for (id in ids) {
                runCatching { manager.updateAppWidget(id, staticViews(context, id)) }
            }
            syncSpinTicker(context, ui.snapshot)
            return
        }
        for (id in ids) {
            // A bad options bundle must never take the whole update down:
            // fall back to narrow (the 4x2 native variant) for this id.
            val wide = runCatching { isWide(context, id) }.getOrDefault(false)
            // Tiered push, per id: full render first; if the host rejects
            // it (any single RemoteViews action can fail the whole apply),
            // retry with bitmaps but WITHOUT the reflection actions (spin
            // tint, content descriptions); if that also fails, the static
            // shell. An id is never left with no content.
            val pushed = runCatching {
                manager.updateAppWidget(id, rendered.forId(context, wide))
            }.isSuccess || runCatching {
                manager.updateAppWidget(id, rendered.forId(context, wide, minimal = true))
            }.isSuccess || runCatching {
                manager.updateAppWidget(id, staticViews(context, id))
            }.isSuccess
            if (!pushed) {
                KLog.w("VinylWidget", "All push tiers failed for $id")
            }
        }
        syncSpinTicker(context, ui.snapshot)
    }

    /**
     * Minimal shell: layout + taps only — no bitmaps, no tint, no content
     * descriptions. Every id exists in both variants and every view class
     * is RemoteViews-legal, so this cannot fail to inflate. It is what an
     * add falls back to, and what onUpdate pushes synchronously before
     * the full render, so a failed enrichment can never fail the add.
     *
     * Deliberately NOT variant-aware: the shell always uses the narrow
     * layout (the 4x2 native variant) and never reads AppWidgetManager
     * options. The shell's only job is to land something inflatable the
     * instant the add happens, and an options read on that path is the
     * one system call that can fail on an OEM-modified bundle and take
     * the add down with it. The async full render picks the correct
     * variant milliseconds later, so a wide cell shows the narrow shell
     * only for a blink, and only if the full render is slow.
     */
    fun staticViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(
            context.packageName,
            R.layout.widget_vinyl_narrow,
        )
        views.setOnClickPendingIntent(R.id.vinyl_tap_sleeve, openAppIntent(context))
        views.setOnClickPendingIntent(R.id.vinyl_tap_record, toggleIntent(context))
        views.setOnClickPendingIntent(R.id.vinyl_btn_play, toggleIntent(context))
        views.setOnClickPendingIntent(R.id.vinyl_btn_next, nextIntent(context))
        return views
    }

    /**
     * One render for every instance: the bitmaps do not depend on the
     * cell, only the layout variant does, and that is picked per id.
     * The variant choice reads the reported aspect only — a uniform %
     * scale leaves it unchanged.
     */
    private fun render(context: Context, ui: PlayerWidgetUi, angleDeg: Float): Rendered {
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
        val disc = discBitmap(
            context = context,
            artworkColor = snap.artworkColor,
            recordColor = recordColor,
            light = light,
        )
        val top = topBitmap(
            context = context,
            artwork = ui.artwork,
            recordColor = recordColor,
            light = light,
            accent = snap.artworkColor ?: scheme.primary.toArgb(),
            secondaryContainer = scheme.secondaryContainer.toArgb(),
            onSecondaryContainer = scheme.onSecondaryContainer.toArgb(),
            isPlaying = snap.isPlaying,
        )
        return Rendered(
            disc = disc,
            top = top,
            play = buttonBitmap(context, play = true, isPlaying = snap.isPlaying, color = buttonColor),
            next = buttonBitmap(context, play = false, isPlaying = false, color = buttonColor),
            label = maskBitmap(context, R.drawable.vinyl_label_marks, labelColor),
            spindle = maskBitmap(context, R.drawable.vinyl_spindle, recordColor),
            labelColor = labelColor,
            angleDeg = angleDeg,
        )
    }

    private class Rendered(
        val disc: Bitmap,
        val top: Bitmap,
        val play: Bitmap,
        val next: Bitmap,
        val label: Bitmap,
        val spindle: Bitmap,
        val labelColor: Int,
        val angleDeg: Float,
    ) {
        fun forId(context: Context, wide: Boolean, minimal: Boolean = false): RemoteViews {
            val views = RemoteViews(
                context.packageName,
                if (wide) R.layout.widget_vinyl_wide else R.layout.widget_vinyl_narrow,
            )
            views.setImageViewBitmap(R.id.vinyl_disc_art, disc)
            views.setImageViewBitmap(R.id.vinyl_top_art, top)
            views.setImageViewBitmap(R.id.vinyl_btn_play, play)
            views.setImageViewBitmap(R.id.vinyl_btn_next, next)
            views.setImageViewBitmap(R.id.vinyl_label_parked, label)
            views.setImageViewBitmap(R.id.vinyl_spindle, spindle)
            // Determinate rotation on every API: progress 0..3600 is the
            // angle 0..360 degrees. Pre-S shows the parked label instead
            // (the spin tint needs API 31, as before); S+ shows the label
            // at the owned angle, frozen when paused, 0 on a new track.
            // The parked label sits under the top layer either way, so the
            // sleeve occludes its left half on every API.
            val spinOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            views.setViewVisibility(
                R.id.vinyl_label_spin,
                if (spinOn) View.VISIBLE else View.GONE,
            )
            views.setViewVisibility(
                R.id.vinyl_label_parked,
                if (spinOn) View.GONE else View.VISIBLE,
            )
            views.setProgressBar(
                R.id.vinyl_label_spin,
                SPIN_MAX,
                angleToProgress(angleDeg),
                false,
            )
            if (spinOn && !minimal) {
                // Tint the turning print the sticker colour. setColorStateList
                // on RemoteViews needs API 31, which the spin itself needs.
                // Skipped on the minimal tier: it is the only reflection
                // action in the widget, so it is the first thing dropped
                // when the host rejects the full render.
                views.setColorStateList(
                    R.id.vinyl_label_spin,
                    "setProgressTintList",
                    ColorStateList.valueOf(labelColor),
                )
            }
            views.setOnClickPendingIntent(R.id.vinyl_tap_sleeve, openAppIntent(context))
            views.setOnClickPendingIntent(R.id.vinyl_tap_record, toggleIntent(context))
            views.setOnClickPendingIntent(R.id.vinyl_btn_play, toggleIntent(context))
            views.setOnClickPendingIntent(R.id.vinyl_btn_next, nextIntent(context))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !minimal) {
                views.setContentDescription(R.id.vinyl_btn_play, "Play or pause")
                views.setContentDescription(R.id.vinyl_btn_next, "Next track")
                views.setContentDescription(R.id.vinyl_tap_sleeve, "Open Koda")
            }
            return views
        }
    }

    /**
     * Geometry shared by the two stage layers. Both bitmaps are the same
     * stage rect, so their adjustViewBounds boxes coincide exactly and
     * everything aligned to the disc layer (label carve, taps) tracks
     * both layers at every size.
     */
    private class StageGeometry(
        val width: Int,
        val height: Int,
        val recordRect: RectF,
        val armRect: RectF,
        val sleeveRect: RectF,
    )

    private fun stageGeometry(): StageGeometry {
        val fill = WidgetShapes.cookie4SidedFillFraction()
        // Stage width in height units: sleeve box (1.0) + half-record
        // protrusion (fill / 2) + arm reach (record / 4).
        val stageW = 1f + fill / 2f + fill / 4f
        val width = (STAGE_PX * stageW).roundToInt()
        val height = STAGE_PX
        val record = fill * height
        val armReach = record / 4f
        val recordLeft = width - armReach - record
        val recordTop = (height - record) / 2f
        return StageGeometry(
            width = width,
            height = height,
            recordRect = RectF(recordLeft, recordTop, recordLeft + record, recordTop + record),
            armRect = RectF(recordLeft, recordTop, width.toFloat(), recordTop + record),
            sleeveRect = RectF(0f, 0f, height.toFloat(), height.toFloat()),
        )
    }

    /** Bottom layer: disc + grooves on transparency. */
    private fun discBitmap(
        context: Context,
        artworkColor: Int?,
        recordColor: Int,
        light: Int,
    ): Bitmap {
        val g = stageGeometry()
        val out = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val discBase = if (artworkColor != null) {
            darken(artworkColor, DISC_DARKEN)
        } else {
            recordColor
        }
        val discColor = withAlpha(discBase, VINYL_DISC_ALPHA)
        drawMask(canvas, context, R.drawable.vinyl_disc, discColor, g.recordRect)
        drawMask(canvas, context, R.drawable.vinyl_grooves, light, g.recordRect)
        return out
    }

    /**
     * Top layer: sleeve (or placeholder tile) + arm, drawn OVER the carve
     * in the layout, so the sleeve occludes the label's and spindle's
     * left half — the disc sits behind the sleeve, never over the art.
     * The arm draw is clipped to the sleeve edge: arm pixels never cross
     * it today, and the clip makes that structural rather than lucky.
     */
    private fun topBitmap(
        context: Context,
        artwork: Bitmap?,
        recordColor: Int,
        light: Int,
        accent: Int,
        secondaryContainer: Int,
        onSecondaryContainer: Int,
        isPlaying: Boolean,
    ): Bitmap {
        val g = stageGeometry()
        val out = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (artwork != null) {
            // A corrupt/recycled cover must never kill the render: fall
            // back to the placeholder tile on any artwork failure.
            val sleeve = try {
                maskedArtwork(artwork, ArtworkShape.COOKIE4SIDED, g.height)
            } catch (e: Exception) {
                KLog.w("VinylWidget", "Artwork mask failed, using tile: ${e.message}")
                null
            }
            if (sleeve != null) {
                canvas.drawBitmap(sleeve, null, g.sleeveRect, null)
            } else {
                drawPlaceholderTile(canvas, context, g.height, g.sleeveRect, secondaryContainer, onSecondaryContainer)
            }
        } else {
            drawPlaceholderTile(canvas, context, g.height, g.sleeveRect, secondaryContainer, onSecondaryContainer)
        }
        canvas.save()
        canvas.clipRect(g.sleeveRect.right, 0f, g.width.toFloat(), g.height.toFloat())
        val armPose = if (isPlaying) "on" else "off"
        val armRes = { layer: String ->
            context.resources.getIdentifier(
                "vinyl_arm_${armPose}_$layer",
                "drawable",
                context.packageName,
            )
        }
        drawMask(canvas, context, armRes("shadow"), recordColor, g.armRect)
        drawMask(canvas, context, armRes("metal"), light, g.armRect)
        drawMask(canvas, context, armRes("accent"), accent, g.armRect)
        canvas.restore()
        return out
    }

    /** No-artwork placeholder: flat shape tile + note mark, centred. */
    private fun drawPlaceholderTile(
        canvas: Canvas,
        context: Context,
        height: Int,
        sleeveRect: RectF,
        secondaryContainer: Int,
        onSecondaryContainer: Int,
    ) {
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
            // Unparceling an OEM-modified bundle can throw; never let it
            // take the update down — fall through to MIN/MAX below.
            val sizes = runCatching {
                @Suppress("DEPRECATION")
                options.getParcelableArrayList<SizeF>(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                )
            }.getOrNull()
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
