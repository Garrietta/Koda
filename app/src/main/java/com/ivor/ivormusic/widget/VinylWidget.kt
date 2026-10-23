package com.ivor.ivormusic.widget

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.R

/**
 * Vinyl: a record half out of its sleeve, turning while the music plays, with
 * a tonearm that comes down onto it and lifts off again. The sleeve is the
 * cover; play and next sit beside it. No card: it stands on the wallpaper.
 *
 * **The cover is the sleeve because the cover cannot turn.** A launcher will
 * rotate a drawable that ships in the app - an indeterminate `ProgressBar`
 * given a rotate drawable, which it animates on its own frame clock
 * ([R.layout.widget_vinyl_spin]) - but not a bitmap handed over at run time,
 * so album art on a spinning label was never available. Putting the art on the
 * sleeve is where it belongs on a real record anyway, and leaves the label
 * free to be something that can spin.
 *
 * **Only the label's print turns.** On a turntable the light on the grooves
 * stays where the lamp is; what moves is what is printed on the record. So the
 * sheen is a still layer and the turning one is the label's type and a scuff
 * on the grooves, both off-centre so the rotation reads. Paused, the same
 * drawable is drawn still.
 *
 * Every layer is an alpha mask tinted with a theme role, as in [PixelWidget]:
 * the record is drawn, not photographed, so it follows the palette. The
 * rotation's tint needs API 31; on 30 the record is drawn still rather than
 * turning in the wrong colour.
 */
class VinylWidget : GlanceAppWidget() {

    // Exact (not Responsive): LocalSize is the true widget bounding box, so
    // the layout scales dynamically to fill whatever space the launcher gives
    // instead of snapping to 3 predefined buckets.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PlayerWidgetHost.ensureSeeded(context)
        provideContent {
            val ui by PlayerWidgetHost.state.collectAsState()
            GlanceTheme(colors = KodaWidgetTheme.colors(context)) {
                VinylContent(ui)
            }
        }
    }
}

private val VINYL_PADDING = 8.dp

@Composable
private fun VinylContent(ui: PlayerWidgetUi) {
    // The vinyl is the widget's only state: it renders even with no media
    // (placeholder sleeve, parked arm), backed by the last-played snapshot
    // the store persists. There is no "Tap to listen" fallback.

    val size = LocalSize.current
    val playing = ui.snapshot.isPlaying
    // Measured against the actual cell, like every layout that can clip: the sleeve
    // takes the cell's height, the record shows half its width past it, the
    // arm stands a quarter of a record further out, and the transport gets
    // what is left.
    // The stage is about 1.75 sleeves wide, so the width caps the sleeve too:
    // a tall cell would otherwise push the buttons out of the cell.
    val widthForStage = size.width - VINYL_PADDING * 2 - 10.dp - 56.dp
    val sleeve = minOf(size.height - VINYL_PADDING * 2, (widthForStage + 4.dp) / 1.75f)
    val record = sleeve - 6.dp
    val armReach = record / 4
    val stage = sleeve + record / 2 + armReach
    val hero = (sleeve * 0.42f).coerceIn(44.dp, 72.dp)
    val side = (sleeve * 0.36f).coerceIn(36.dp, 64.dp)

    // No card, like Bloom: the sleeve, the record and the buttons are all
    // opaque and stand on the wallpaper by themselves. That is also why there
    // is no title here - theme-coloured text on a wallpaper nobody chose is
    // the one thing that could not supply its own contrast, and the cover on
    // the sleeve already says what is playing.
    WidgetSurface(background = null, openAppOnTap = false) {
        Row(
            modifier = GlanceModifier.padding(VINYL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = GlanceModifier.width(stage).height(sleeve)) {
                Box(
                    modifier = GlanceModifier.fillMaxSize().padding(end = armReach),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    VinylRecord(size = record, playing = playing)
                }
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())) {
                            WidgetArtwork(ui.artwork, size = sleeve, shape = ArtworkShape.COOKIE)
                        }
                        // The sleeve's shadow on the record beside it.
                        Image(
                            provider = ImageProvider(R.drawable.vinyl_sleeve_shadow),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            colorFilter = ColorFilter.tint(recordColor()),
                            modifier = GlanceModifier.width(10.dp).height(record - 8.dp),
                        )
                    }
                }
                // Last, so the arm lies over the record: the curved tonearm,
                // needle on the grooves while it plays, parked beside the
                // disc when it does not.
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    CurvedTonearm(
                        width = record + armReach,
                        height = record,
                        onRecord = playing,
                    )
                }
            }
            Spacer(modifier = GlanceModifier.width(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StarburstPlayButton(isPlaying = playing, size = hero)
                Spacer(modifier = GlanceModifier.height(6.dp))
                PuffyNextButton(size = side)
            }
        }
    }
}

