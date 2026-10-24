package com.ivor.ivormusic.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.DrawableCompat
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
import kotlin.math.pow

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
 * **Only the print turns.** On a turntable the light on the grooves
 * stays where the lamp is; what moves is what is printed on the record. So the
 * sheen is a still layer and the turning one is the label artwork - the
 * SoftBurst sticker with its type knocked out and a scuff on the grooves, all
 * off-centre so the rotation reads. Paused, the same artwork is drawn still.
 *
 * **Every size is proportional to the window.** SizeMode.Exact hands this
 * composition the launcher's true bounding box, and nothing here is clamped
 * to a fixed size: the transport column is sized by the height it has to
 * fill, the stage takes the width that is left, and the record takes the
 * smaller of its height-bound and width-bound size - so the widget scales to
 * whatever cell it is resized into instead of one designed size. The
 * transport buttons are capped by width as well as height, so resizing
 * taller grows the record instead of shrinking it: a height-only button cap
 * ate the stage's width as the window grew, leaving dead space below art
 * that could have filled the window. The row fills the window and centers
 * its content, so any leftover is shared, never all below.
 *
 * **Color comes from two places, never mixed.** The buttons take the app's
 * selected palette - the same scheme the app UI draws in, so changing the
 * palette style in Settings recolors them with it. The record keeps album
 * colors: the center-label sticker takes one album swatch, the disc a
 * second, darkened, translucent one, like a fancy coloured vinyl - the
 * translucency is unchanged, the darker pick keeps real contrast against
 * the wallpaper - but the sticker stays fully opaque, so its print stays
 * solid and legible over the see-through disc. The rotation's tint needs
 * API 31; on 30 the record is drawn still rather than turning in the wrong
 * colour.
 *
 * **The glyph ink is baked, not tinted.** The play/pause/next glyph is drawn
 * into the button bitmap itself in black or white, whichever contrasts
 * better with that button's album color - so it reads on any album color,
 * on any launcher, instead of trusting a runtime tint over a vector.
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

/** How see-through the disc is: a fancy coloured vinyl, not a solid record. */
private const val VINYL_DISC_ALPHA = 0.64f

@Composable
private fun VinylContent(ui: PlayerWidgetUi) {
    // The vinyl is the widget's only state: it renders even with no media
    // (placeholder sleeve, parked arm), backed by the last-played snapshot
    // the store persists. There is no "Tap to listen" fallback.

    val size = LocalSize.current
    val playing = ui.snapshot.isPlaying
    // Proportional to the window, start to finish. The transport column is
    // sized by the height it has to fill (two buttons plus the gap), the
    // stage takes the width that is left, and the record takes the smaller
    // of its height-bound and width-bound size. Nothing is clamped to a
    // fixed dp, so a resized cell rescales the whole widget instead of
    // leaving small buttons beside a large record (or the reverse). The
    // cookie's lobes don't fill their box, so the sleeve box is the record
    // over the shape's true visual fill fraction, and the arm stands a
    // quarter of a record further out.
    val availWidth = size.width - VINYL_PADDING * 2
    val availHeight = size.height - VINYL_PADDING * 2
    val gap = (availHeight * 0.04f).coerceAtLeast(4.dp)
    // Capped by width as well as height: with a height-only cap, resizing
    // taller grew the buttons, which ate the stage's width and shrank the
    // record - the widget got taller and its art got smaller.
    val playSize = minOf(availHeight * 0.46f, availWidth * 0.20f).coerceAtLeast(24.dp)
    val nextSize = playSize * 0.95f
    val cookieFill = WidgetShapes.cookie4SidedFillFraction()
    val record = minOf(
        availHeight * cookieFill,
        (availWidth - gap - playSize) / (1f / cookieFill + 0.75f),
    ).coerceAtLeast(1.dp)
    val sleeve = record / cookieFill
    val armReach = record / 4
    val stage = sleeve + record / 2 + armReach

    // The buttons take the app palette's primary - the same scheme the app
    // UI draws in, resolved from the palette style in Settings, so they
    // follow a palette change (the palette setters push a widget redraw).
    // The record keeps album colors: a second album swatch for the label,
    // the darkened vibrant translucent for the disc.
    val buttonColor = GlanceTheme.colors.primary.toArgb()
    val labelColor = ui.snapshot.artworkLabelColor?.let { ColorProvider(Color(it)) }
        ?: GlanceTheme.colors.primaryContainer

    // No card, like Bloom: the sleeve, the buttons and the label are all
    // opaque and stand on the wallpaper by themselves - only the disc is
    // translucent, by design. That is also why there is no title here -
    // theme-coloured text on a wallpaper nobody chose is the one thing that
    // could not supply its own contrast, and the cover on the sleeve already
    // says what is playing.
    WidgetSurface(background = null, openAppOnTap = false) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(VINYL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = GlanceModifier.width(stage).height(sleeve)) {
                Box(
                    modifier = GlanceModifier.fillMaxSize().padding(end = armReach),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    VinylRecord(
                        size = record,
                        playing = playing,
                        discColor = discColor(ui.snapshot.artworkColor),
                        labelColor = labelColor,
                    )
                }
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())) {
                        WidgetArtwork(ui.artwork, size = sleeve, shape = ArtworkShape.COOKIE4SIDED)
                    }
                }
                // Last, so the arm lies over the record: on the grooves while
                // it plays, parked beside the disc when it does not.
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    val accent = ui.snapshot.artworkColor?.let { ColorProvider(Color(it)) }
                        ?: GlanceTheme.colors.primary
                    VinylArm(
                        width = record + armReach,
                        height = record,
                        onRecord = playing,
                        accentColor = accent
                    )
                }
            }
            Spacer(modifier = GlanceModifier.width(gap))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SunnyPlayButton(isPlaying = playing, size = playSize, color = buttonColor)
                Spacer(modifier = GlanceModifier.height(gap))
                FlowerNextButton(size = nextSize, color = buttonColor)
            }
        }
    }
}

