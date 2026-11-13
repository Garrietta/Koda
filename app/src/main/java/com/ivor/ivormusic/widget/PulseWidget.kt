package com.ivor.ivormusic.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.LocalContext
import androidx.glance.appwidget.SizeMode
import androidx.glance.LocalContext
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.LocalContext
import androidx.glance.layout.Column
import androidx.glance.LocalContext
import androidx.glance.layout.Row
import androidx.glance.LocalContext
import androidx.glance.layout.Spacer
import androidx.glance.LocalContext
import androidx.glance.layout.fillMaxSize
import androidx.glance.LocalContext
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.LocalContext
import androidx.glance.layout.padding
import androidx.glance.LocalContext
import androidx.glance.layout.width
import androidx.glance.LocalContext
import androidx.glance.text.FontWeight
import com.ivor.ivormusic.R

/**
 * Pulse: the everyday widget, and the only one that says what is playing in
 * words. A strip at 4x1, a now-playing card when given a second row.
 *
 * Short and tall cells use different layouts rather than one squeezed - a 4x1 has
 * room for a cover, two lines and a single control at a real touch target, and
 * five controls in that space would leave every one of them under 48dp.
 */
class PulseWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PlayerWidgetHost.ensureSeeded(context)
        provideContent {
            // Collected, not captured: provideGlance runs once per session
            // while updateAll only recomposes, so a value read out here would
            // never change again for the life of that session.
            val ui by PlayerWidgetHost.state.collectAsState()
            GlanceTheme(colors = KodaWidgetTheme.blockColors(context, ui.snapshot.artworkColor)) {
                PulseContent(ui)
            }
        }
    }
}

private val CARD_PADDING = 12.dp
private val COVER_GAP = 14.dp

@Composable
private fun PulseContent(ui: PlayerWidgetUi) {
    val size = LocalSize.current
    WidgetSurface {
        when {
            !ui.snapshot.hasMedia -> WidgetEmptyState()
            size.height >= 110.dp -> PulseCard(ui, size.width, size.height)
            else -> PulseStrip(ui, withNext = size.width >= 300.dp)
        }
    }
}

/** 4x1: cover, two lines, the hero, and next once the strip is wide enough. */
@Composable
private fun PulseStrip(ui: PlayerWidgetUi, withNext: Boolean) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = CARD_PADDING, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(ui.artwork, size = 48.dp, shape = ArtworkShape.COOKIE)
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            PulseTitle(ui.snapshot, fontSize = 14.sp, maxLines = 1)
            Spacer(modifier = GlanceModifier.height(1.dp))
            WidgetSubtitle(ui.snapshot.artist ?: "", fontSize = 12.sp)
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        HeroPlayButton(isPlaying = ui.snapshot.isPlaying, size = 48.dp)
        if (withNext) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            CardSquareButton(
                iconRes = R.drawable.ic_media_next,
                description = widgetString(R.string.widget_action_next),
                action = actionRunCallback<SkipNextAction>(),
                size = 44.dp,
            )
        }
    }
}

/**
 * 4x2: the cover as tall as the card allows, and a column beside it exactly as
 * tall as the cover - track at the top edge, transport on the bottom edge - so
 * the two blocks share a top and a baseline instead of floating.
 */
@Composable
private fun PulseCard(ui: PlayerWidgetUi, width: Dp, height: Dp) {
    val hero = 48.dp
    val side = 40.dp
    val gap = 6.dp
    val full = cardTransportWidth(hero, side, gap)
    // The cover takes the card's height. Where that leaves too little width
    // for previous, it gives up a little height to keep it - but only a
    // little, and never below what the column beside it needs to stand in
    // (a title, an artist and the 48dp hero, about 86dp). Past that, previous
    // is what goes.
    val byHeight = minOf(height - CARD_PADDING * 2, 128.dp)
    val withRoomForAll = width - CARD_PADDING * 2 - COVER_GAP - full
    val cover = when {
        withRoomForAll >= byHeight -> byHeight
        withRoomForAll >= byHeight - 16.dp && withRoomForAll >= 88.dp -> withRoomForAll
        else -> byHeight
    }
    val column = width - CARD_PADDING * 2 - COVER_GAP - cover
    val large = cover >= 110.dp

    Row(
        modifier = GlanceModifier.fillMaxSize().padding(CARD_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(ui.artwork, size = cover, shape = ArtworkShape.COOKIE)
        Spacer(modifier = GlanceModifier.width(COVER_GAP))
        Column(modifier = GlanceModifier.defaultWeight().height(cover)) {
            PulseTitle(
                ui.snapshot,
                fontSize = if (large) 16.sp else 15.sp,
                maxLines = if (large) 2 else 1,
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            WidgetSubtitle(ui.snapshot.artist ?: "", fontSize = if (large) 13.sp else 12.sp)
            Spacer(modifier = GlanceModifier.defaultWeight())
            CardTransportRow(
                ui.snapshot,
                heroSize = hero,
                sideSize = side,
                gap = gap,
                withPrevious = column >= full,
            )
        }
    }
}

@Composable
private fun PulseTitle(snapshot: PlayerWidgetSnapshot, fontSize: TextUnit, maxLines: Int) {
    WidgetTitle(
        snapshot.title ?: widgetString(R.string.app_name),
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        maxLines = maxLines,
    )
}

class PulseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PulseWidget()
}