/**
 * The tonearm, drawn like a real one: a pivot housing with a counterweight
 * stub, a tapered metallic arm tube on a gentle S-curve, and an angled
 * headshell with a cartridge and stylus tip. Two poses: the stylus rests on
 * the record's grooves while playing, parked off the disc when paused.
 * Rendered as a bitmap rather than layered masks.
 */
@Composable
private fun CurvedTonearm(width: Dp, height: Dp, onRecord: Boolean) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val wPx = (width.value * density).toInt().coerceAtLeast(1)
    val hPx = (height.value * density).toInt().coerceAtLeast(1)
    val arm = androidx.compose.runtime.remember(wPx, hPx, onRecord) {
        drawTonearm(wPx, hPx, onRecord)
    }
    Image(
        provider = ImageProvider(arm),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.width(width).height(height),
    )
}

/**
 * Draw a realistic tonearm bitmap, top-down.
 *
 * The bitmap is [widthPx] = record + arm reach wide and [heightPx] = record
 * tall; the record disc occupies x in [0, heightPx], centred vertically.
 */
private fun drawTonearm(widthPx: Int, heightPx: Int, onRecord: Boolean): android.graphics.Bitmap {
    val out = android.graphics.Bitmap.createBitmap(widthPx, heightPx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val w = widthPx.toFloat()
    val h = heightPx.toFloat()

    // Record geometry, in this bitmap's coordinates.
    val recordR = h / 2f
    val recordCx = recordR
    val recordCy = h / 2f

    // Pivot housing, back-right like a real deck.
    val px = w * 0.88f
    val py = h * 0.14f
    // Stylus contact: mid-grooves while playing, parked off the disc when paused.
    val grooveR = recordR * 0.62f
    val contactX: Float
    val contactY: Float
    val ctrlX: Float
    val ctrlY: Float
    if (onRecord) {
        contactX = recordCx + grooveR * 0.906f
        contactY = recordCy + grooveR * 0.423f
        ctrlX = w * 0.97f
        ctrlY = h * 0.40f
    } else {
        contactX = w * 0.94f
        contactY = h * 0.38f
        ctrlX = w * 1.02f
        ctrlY = h * 0.14f
    }

    // --- Arm tube: a tapered ribbon along the quadratic bezier P -> Q -> N.
    val samples = 28
    val leftPts = FloatArray((samples + 1) * 2)
    val rightPts = FloatArray((samples + 1) * 2)
    var endTx = 1f
    var endTy = 0f
    for (i in 0..samples) {
        val t = i.toFloat() / samples
        val mt = 1f - t
        val bx = mt * mt * px + 2f * mt * t * ctrlX + t * t * contactX
        val by = mt * mt * py + 2f * mt * t * ctrlY + t * t * contactY
        // Tangent of the quadratic at t.
        var tx = 2f * mt * (ctrlX - px) + 2f * t * (contactX - ctrlX)
        var ty = 2f * mt * (ctrlY - py) + 2f * t * (contactY - ctrlY)
        val len = kotlin.math.sqrt(tx * tx + ty * ty).coerceAtLeast(1e-6f)
        tx /= len
        ty /= len
        if (i == samples) {
            endTx = tx
            endTy = ty
        }
        // Normal, and a half-width that tapers from pivot to headshell.
        val nx = -ty
        val ny = tx
        val half = w * (0.034f + (0.020f - 0.034f) * t)
        leftPts[i * 2] = bx + nx * half
        leftPts[i * 2 + 1] = by + ny * half
        rightPts[i * 2] = bx - nx * half
        rightPts[i * 2 + 1] = by - ny * half
    }
    val tubePath = android.graphics.Path().apply {
        moveTo(leftPts[0], leftPts[1])
        for (i in 1..samples) lineTo(leftPts[i * 2], leftPts[i * 2 + 1])
        for (i in samples downTo 0) lineTo(rightPts[i * 2], rightPts[i * 2 + 1])
        close()
    }

    // Soft shadow so the arm sits on the record instead of floating.
    val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55000000
        maskFilter = android.graphics.BlurMaskFilter(w * 0.018f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.save()
    canvas.translate(w * 0.010f, h * 0.016f)
    canvas.drawPath(tubePath, shadowPaint)
    canvas.restore()

    // --- Counterweight: short stub up-right of the pivot with a weight disc.
    val cwDirX = 0.5f
    val cwDirY = -0.866f
    val stubPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A3F45.toInt()
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = w * 0.034f
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    val cwX = px + cwDirX * w * 0.14f
    val cwY = py + cwDirY * w * 0.14f
    canvas.drawLine(px, py, cwX, cwY, stubPaint)
    val cwPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.RadialGradient(
            cwX, cwY, w * 0.052f,
            intArrayOf(0xFF7A828C.toInt(), 0xFF33373D.toInt()),
            floatArrayOf(0f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
    }
    canvas.drawCircle(cwX, cwY, w * 0.052f, cwPaint)
    val cwRim = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF22262B.toInt()
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = w * 0.006f
    }
    canvas.drawCircle(cwX, cwY, w * 0.052f, cwRim)

    // --- Pivot housing: base disc, column, cap.
    val basePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.RadialGradient(
            px - w * 0.02f, py - w * 0.02f, w * 0.085f,
            intArrayOf(0xFF9AA2AB.toInt(), 0xFF4E545C.toInt()),
            floatArrayOf(0f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
    }
    canvas.drawCircle(px, py, w * 0.085f, basePaint)
    val baseRim = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2E3339.toInt()
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = w * 0.008f
    }
    canvas.drawCircle(px, py, w * 0.085f, baseRim)
    val columnPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.RadialGradient(
            px, py, w * 0.045f,
            intArrayOf(0xFFC3C9D0.toInt(), 0xFF5F666E.toInt()),
            floatArrayOf(0f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
    }
    canvas.drawCircle(px, py, w * 0.045f, columnPaint)
    val capPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2E3339.toInt()
    }
    canvas.drawCircle(px, py, w * 0.016f, capPaint)

    // --- Arm tube with a cylindrical metallic sheen across its width.
    val chordDx = contactX - px
    val chordDy = contactY - py
    val chordLen = kotlin.math.sqrt(chordDx * chordDx + chordDy * chordDy).coerceAtLeast(1e-6f)
    val perpX = -chordDy / chordLen
    val perpY = chordDx / chordLen
    val halfMax = w * 0.034f
    val tubePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.LinearGradient(
            px + perpX * halfMax, py + perpY * halfMax,
            px - perpX * halfMax, py - perpY * halfMax,
            intArrayOf(
                0xFF6E747C.toInt(),
                0xFFDDE2E7.toInt(),
                0xFFFFFFFF.toInt(),
                0xFFC3C9D0.toInt(),
                0xFF6E747C.toInt(),
            ),
            floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
    }
    canvas.drawPath(tubePath, tubePaint)

    // --- Headshell: angled black shell at the tip, with cartridge + stylus.
    val hsLen = w * 0.16f
    val hsHalf = w * 0.030f
    val angleDeg = Math.toDegrees(kotlin.math.atan2(endTy.toDouble(), endTx.toDouble())).toFloat()
    canvas.save()
    canvas.rotate(angleDeg, contactX, contactY)
    val shellPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF17191C.toInt()
    }
    canvas.drawRoundRect(
        contactX - hsLen, contactY - hsHalf,
        contactX + w * 0.012f, contactY + hsHalf,
        hsHalf, hsHalf, shellPaint,
    )
    // Cartridge block at the tip.
    val cartPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2A2E33.toInt()
    }
    canvas.drawRoundRect(
        contactX - w * 0.055f, contactY - w * 0.020f,
        contactX + w * 0.006f, contactY + w * 0.020f,
        w * 0.008f, w * 0.008f, cartPaint,
    )
    // Top highlight along the shell.
    val hlPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A3F45.toInt()
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = w * 0.006f
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    canvas.drawLine(contactX - hsLen + hsHalf, contactY - hsHalf + w * 0.008f,
        contactX - w * 0.02f, contactY - hsHalf + w * 0.008f, hlPaint)
    canvas.restore()
    // Stylus glint exactly at the contact point.
    val stylusPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2F5F7.toInt()
    }
    canvas.drawCircle(contactX, contactY, w * 0.007f, stylusPaint)

    return out
}