/**
 * The tonearm, in one of its two poses. Three masks: a soft shadow that also
 * gives it an edge on any wallpaper, the metal, and the headshell and pivot
 * cap in the accent colour.
 */
@Composable
private fun VinylArm(width: Dp, height: Dp, onRecord: Boolean, accentColor: ColorProvider) {
    val colors = GlanceTheme.colors
    val dark = KodaWidgetTheme.isDark(LocalContext.current)
    val layers = if (onRecord) {
        listOf(
            R.drawable.vinyl_arm_on_shadow to recordColor(),
            R.drawable.vinyl_arm_on_metal to (if (dark) colors.onSurface else colors.surface),
            R.drawable.vinyl_arm_on_accent to accentColor,
        )
    } else {
        listOf(
            R.drawable.vinyl_arm_off_shadow to recordColor(),
            R.drawable.vinyl_arm_off_metal to (if (dark) colors.onSurface else colors.surface),
            R.drawable.vinyl_arm_off_accent to accentColor,
        )
    }
    Box(modifier = GlanceModifier.width(width).height(height)) {
        layers.forEach { (mask, color) -> VinylLayer(mask, color, ContentScale.FillBounds) }
    }
}

/**
 * Expressive transport buttons, deliberately different shapes: the official
 * M3E Sunny for play - points, unmistakable at a glance - against the
 * official M3E Flower for next, rounded petals. Both tinted by the app
 * palette's primary, never the album. The shape AND the glyph are baked
 * into one bitmap, so the glyph ink is exactly the contrast color for
 * that button's color - no runtime tint for a launcher to drop. The whole
 * bitmap is the tap target.
 */
@Composable
private fun SunnyPlayButton(isPlaying: Boolean, size: Dp, color: Int) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val px = (size.value * density).toInt().coerceAtLeast(1)
    val bgColor = color
    val glyphRes = if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
    val bg = androidx.compose.runtime.remember(px, bgColor, glyphRes) {
        transportButtonBitmap(context, WidgetShapes.sunny(px.toFloat()), px, bgColor, glyphRes)
    }
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
    }
}

@Composable
private fun FlowerNextButton(size: Dp, color: Int) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val px = (size.value * density).toInt().coerceAtLeast(1)
    val bgColor = color
    val bg = androidx.compose.runtime.remember(px, bgColor) {
        transportButtonBitmap(context, WidgetShapes.flower(px.toFloat()), px, bgColor, R.drawable.ic_media_next)
    }
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
    }
}

/**
 * One transport button as a single bitmap: the expressive shape filled with
 * [bgColor], the glyph drawn over it in the ink that contrasts best with
 * that color. Baking the glyph in - instead of overlaying a tinted vector -
 * is what makes the contrast hold on every launcher: nothing downstream can
 * drop or ignore the tint.
 */
