package com.ivor.ivormusic.widget

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the home screen widgets on the JVM, as the launcher would: each
 * widget's Glance content is composed to RemoteViews at a cell size and
 * inflated, on a wallpaper, so a layout can be looked at without a phone:
 *
 *     ./gradlew recordRoborazziDebug --tests '*WidgetScreenshotTest'
 *
 * writes widget_*.png to app/build/outputs/roborazzi. Covers are drawn
 * here (no network). A launcher's real cell size, its corner clip and
 * the ripple still need the phone.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xxhdpi", application = android.app.Application::class)
class WidgetScreenshotTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** A cover: a field of [base] with a sun of [accent] and a horizon, drawn rather than fetched. */
    private fun cover(base: Int, accent: Int, px: Int = 384): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, px.toFloat(), base, darken(base, 0.55f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, px.toFloat(), px.toFloat(), paint)
        paint.shader = RadialGradient(px * 0.62f, px * 0.38f, px * 0.24f, accent, darken(accent, 0.8f), Shader.TileMode.CLAMP)
        canvas.drawCircle(px * 0.62f, px * 0.38f, px * 0.2f, paint)
        paint.shader = null
        paint.color = darken(base, 0.35f)
        canvas.drawRect(0f, px * 0.7f, px.toFloat(), px.toFloat(), paint)
        return bitmap
    }

    private fun darken(color: Int, by: Float): Int {
        val r = ((color shr 16) and 0xFF) * by
        val g = ((color shr 8) and 0xFF) * by
        val b = (color and 0xFF) * by
        return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    private val queue = listOf(
        UpNextEntry(4, "CAN'T GET OVER YOU (feat. Clams Casino)", "Joji", "art:1"),
        UpNextEntry(5, "American Dreaming", "Sierra Ferrell", "art:2"),
        UpNextEntry(6, "Glasshouse", "Neon Coast", "art:3"),
        UpNextEntry(7, "Ultraviolet", "Neon Coast", "art:4"),
        UpNextEntry(8, "Paper Planes", "M.I.A.", "art:5"),
    )

    private val rowArt by lazy {
        mapOf(
            "art:1" to cover(0xFF1D3557.toInt(), 0xFFE9C46A.toInt(), 144),
            "art:2" to cover(0xFF6D597A.toInt(), 0xFFF4A261.toInt(), 144),
            "art:3" to cover(0xFF2A9D8F.toInt(), 0xFFFFF1E0.toInt(), 144),
            "art:4" to cover(0xFF5B4BFF.toInt(), 0xFFC77DF2.toInt(), 144),
            "art:5" to cover(0xFF264653.toInt(), 0xFFE76F51.toInt(), 144),
        )
    }

    private fun playing(
        artworkColor: Int = 0xFFE8434B.toInt(),
        art: Bitmap? = cover(0xFFB3262E.toInt(), 0xFFFFB347.toInt()),
        upNext: List<UpNextEntry> = queue,
        isPlaying: Boolean = true,
    ) = PlayerWidgetUi(
        PlayerWidgetSnapshot(
            hasMedia = true,
            isPlaying = isPlaying,
            title = "Boblo Boat (feat. J. Cole)",
            artist = "Royce da 5'9\"",
            artworkUri = null,
            positionMs = 61_000,
            durationMs = 248_000,
            hasNext = true,
            hasPrevious = true,
            shuffleEnabled = false,
            repeatMode = Player.REPEAT_MODE_OFF,
            upNext = upNext,
            mediaId = "id",
            album = "The Book of Ryan",
            artworkColor = artworkColor,
            artworkLabelColor = null,
        ),
        art,
        rowArt,
    )

    private fun shoot(
        name: String,
        cells: DpSize,
        dark: Boolean,
        ui: PlayerWidgetUi,
        // A picker preview: no margin, on black or white (tools/widget_previews.py recovers the
        // transparency from the pair), written where that script picks it up.
        preview: Boolean = false,
        previewGround: Int = 0,
        content: @Composable (PlayerWidgetUi) -> Unit,
    ) {
        if (dark) RuntimeEnvironment.setQualifiers("+night") else RuntimeEnvironment.setQualifiers("+notnight")
        val views = runBlocking {
            GlanceRemoteViews().compose(context, cells) {
                GlanceTheme(colors = KodaWidgetTheme.blockColors(context, ui.snapshot.artworkColor)) { content(ui) }
            }.remoteViews
        }
        // No title bar: the widget is all the picture holds.
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        val activity = controller.setup().get()
        val density = activity.resources.displayMetrics.density
        val w = (cells.width.value * density).toInt()
        val h = (cells.height.value * density).toInt()
        val pad = if (preview) 0 else (24 * density).toInt()
        val frame = FrameLayout(activity)
        // A wallpaper of sea and hills, so a widget is judged on what it sits on.
        frame.background = if (preview) {
            android.graphics.drawable.ColorDrawable(previewGround)
        } else {
            android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF8FA9B8.toInt(), 0xFF35607A.toInt(), 0xFF2F5A3A.toInt()),
            )
        }
        val view: View = views.apply(activity, frame)
        frame.addView(view, FrameLayout.LayoutParams(w, h).apply { setMargins(pad, pad, pad, pad) })
        activity.setContentView(frame, FrameLayout.LayoutParams(w + 2 * pad, h + 2 * pad))
        shadowOf(Looper.getMainLooper()).idle()
        frame.measure(
            View.MeasureSpec.makeMeasureSpec(w + 2 * pad, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h + 2 * pad, View.MeasureSpec.EXACTLY),
        )
        frame.layout(0, 0, w + 2 * pad, h + 2 * pad)
        val ground = if (previewGround == 0xFF000000.toInt()) "black" else "white"
        frame.captureRoboImage(if (preview) "build/outputs/roborazzi/preview_${name}_$ground.png" else "build/outputs/roborazzi/widget_$name.png")
    }

    // A cell on a 4-wide phone grid is about 80 x 120dp; a widget is n cells less the launcher's padding.
    private val w4h1 = DpSize(320.dp, 96.dp)
    private val w4h2 = DpSize(320.dp, 200.dp)
    private val w4h3 = DpSize(320.dp, 300.dp)
    private val w2h2 = DpSize(150.dp, 200.dp)

    @Test fun pulse_4x1() = shoot("pulse_4x1", w4h1, false, playing()) { PulseContent(it) }
    @Test fun pulse_4x2() = shoot("pulse_4x2", w4h2, false, playing()) { PulseContent(it) }
    @Test fun pulse_4x3() = shoot("pulse_4x3", w4h3, false, playing()) { PulseContent(it) }
    @Test fun pulse_4x3_dark() = shoot("pulse_4x3_dark", w4h3, true, playing(artworkColor = 0xFF3A7BD5.toInt(), art = cover(0xFF1D4E89.toInt(), 0xFFFFF1E0.toInt()))) { PulseContent(it) }
    @Test fun lineup_4x2() = shoot("lineup_4x2", w4h2, false, playing()) { LineupContent(it) }
    @Test fun lineup_4x3() = shoot("lineup_4x3", w4h3, false, playing(upNext = queue.take(2))) { LineupContent(it) }
    @Test fun lineup_4x3_dark() = shoot("lineup_4x3_dark", w4h3, true, playing()) { LineupContent(it) }
    @Test fun bloom_2x2() = shoot("bloom_2x2", w2h2, false, playing()) { BloomContent(it) }
    @Test fun orbit_2x2() = shoot("orbit_2x2", w2h2, false, playing()) { OrbitContent(it) }
    @Test fun lineup_empty() = shoot("lineup_empty", w4h2, false, PlayerWidgetUi.Empty) { LineupContent(it) }
    @Test fun pulse_empty_4x1() = shoot("pulse_empty_4x1", w4h1, true, PlayerWidgetUi.Empty) { PulseContent(it) }

    private val w4h4 = DpSize(320.dp, 400.dp)
    private val w2h1 = DpSize(150.dp, 96.dp)

    @Test fun blocks_4x2() = shoot("blocks_4x2", w4h2, false, PlayerWidgetUi.Empty) { BlocksContent("Midnight Rain") }
    @Test fun blocks_4x2_dark() = shoot("blocks_4x2_dark", w4h2, true, PlayerWidgetUi.Empty) { BlocksContent("Midnight Rain") }
    @Test fun blocks_4x1() = shoot("blocks_4x1", w4h1, false, PlayerWidgetUi.Empty) { BlocksContent(null) }
    @Test fun blocks_2x2() = shoot("blocks_2x2", w2h2, false, PlayerWidgetUi.Empty) { BlocksContent("Midnight Rain") }
    @Test fun blocks_4x4() = shoot("blocks_4x4", w4h4, false, PlayerWidgetUi.Empty) { BlocksContent("Midnight Rain") }

    // ------------------------------------------------------------ picker previews

    /** The cover every preview shows: Rondo's iris and orange, as the brand's record is. */
    private fun previewUi(color: Int, base: Int, accent: Int) = playing(
        artworkColor = color,
        art = cover(base, accent),
        isPlaying = false,
    ).let { ui ->
        ui.copy(
            snapshot = ui.snapshot.copy(
                title = context.getString(com.ivor.ivormusic.R.string.widget_preview_title),
                artist = context.getString(com.ivor.ivormusic.R.string.widget_preview_artist),
                album = null,
                upNext = queue.take(3),
            ),
        )
    }

    private val orange get() = previewUi(0xFFFF7A45.toInt(), 0xFFFF7A45.toInt(), 0xFFFFF1E0.toInt())
    private val iris get() = previewUi(0xFF5B4BFF.toInt(), 0xFF5B4BFF.toInt(), 0xFFFF7A45.toInt())
    private val orchid get() = previewUi(0xFFC77DF2.toInt(), 0xFF8E4FD8.toInt(), 0xFFFFF1E0.toInt())

    private fun preview(name: String, cells: DpSize, ui: PlayerWidgetUi, content: @Composable (PlayerWidgetUi) -> Unit) {
        shoot(name, cells, false, ui, preview = true, previewGround = 0xFF000000.toInt(), content = content)
        shoot(name, cells, false, ui, preview = true, previewGround = 0xFFFFFFFF.toInt(), content = content)
    }

    @Test fun preview_pulse() = preview("pulse", DpSize(320.dp, 96.dp), orange) { PulseContent(it) }
    @Test fun preview_lineup() = preview("lineup", DpSize(320.dp, 200.dp), iris) { LineupContent(it) }
    @Test fun preview_bloom() = preview("bloom", DpSize(160.dp, 160.dp), orange) { BloomContent(it) }
    @Test fun preview_orbit() = preview("orbit", DpSize(96.dp, 96.dp), iris) { OrbitContent(it) }
    @Test fun preview_pixel() = preview("pixel", DpSize(160.dp, 160.dp), iris) { PixelContent(it) }
    @Test fun preview_blocks() = preview("blocks", DpSize(320.dp, 200.dp), PlayerWidgetUi.Empty) { BlocksContent("Midnight Rain") }
}