/**
 * Expressive transport buttons: the official M3E Sunny in amber, the official
 * M3E Puffy in white — per the M3E mockup. The shape is baked into a bitmap
 * background with the icon drawn over it; the whole thing is the tap target.
 */
@Composable
private fun StarburstPlayButton(isPlaying: Boolean, size: Dp) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val px = (size.value * density).toInt().coerceAtLeast(1)
    val bg = androidx.compose.runtime.remember(px) {
        filledPath(WidgetShapes.sunny(px.toFloat()), px, 0xFFFFB300.toInt())
    }
    val dark = ColorProvider(Color(0xFF1A1A1A.toInt()))
    Box(
        modifier = GlanceModifier
            .size(size)
            .clickable(widgetTransportAction(WidgetActionActivity.ACTION_TOGGLE))
            .semantics {
                contentDescription = context.getString(
                    if (isPlaying) R.string.widget_action_pause else R.string.widget_action_play
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(bg),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
        )
        Image(
            provider = ImageProvider(
                if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
            ),
            contentDescription = null,
            colorFilter = ColorFilter.tint(dark),
            modifier = GlanceModifier.size(size * 0.42f),
        )
    }
}

@Composable
private fun PuffyNextButton(size: Dp) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val px = (size.value * density).toInt().coerceAtLeast(1)
    val bg = androidx.compose.runtime.remember(px) {
        filledPath(WidgetShapes.puffy(px.toFloat()), px, 0xFFFFFFFF.toInt())
    }
    val dark = ColorProvider(Color(0xFF1A1A1A.toInt()))
    val nextDesc = widgetString(R.string.widget_action_next)
    Box(
        modifier = GlanceModifier
            .size(size)
            .clickable(widgetTransportAction(WidgetActionActivity.ACTION_NEXT))
            .semantics { contentDescription = nextDesc },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(bg),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
        )
        Image(
            provider = ImageProvider(R.drawable.ic_media_next),
            contentDescription = null,
            colorFilter = ColorFilter.tint(dark),
            modifier = GlanceModifier.size(size * 0.42f),
        )
    }
}