private fun transportButtonBitmap(
    context: Context,
    shape: android.graphics.Path,
    sizePx: Int,
    bgColor: Int,
    @DrawableRes glyphRes: Int,
): Bitmap {
    val side = sizePx.coerceAtLeast(1)
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
    canvas.drawPath(shape, paint)
    val glyph = context.getDrawable(glyphRes)?.mutate()
    if (glyph != null) {
        DrawableCompat.setTint(glyph, glyphInk(bgColor))
        val g = (side * 0.42f).toInt().coerceAtLeast(1)
        val left = (side - g) / 2
        glyph.setBounds(left, left, left + g, left + g)
        glyph.draw(canvas)
    }
    return out
}

/**
 * The glyph ink for a button color: near-black or white, whichever has the
 * higher WCAG contrast ratio against it. Relative luminance, sRGB
 * linearized - a plain brightness threshold misreads saturated album colors
 * (a vivid green is bright but still needs a dark glyph; a deep red is dark
 * but a threshold can call it light).
 */
private fun glyphInk(bgColor: Int): Int {
    fun lin(channel: Int): Double {
        val s = channel / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    val r = (bgColor shr 16) and 0xFF
    val g = (bgColor shr 8) and 0xFF
    val b = bgColor and 0xFF
    val lum = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    // Contrast against white (luminance 1) and against near-black ink (~0.01).
    val contrastWhite = 1.05 / (lum + 0.05)
    val contrastDark = (lum + 0.05) / 0.06
    return if (contrastDark >= contrastWhite) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
}

/** A record is dark whichever way the theme faces. */
@Composable
private fun recordColor(): ColorProvider {
    val dark = KodaWidgetTheme.isDark(LocalContext.current)
    return if (dark) GlanceTheme.colors.background else GlanceTheme.colors.onSurface
}

/**
 * The disc, translucent like a fancy coloured vinyl: the album's vibrant,
 * darkened, at [VINYL_DISC_ALPHA], falling back to the dark record colour
 * at the same alpha when there is no artwork color. The translucency is
 * unchanged - what changed is the pick: the raw vibrant came out too light
 * and washed out at 64% alpha, so the disc color is darkened (same hue and
 * saturation, value scaled down) to keep real contrast against the
 * wallpaper. The tint's alpha is what makes the wallpaper show through the
 * disc - the label tint below stays fully opaque, and nothing here touches
 * it.
 */
@Composable
private fun discColor(artworkColor: Int?): ColorProvider {
    val base = recordColor().read()
    val solid = artworkColor?.let { darken(Color(it)) } ?: base
    return ColorProvider(solid.copy(alpha = VINYL_DISC_ALPHA))
}

/** Same hue and saturation, darker value: a translucent disc that still reads. */
private fun darken(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[2] = (hsv[2] * 0.75f).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
private fun VinylRecord(
    size: Dp,
    playing: Boolean,
    discColor: ColorProvider,
    labelColor: ColorProvider,
) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    val dark = KodaWidgetTheme.isDark(context)
    // The light on the grooves is light in both themes, for the same reason.
    val sheen = if (dark) colors.onSurface else colors.surface
    // The label is the official M3E SoftBurst, baked into the spinning
    // artwork (drawable-nodpi/vinyl_label_marks.png) together with its print:
    // the label's type is knocked out of the starburst so the disc shows
    // through like real print, and the groove scuffs ride along. A launcher
    // only animates resource drawables, never runtime bitmaps, so the label
    // could not spin as a separate layer -- as part of the disc's own artwork
    // it turns with the record, exactly like a real vinyl's printed label.
    // The label tint is FULLY OPAQUE, always: only the disc goes translucent,
    // never the sticker on it.

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
        VinylLayer(R.drawable.vinyl_disc, discColor)
        VinylLayer(R.drawable.vinyl_grooves, sheen)
        if (playing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The label turns with the record: it is baked into the spinning
            // artwork, driven by the same ProgressBar mechanism as before.
            val tint = ColorStateList.valueOf(labelColor.read().toArgb())
            val spin = RemoteViews(context.packageName, R.layout.widget_vinyl_spin).apply {
                setColorStateList(R.id.vinyl_spin, "setIndeterminateTintList", tint)
            }
            AndroidRemoteViews(remoteViews = spin, modifier = GlanceModifier.fillMaxSize())
        } else {
            // Parked: the same artwork sits still.
            VinylLayer(R.drawable.vinyl_label_marks, labelColor)
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
