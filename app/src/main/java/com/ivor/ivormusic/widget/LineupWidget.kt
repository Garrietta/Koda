package com.ivor.ivormusic.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ivor.ivormusic.R

/**
 * Lineup: what is playing, and what is after it, each row a jump.
 *
 * The one widget that shows something the app keeps behind a sheet, and the
 * only one whose rows are targets rather than decoration. Rows address the
 * timeline by index and never by media id, because a queue can hold the same
 * track twice, and a jump goes through the app's own skip-to-index command so
 * it runs the crossfade path exactly like a tap in the queue sheet. The
 * timeline is the play order (shuffle rearranges it rather than layering an
 * order over it), so the next index is the next song even while shuffling.
 *
 * How many rows fit is derived from the cell rather than baked into a size
 * ([lineupPlan]), and capped at four: the queue's column has to stay inside a
 * Glance container's ten-child limit, past which it truncates silently.
 */
class LineupWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PlayerWidgetHost.ensureSeeded(context)
        provideContent {
            val ui by PlayerWidgetHost.state.collectAsState()
            GlanceTheme(colors = KodaWidgetTheme.blockColors(context, ui.snapshot.artworkColor)) {
                LineupContent(ui)
            }
        }
    }
}

/** The card's inner gutter; the list's corners are drawn concentric with it. */
private val GUTTER = 12.dp
private val HEADER_GAP = 10.dp
/** "Up next" and the space under it. */
private val LABEL = 20.dp
private val ROW_HEIGHT = 44.dp
private val THUMB = 32.dp
private val SEAM = 2.dp
private val HEADER_MIN = 52.dp
private val HEADER_MAX = 140.dp
private const val MAX_ROWS = 4

/**
 * How the card's height is shared: the queue gets as many rows as it has
 * songs and the cell has room for (at most four), and the now-playing
 * header takes the rest, up to a cover that would start to crowd the
 * text. A short queue in a tall cell is a big cover, not empty card.
 * "Up next" is dropped where leaving it out is what lets one more row in.
 */
internal data class LineupPlan(val rows: Int, val header: Dp, val label: Boolean)

internal fun lineupPlan(height: Dp, queued: Int): LineupPlan {
    val inner = height - GUTTER * 2
    fun listHeight(rows: Int, label: Boolean) =
        if (rows == 0) 0.dp else (if (label) LABEL else 0.dp) + ROW_HEIGHT * rows + SEAM * (rows - 1)
    fun rowsFor(label: Boolean): Int {
        var rows = queued.coerceIn(1, MAX_ROWS)
        while (rows > 0 && inner - HEADER_GAP - listHeight(rows, label) < HEADER_MIN) rows--
        return rows
    }
    val withLabel = rowsFor(label = true)
    val label = rowsFor(label = false) <= withLabel
    val rows = if (label) withLabel else rowsFor(label = false)
    val room = if (rows == 0) inner else inner - HEADER_GAP - listHeight(rows, label)
    return LineupPlan(rows, room.coerceIn(HEADER_MIN.coerceAtMost(inner), HEADER_MAX), label && rows > 0)
}

@Composable
internal fun LineupContent(ui: PlayerWidgetUi) {
    val snapshot = ui.snapshot
    val size = LocalSize.current

    WidgetSurface {
        if (!snapshot.hasMedia) {
            WidgetEmptyState()
            return@WidgetSurface
        }
        val plan = lineupPlan(size.height, snapshot.upNext.size)
        Column(modifier = GlanceModifier.fillMaxSize().padding(GUTTER)) {
            LineupHeader(ui, plan.header, size.width - GUTTER * 2)
            if (plan.rows > 0) {
                // The header holds the top edge and the queue the bottom one;
                // whatever the plan could not hand out sits between them.
                Spacer(modifier = GlanceModifier.defaultWeight())
                if (plan.label) Text(
                    text = widgetString(R.string.widget_up_next),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.height(LABEL).padding(start = 4.dp),
                ) else Spacer(modifier = GlanceModifier.height(HEADER_GAP))
                LineupList(snapshot.upNext.take(plan.rows), ui.upNextArt)
            }
        }
    }
}