/** A record is dark whichever way the theme faces. */
@Composable
private fun recordColor(): ColorProvider {
    val dark = KodaWidgetTheme.isDark(LocalContext.current)
    return if (dark) GlanceTheme.colors.background else GlanceTheme.colors.onSurface
}

@Composable
private fun VinylRecord(size: Dp, playing: Boolean) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    val dark = KodaWidgetTheme.isDark(context)
    // The light on the grooves is light in both themes, for the same reason.
    val sheen = if (dark) colors.onSurface else colors.surface
    val print = colors.onPrimaryContainer
    // The label goes expressive: the official M3E SoftBurst cut from the
    // primary container, generated at the record's pixel size and cached per
    // size/color. The label is ~36% of the record diameter, centred, like the
    // mask it replaces.
    val density = context.resources.displayMetrics.density
    val px = (size.value * density).toInt().coerceAtLeast(1)
    val labelColor = colors.primaryContainer.read().toArgb()
    val softBurstLabel = androidx.compose.runtime.remember(px, labelColor) {
        val labelPx = (px * 0.36f).toInt().coerceAtLeast(1)
        val label = filledPath(WidgetShapes.softBurst(labelPx.toFloat()), labelPx, labelColor)
        val out = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val left = (px - labelPx) / 2f
        canvas.drawBitmap(label, left, left, null)
        out
    }

    Box(
        modifier = GlanceModifier
            .size(size)
            .semantics {
                contentDescription = context.getString(
                    if (playing) R.string.widget_action_pause else R.string.widget_action_play
                )
            }
            .clickable(widgetTransportAction(WidgetActionActivity.ACTION_TOGGLE)),
        contentAlignment = Alignment.Center,
    ) {
        VinylLayer(R.drawable.vinyl_disc, recordColor())
        VinylLayer(R.drawable.vinyl_grooves, sheen)
        Image(
            provider = ImageProvider(softBurstLabel),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.fillMaxSize(),
        )
        if (playing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val tint = ColorStateList.valueOf(print.read().toArgb())
            val spin = RemoteViews(context.packageName, R.layout.widget_vinyl_spin).apply {
                setColorStateList(R.id.vinyl_spin, "setIndeterminateTintList", tint)
            }
            AndroidRemoteViews(remoteViews = spin, modifier = GlanceModifier.fillMaxSize())
        } else {
            VinylLayer(R.drawable.vinyl_label_marks, print)
        }
        VinylLayer(R.drawable.vinyl_spindle, recordColor())
    }
}

/** One layer of the record: a mask the full size of the disc, tinted. */
@Composable
private fun VinylLayer(
    @DrawableRes mask: Int,
    color: ColorProvider,
    scale: ContentScale = ContentScale.Fit,
) {
    Image(
        provider = ImageProvider(mask),
        contentDescription = null,
        contentScale = scale,
        colorFilter = ColorFilter.tint(color),
        modifier = GlanceModifier.fillMaxSize(),
    )
}

class VinylWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VinylWidget()
}
