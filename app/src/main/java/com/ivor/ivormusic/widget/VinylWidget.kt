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
import kotlinx.coroutines.isActive
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
 * EXACT PAUSE: the freeze must happen the moment a pause is published,
 * not after the render. The publish path debounces 120ms, enriches
 * colours on IO, then bakes bitmaps — and the ticker used to run through
 * all of it, so the label froze 200ms+ past the pause instant, at a
 * visibly different angle from where the music stopped. Worse, the full
 * render captured its angle BEFORE baking, so the host applied it after
 * newer tick partials and the label jumped BACK on pause. So: a paused
 * publish freezes the ticker synchronously at publish entry; the full
 * render captures its angle AFTER baking; and a playing render re-pushes
 * the latest tick once, so no older angle can land after a newer one.
 *
 * ARM IS TWEENED, NOT SWAPPED: the tonearm used to teleport between its
 * two baked poses in a single frame. Now a play/pause transition swings
 * it over ~300ms in 50ms steps: the current pose's arm layers rotate
 * around the measured pivot (PNG 565.4, 95.8, from the two poses' tube
 * centerlines, 36.2 degrees apart) toward the target pose, with the true
 * target pose crossfading in over the last 30% so the swing lands exactly
 * on the true pose. The full render always carries the arm's CURRENT
 * position, never the target, so a render mid-swing cannot jump it; a
 * new transition cancels the old tween and swings from wherever the arm
 * is. No bounce, no overshoot: a short smooth swing, nothing crazy.
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
        // Push a placeholder shell FIRST, synchronously, before any
        // rendering work: if the full render below throws for any reason
        // (bad snapshot, bad artwork, a hostile device state), the widget
        // is still ADDED — showing the placeholder widget, not an empty
        // cell — instead of One UI showing "Couldn't add widget." The
        // shell is never transparent: it bakes the empty-state widget
        // (placeholder tile, parked arm, stored colours) with no Coil and
        // no network, so a slow full render reads as a widget loading,
        // not as no widget. The full render replaces it when ready.
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
     * Freeze the spin the moment a pause is published. Called
     * synchronously from PlayerWidgets.publish (before its 120ms
     * debounce, Palette enrichment, and bitmap baking) so the frozen
     * angle is the pause instant, not the angle hundreds of ms later
     * when the debounced render finally runs. The angle itself is kept:
     * resume continues from it, and only a track change resets it.
     */
    internal fun freezeSpin() {
        stopSpinTicker()
    }

    /**
     * Tonearm swing, measured from the arm PNGs (drawable-nodpi, 640x512):
     * the on/off tube centerlines intersect at (565.4, 95.8) — the pivot —
     * and sit 36.2 degrees apart (off leans +4.5 degrees from vertical,
     * on leans -31.7). The arm bitmaps are drawn stretched into armRect,
     * whose aspect (1.25) matches the PNG aspect exactly, so the stretch
     * is uniform and a rotation in stage space is a true rotation.
     */
    private const val ARM_SWING_DEG = 36.2f
    private const val ARM_PIVOT_PNG_X = 565.4f
    private const val ARM_PIVOT_PNG_Y = 95.8f
    private const val ARM_PNG_W = 640f

    /** Tween cadence and length: 50ms steps over ~300ms, no overshoot. */
    private const val ARM_TICK_MS = 50L
    private const val ARM_TWEEN_MS = 300L

    /**
     * The arm's current position, as (fromPose, progress): progress 0 is
     * exactly the fromPose's true layers, 1 is exactly the other pose's
     * true layers, in between is the rotated from-pose. Guarded by
     * [spinLock] with the spin state: the tween job and updateAll both
     * touch it. The fraction form is what updateAll compares against the
     * target pose: fromOn=true reads 1-progress, fromOn=false reads
     * progress.
     */
    @Volatile
    private var armFromOn: Boolean = false
    @Volatile
    private var armProgress: Float = 0f
    private var armJob: Job? = null

    private val armFraction: Float
        get() = if (armFromOn) 1f - armProgress else armProgress

    /** Inputs the last full render baked the top layer from, so a tween
     *  frame can re-bake it without a full render. The artwork reference
     *  is the same one PlayerWidgetHost already holds; nothing new is
     *  retained. */
    private class TopInputs(
        val artwork: Bitmap?,
        val recordColor: Int,
        val light: Int,
        val accent: Int,
        val secondaryContainer: Int,
        val onSecondaryContainer: Int,
    )

    private var lastTop: TopInputs? = null

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

    /**
     * One-bitmap partial push: only the top layer (sleeve + arm) moves,
     * so a tween frame costs one bitmap instead of a full render. Like
     * the spin ticks, it never touches the label progress or the buttons.
     */
    private fun pushTopBitmap(context: Context, top: Bitmap) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = runCatching {
            manager.getAppWidgetIds(ComponentName(context, VinylWidgetReceiver::class.java))
        }.getOrNull() ?: return
        if (ids.isEmpty()) {
            synchronized(spinLock) {
                armJob?.cancel()
                armJob = null
            }
            return
        }
        for (id in ids) {
            val wide = runCatching { isWide(context, id) }.getOrDefault(false)
            val views = RemoteViews(
                context.packageName,
                if (wide) R.layout.widget_vinyl_wide else R.layout.widget_vinyl_narrow,
            )
            views.setImageViewBitmap(R.id.vinyl_top_art, top)
            runCatching { manager.updateAppWidget(id, views) }
        }
    }

    /**
     * Swing the arm from its current position to [targetOn] over
     * [ARM_TWEEN_MS]. The sleeve is baked once; each frame re-composites
     * it with the arm at the next interpolated position. A new transition
     * cancels this job and starts from wherever the arm is, so rapid
     * play/pause taps reverse the swing instead of teleporting it.
     */
    private fun startArmTween(context: Context, targetOn: Boolean) {
        val appContext = context.applicationContext
        val inputs = lastTop ?: return
        synchronized(spinLock) {
            armJob?.cancel()
            armJob = spinScope.launch {
                // Capture the start once: the tween interpolates linearly
                // from here to the target, keeping fromOn fixed so the
                // rotation never flips sides mid-swing.
                val (fromOn, startProgress, targetProgress) = synchronized(spinLock) {
                    val tp = if (targetOn == armFromOn) 0f else 1f
                    Triple(armFromOn, armProgress, tp)
                }
                val start = SystemClock.uptimeMillis()
                while (isActive) {
                    val elapsed = SystemClock.uptimeMillis() - start
                    val t = (elapsed.toFloat() / ARM_TWEEN_MS).coerceIn(0f, 1f)
                    val progress = startProgress + (targetProgress - startProgress) * t
                    synchronized(spinLock) { armProgress = progress }
                    val frame = topBitmapWithArm(appContext, inputs, fromOn, progress)
                    pushTopBitmap(appContext, frame)
                    if (t >= 1f) break
                    delay(ARM_TICK_MS)
                }
                synchronized(spinLock) {
                    // Land exactly: progress 0/1 draws the true pose layers.
                    armProgress = targetProgress
                    armJob = null
                }
            }
        }
    }

    /** The top layer re-baked at an interpolated arm position. */
    private fun topBitmapWithArm(
        context: Context,
        inputs: TopInputs,
        fromOn: Boolean,
        progress: Float,
    ): Bitmap {
        val g = stageGeometry()
        val out = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        drawSleeve(canvas, context, inputs, g)
        drawArm(canvas, context, g, fromOn, progress, inputs.recordColor, inputs.light, inputs.accent)
        return out
    }

    suspend fun updateAll(context: Context) {
        // Seeding must never take the update down with it: a failed seed
        // still renders (empty state, placeholder tile) instead of pushing
        // nothing, which is what One UI reports as "Couldn't add widget."
        // Timed: a fresh add that "lands eventually" is a latency story,
        // and the next device report needs numbers, not guesses.
        val seedStart = SystemClock.uptimeMillis()
        runCatching { PlayerWidgetHost.ensureSeeded(context) }
            .onFailure { KLog.w("VinylWidget", "Seed failed, rendering empty: ${it.message}") }
        val seedMs = SystemClock.uptimeMillis() - seedStart
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
        // A paused snapshot stops the ticker BEFORE the render, not after:
        // a tick landing between the render and the old trailing stop
        // would un-freeze the label for one frame. And an arm tween in
        // flight is cancelled now, so its partials can never land after
        // this full render and jump the arm back mid-swing.
        if (!ui.snapshot.isPlaying) stopSpinTicker()
        synchronized(spinLock) {
            armJob?.cancel()
            armJob = null
        }
        val manager = AppWidgetManager.getInstance(context)
        // The full render carries the arm's CURRENT position, never the
        // target pose: the tween below swings it from here, so a render
        // mid-transition cannot teleport it.
        val renderStart = SystemClock.uptimeMillis()
        val rendered = try {
            render(context, ui)
        } catch (e: Exception) {
            KLog.w("VinylWidget", "Render failed, pushing static shell: ${e.message}")
            for (id in ids) {
                runCatching { manager.updateAppWidget(id, staticViews(context, id)) }
            }
            syncSpinTicker(context, ui.snapshot)
            return
        }
        val bakeMs = SystemClock.uptimeMillis() - renderStart
        if (seedMs + bakeMs > 1000L) {
            KLog.w("VinylWidget", "Slow full render: seed ${seedMs}ms, bake ${bakeMs}ms")
        } else {
            KLog.d("VinylWidget", "Full render: seed ${seedMs}ms, bake ${bakeMs}ms")
        }
        // Capture the angle AFTER baking, not before: baking takes
        // ~100ms+, and the old code captured it first, so the pushed full
        // render carried an angle older than the ticker's latest partial
        // — the host applied it after the partial and the label visibly
        // jumped BACK on pause. Reset still happens only on track change.
        rendered.angleDeg = spinAngleFor(ui.snapshot)
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
        // Re-sync: a tick that landed during the push loop is newer than
        // the full render's angle; push it again so the full render can't
        // leave the label behind by one tick.
        if (ui.snapshot.isPlaying) pushSpinProgress(context)
        syncSpinTicker(context, ui.snapshot)
        // Swing the arm only if the pose actually flipped: the full render
        // above already shows the current position, and the tween moves it
        // to the target from there.
        val targetOn = ui.snapshot.hasMedia && ui.snapshot.isPlaying
        if (armFraction != (if (targetOn) 1f else 0f)) {
            startArmTween(context, targetOn)
        }
    }

    /**
     * Placeholder shell: the empty-state widget baked synchronously —
     * placeholder tile sleeve, parked arm, disc, label, spindle, both
     * buttons — with NO Coil, NO network, and NO reflection actions, so
     * it can neither block on artwork nor fail a host apply. The old
     * shell set layout + taps only, and the layout carries no drawable
     * defaults anywhere, so until the full render landed the cell was
     * fully transparent and read as "no widget at all": a fresh add
     * after a delete (cold process, Coil artwork load, bitmap baking)
     * can leave that gap open for seconds. Stored colours (disc, label,
     * button) are used when the store has them, so the shell already
     * wears the last song's colours; the artwork itself follows with
     * the full render. Any bake failure falls back to the bare layout +
     * taps, never to no content.
     *
     * Deliberately NOT variant-aware: the shell always uses the narrow
     * layout (the 4x2 native variant) and never reads AppWidgetManager
     * options. The shell's only job is to land something visible the
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
        runCatching { bakeShellBitmaps(context, views) }
            .onFailure { KLog.w("VinylWidget", "Shell bake failed, bare shell: ${it.message}") }
        views.setOnClickPendingIntent(R.id.vinyl_tap_sleeve, openAppIntent(context))
        views.setOnClickPendingIntent(R.id.vinyl_tap_record, toggleIntent(context))
        views.setOnClickPendingIntent(R.id.vinyl_btn_play, toggleIntent(context))
        views.setOnClickPendingIntent(R.id.vinyl_btn_next, nextIntent(context))
        return views
    }

    /**
     * Empty-state bitmaps for the shell. No artwork load, so nothing here
     * can wait on Coil or the network; the only reads are theme and
     * store SharedPreferences, both synchronous. The spin bar is shown
     * (S+) at the current angle but deliberately NOT tinted: the tint is
     * the shell's only reflection action, and the minimal tier exists
     * precisely because a host can reject a whole apply over one
     * reflection action. The parked label covers pre-S.
     */
    private fun bakeShellBitmaps(context: Context, views: RemoteViews) {
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
        val stored = runCatching { PlayerWidgetStore.read(context) }.getOrNull()
        val buttonColor = KodaWidgetTheme.vinylButtonArgb(context, stored?.artworkColor)
        val labelColor = stored?.artworkLabelColor ?: scheme.primaryContainer.toArgb()
        views.setImageViewBitmap(
            R.id.vinyl_disc_art,
            discBitmap(context, stored?.artworkColor, recordColor, light),
        )
        val topInputs = TopInputs(
            artwork = null,
            recordColor = recordColor,
            light = light,
            accent = stored?.artworkColor ?: scheme.primary.toArgb(),
            secondaryContainer = scheme.secondaryContainer.toArgb(),
            onSecondaryContainer = scheme.onSecondaryContainer.toArgb(),
        )
        views.setImageViewBitmap(
            R.id.vinyl_top_art,
            topBitmapWithArm(context, topInputs, fromOn = false, progress = 0f),
        )
        views.setImageViewBitmap(
            R.id.vinyl_btn_play,
            buttonBitmap(
                context,
                play = true,
                isPlaying = stored?.isPlaying ?: false,
                color = buttonColor,
            ),
        )
        views.setImageViewBitmap(
            R.id.vinyl_btn_next,
            buttonBitmap(context, play = false, isPlaying = false, color = buttonColor),
        )
        views.setImageViewBitmap(
            R.id.vinyl_label_parked,
            maskBitmap(context, R.drawable.vinyl_label_marks, labelColor),
        )
        views.setImageViewBitmap(
            R.id.vinyl_spindle,
            maskBitmap(context, R.drawable.vinyl_spindle, recordColor),
        )
        val spinOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        views.setViewVisibility(
            R.id.vinyl_label_spin,
            if (spinOn) View.VISIBLE else View.GONE,
        )
        views.setViewVisibility(
            R.id.vinyl_label_parked,
            if (spinOn) View.GONE else View.VISIBLE,
        )
        views.setProgressBar(R.id.vinyl_label_spin, SPIN_MAX, angleToProgress(spinAngleDeg), false)
    }

    /**
     * One render for every instance: the bitmaps do not depend on the
     * cell, only the layout variant does, and that is picked per id.
     * The variant choice reads the reported aspect only — a uniform %
     * scale leaves it unchanged. The angle is NOT captured here: the
     * caller sets [Rendered.angleDeg] after baking, so the pushed angle
     * is never stale by the bake duration. The arm is rendered at its
     * current interpolated position, never the target pose.
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
        val disc = discBitmap(
            context = context,
            artworkColor = snap.artworkColor,
            recordColor = recordColor,
            light = light,
        )
        val topInputs = TopInputs(
            artwork = ui.artwork,
            recordColor = recordColor,
            light = light,
            accent = snap.artworkColor ?: scheme.primary.toArgb(),
            secondaryContainer = scheme.secondaryContainer.toArgb(),
            onSecondaryContainer = scheme.onSecondaryContainer.toArgb(),
        )
        lastTop = topInputs
        val (fromOn, progress) = synchronized(spinLock) { armFromOn to armProgress }
        val top = topBitmapWithArm(context, topInputs, fromOn, progress)
        return Rendered(
            disc = disc,
            top = top,
            play = buttonBitmap(context, play = true, isPlaying = snap.isPlaying, color = buttonColor),
            next = buttonBitmap(context, play = false, isPlaying = false, color = buttonColor),
            label = maskBitmap(context, R.drawable.vinyl_label_marks, labelColor),
            spindle = maskBitmap(context, R.drawable.vinyl_spindle, recordColor),
            labelColor = labelColor,
            angleDeg = 0f,
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
        var angleDeg: Float,
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
     * Sleeve (or placeholder tile) into the stage canvas, at the sleeve
     * rect. The sleeve draws before the arm and above the label carve in
     * the layout, so it occludes the label's left half — the disc sits
     * behind the sleeve, never over the art.
     */
    private fun drawSleeve(
        canvas: Canvas,
        context: Context,
        inputs: TopInputs,
        g: StageGeometry,
    ) {
        if (inputs.artwork != null) {
            // A corrupt/recycled cover must never kill the render: fall
            // back to the placeholder tile on any artwork failure.
            val sleeve = try {
                maskedArtwork(inputs.artwork, ArtworkShape.COOKIE4SIDED, g.height)
            } catch (e: Exception) {
                KLog.w("VinylWidget", "Artwork mask failed, using tile: ${e.message}")
                null
            }
            if (sleeve != null) {
                canvas.drawBitmap(sleeve, null, g.sleeveRect, null)
            } else {
                drawPlaceholderTile(canvas, context, g.height, g.sleeveRect, inputs.secondaryContainer, inputs.onSecondaryContainer)
            }
        } else {
            drawPlaceholderTile(canvas, context, g.height, g.sleeveRect, inputs.secondaryContainer, inputs.onSecondaryContainer)
        }
    }

    /** One pose's three arm layers, baked at armRect size. */
    private fun armBitmap(
        context: Context,
        g: StageGeometry,
        poseOn: Boolean,
        recordColor: Int,
        light: Int,
        accent: Int,
    ): Bitmap {
        val w = g.armRect.width().roundToInt()
        val h = g.armRect.height().roundToInt()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val pose = if (poseOn) "on" else "off"
        val full = RectF(0f, 0f, w.toFloat(), h.toFloat())
        drawMask(canvas, context, armRes(context, pose, "shadow"), recordColor, full)
        drawMask(canvas, context, armRes(context, pose, "metal"), light, full)
        drawMask(canvas, context, armRes(context, pose, "accent"), accent, full)
        return out
    }

    private fun armRes(context: Context, pose: String, layer: String): Int =
        context.resources.getIdentifier(
            "vinyl_arm_${pose}_$layer",
            "drawable",
            context.packageName,
        )

    /** One true pose's layers, drawn under the sleeve-edge clip. */
    private fun drawArmPose(
        canvas: Canvas,
        context: Context,
        g: StageGeometry,
        poseOn: Boolean,
        recordColor: Int,
        light: Int,
        accent: Int,
    ) {
        val bmp = armBitmap(context, g, poseOn, recordColor, light, accent)
        canvas.save()
        canvas.clipRect(g.sleeveRect.right, 0f, g.width.toFloat(), g.height.toFloat())
        canvas.drawBitmap(bmp, null, g.armRect, null)
        canvas.restore()
    }

    /**
     * The arm at an interpolated position. Progress 0 draws exactly the
     * fromOn pose's true layers, 1 exactly the other pose's true layers.
     * In between, the from-pose layers rotate around the measured pivot
     * toward the target angle, and the true target layers crossfade in
     * over the last 30% — so the swing lands exactly on the true pose
     * instead of a rotated approximation, and a mid-swing render never
     * jumps. The clip keeps arm pixels right of the sleeve edge, as
     * before: the arm never crosses it today, and the clip makes that
     * structural rather than lucky.
     */
    private fun drawArm(
        canvas: Canvas,
        context: Context,
        g: StageGeometry,
        fromOn: Boolean,
        progress: Float,
        recordColor: Int,
        light: Int,
        accent: Int,
    ) {
        if (progress <= 0f) {
            drawArmPose(canvas, context, g, fromOn, recordColor, light, accent)
            return
        }
        if (progress >= 1f) {
            drawArmPose(canvas, context, g, !fromOn, recordColor, light, accent)
            return
        }
        val bmp = armBitmap(context, g, fromOn, recordColor, light, accent)
        val s = g.armRect.width() / ARM_PNG_W
        val px = g.armRect.left + ARM_PIVOT_PNG_X * s
        val py = g.armRect.top + ARM_PIVOT_PNG_Y * s
        // ON leans -31.7 degrees from vertical, OFF +4.5: ON->OFF swings
        // +36.2 (clockwise on screen), OFF->ON swings -36.2.
        val rot = (if (fromOn) ARM_SWING_DEG else -ARM_SWING_DEG) * progress
        canvas.save()
        canvas.clipRect(g.sleeveRect.right, 0f, g.width.toFloat(), g.height.toFloat())
        canvas.rotate(rot, px, py)
        canvas.drawBitmap(bmp, null, g.armRect, null)
        canvas.restore()
        if (progress >= 0.7f) {
            val alpha = ((progress - 0.7f) / 0.3f * 255f).roundToInt().coerceIn(0, 255)
            val toBmp = armBitmap(context, g, !fromOn, recordColor, light, accent)
            val paint = Paint().apply { this.alpha = alpha }
            canvas.save()
            canvas.clipRect(g.sleeveRect.right, 0f, g.width.toFloat(), g.height.toFloat())
            canvas.drawBitmap(toBmp, null, g.armRect, paint)
            canvas.restore()
        }
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