/**
 * The song playing: its cover as large as the plan allows, and beside it
 * the title, the artist and the controls. A tall header stacks the
 * controls under the text, as Pulse does; a short one sets play and next
 * at the end of the row.
 */
@Composable
private fun LineupHeader(ui: PlayerWidgetUi, height: Dp, width: Dp) {
    val snapshot = ui.snapshot
    val tall = height >= 96.dp
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(ui.artwork, size = height, shape = ArtworkShape.COOKIE)
        Spacer(modifier = GlanceModifier.width(14.dp))
        if (tall) {
            val column = width - height - 14.dp
            Column(modifier = GlanceModifier.defaultWeight().height(height)) {
                // Two lines of title only where the controls still fit under them.
                WidgetTitle(
                    snapshot.title ?: widgetString(R.string.app_name),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (height >= 118.dp) 2 else 1,
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                WidgetSubtitle(snapshot.artist ?: "", fontSize = 13.sp)
                Spacer(modifier = GlanceModifier.defaultWeight())
                CardTransportRow(
                    snapshot,
                    heroSize = 48.dp,
                    sideSize = 40.dp,
                    gap = 6.dp,
                    withPrevious = column >= cardTransportWidth(48.dp, 40.dp, 6.dp),
                )
            }
        } else {
            Column(modifier = GlanceModifier.defaultWeight()) {
                WidgetTitle(
                    snapshot.title ?: widgetString(R.string.app_name),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = GlanceModifier.height(1.dp))
                WidgetSubtitle(snapshot.artist ?: "", fontSize = 12.sp)
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            HeroPlayButton(isPlaying = snapshot.isPlaying, size = 48.dp)
            if (width >= 280.dp) {
                Spacer(modifier = GlanceModifier.width(6.dp))
                CardSquareButton(
                    iconRes = R.drawable.ic_media_next,
                    description = widgetString(R.string.widget_action_next),
                    action = actionRunCallback<SkipNextAction>(),
                    size = 40.dp,
                )
            }
        }
    }
}

@Composable
private fun LineupList(entries: List<UpNextEntry>, art: Map<String, Bitmap>) {
    if (entries.isEmpty()) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .shaped(R.drawable.widget_shape_segment_single, GlanceTheme.colors.surfaceVariant)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = widgetString(R.string.widget_nothing_up_next),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        return
    }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        entries.forEachIndexed { position, entry ->
            if (position > 0) Spacer(modifier = GlanceModifier.height(SEAM))
            LineupRow(entry, entry.artworkUri?.let(art::get), segmentShape(position, entries.size))
        }
    }
}

@DrawableRes
private fun segmentShape(position: Int, count: Int): Int = when {
    count == 1 -> R.drawable.widget_shape_segment_single
    position == 0 -> R.drawable.widget_shape_segment_top
    position == count - 1 -> R.drawable.widget_shape_segment_bottom
    else -> R.drawable.widget_shape_segment_middle
}

/**
 * One song still to come, on a tonal step of the card: its cover, its
 * title and artist. The whole row is a jump to it.
 */
@Composable
private fun LineupRow(entry: UpNextEntry, cover: Bitmap?, @DrawableRes shape: Int) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .shaped(shape, GlanceTheme.colors.surfaceVariant)
            // The shape draws the row; this only clips its ripple on API 31+.
            // One radius between the 4dp seams and the 20dp ends: a ripple a
            // little rounder than a middle row stays inside it, and one a
            // little squarer than an end row barely shows past it.
            .cornerRadius(12.dp)
            .clickable(
                widgetTransportActionWithIndex(
                    WidgetActionActivity.ACTION_PLAY_INDEX,
                    entry.index
                )
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(cover, size = THUMB, shape = ArtworkShape.ROUNDED)
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = entry.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            if (entry.artist != null) {
                Text(
                    text = entry.artist,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

class LineupWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LineupWidget()
}
