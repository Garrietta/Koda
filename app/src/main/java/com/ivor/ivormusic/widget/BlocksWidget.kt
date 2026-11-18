package com.ivor.ivormusic.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ivor.ivormusic.R
import com.ivor.ivormusic.ui.home.BlockKind

/**
 * Blocks: Home's tiles on the home screen. The DJ, For you, Liked and New
 * episodes, each in its Home color and wearing its Home face, and each a
 * door: a tap opens Rondo on that tile's page, grown out of the tile there
 * as a tap on Home grows it.
 *
 * The tiles sit straight on the wallpaper with gaps between them, as they
 * sit on Home's ground, rather than inside a card of their own. How many
 * there are and how they are arranged follows the cell: a row of four at
 * 4x1, the DJ over two at 2x2, the DJ over three at 4x2, and Home's own
 * arrangement - the DJ, For you tall, Liked and New episodes beside it -
 * from 4x3.
 */
class BlocksWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // The name of the mix the DJ last made, as the DJ tile on Home shows it.
        val djTitle = runCatching { com.ivor.ivormusic.data.AiDjRepository(context).reloadCachedMix()?.title }
            .getOrNull()
        provideContent {
            GlanceTheme(colors = KodaWidgetTheme.colors(context)) {
                BlocksContent(djTitle)
            }
        }
    }
}

private val GAP = 8.dp

@Composable
internal fun BlocksContent(djTitle: String?) {
    val size = LocalSize.current
    val context = LocalContext.current
    val tiles = remember(context) { KodaWidgetTheme.blockTiles(context) }
    Box(modifier = GlanceModifier.fillMaxSize()) {
        when {
            size.height < 130.dp -> Row(modifier = GlanceModifier.fillMaxSize()) {
                DjTile(tiles, djTitle, compact = true, modifier = GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(modifier = GlanceModifier.width(GAP))
                SmallTile(tiles, BlockKind.ForYou, modifier = GlanceModifier.defaultWeight().fillMaxHeight(), iconOnly = true)
                Spacer(modifier = GlanceModifier.width(GAP))
                SmallTile(tiles, BlockKind.Liked, modifier = GlanceModifier.defaultWeight().fillMaxHeight(), iconOnly = true)
            }
            size.width < 250.dp -> Column(modifier = GlanceModifier.fillMaxSize()) {
                DjTile(tiles, djTitle, compact = true, modifier = GlanceModifier.fillMaxWidth().defaultWeight())
                Spacer(modifier = GlanceModifier.height(GAP))
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    SmallTile(tiles, BlockKind.ForYou, modifier = GlanceModifier.defaultWeight().fillMaxHeight(), iconOnly = true)
                    Spacer(modifier = GlanceModifier.width(GAP))
                    SmallTile(tiles, BlockKind.Liked, modifier = GlanceModifier.defaultWeight().fillMaxHeight(), iconOnly = true)
                }
            }
            size.height < 260.dp -> Column(modifier = GlanceModifier.fillMaxSize()) {
                DjTile(tiles, djTitle, compact = false, modifier = GlanceModifier.fillMaxWidth().defaultWeight())
                Spacer(modifier = GlanceModifier.height(GAP))
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    SmallTile(tiles, BlockKind.ForYou, modifier = GlanceModifier.defaultWeight().fillMaxHeight())
                    Spacer(modifier = GlanceModifier.width(GAP))
                    SmallTile(tiles, BlockKind.Liked, modifier = GlanceModifier.defaultWeight().fillMaxHeight())
                    Spacer(modifier = GlanceModifier.width(GAP))
                    SmallTile(tiles, BlockKind.Episodes, modifier = GlanceModifier.defaultWeight().fillMaxHeight())
                }
            }
            else -> Column(modifier = GlanceModifier.fillMaxSize()) {
                DjTile(tiles, djTitle, compact = false, large = true, modifier = GlanceModifier.fillMaxWidth().height(size.height * 0.42f))
                Spacer(modifier = GlanceModifier.height(GAP))
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    SmallTile(tiles, BlockKind.ForYou, modifier = GlanceModifier.defaultWeight().fillMaxHeight(), withCaption = true)
                    Spacer(modifier = GlanceModifier.width(GAP))
                    Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                        SmallTile(tiles, BlockKind.Liked, modifier = GlanceModifier.fillMaxWidth().defaultWeight(), pill = true)
                        Spacer(modifier = GlanceModifier.height(GAP))
                        SmallTile(tiles, BlockKind.Episodes, modifier = GlanceModifier.fillMaxWidth().defaultWeight(), withCaption = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> remember(key: Any, calculation: () -> T): T = androidx.compose.runtime.remember(key, calculation)

/** A tile: its color in its shape, the whole of it a tap to its page. */
@Composable
private fun Tile(
    tiles: Map<BlockKind, WidgetBlockTile>,
    kind: BlockKind,
    modifier: GlanceModifier,
    @DrawableRes shape: Int = WidgetShape.Tile,
    padding: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val tile = tiles.getValue(kind)
    Box(
        modifier = modifier
            .shaped(shape, tile.ground)
            .cornerRadius(24.dp)
            .clickable(openHomeBlockAction(LocalContext.current, kind))
            .padding(padding),
    ) { content() }
}

/**
 * The DJ: its name large with the mix's name under it, and beside them
 * the level meter Home's DJ tile has, in the iris of Rondo's brand.
 */
@Composable
private fun DjTile(tiles: Map<BlockKind, WidgetBlockTile>, title: String?, compact: Boolean, modifier: GlanceModifier, large: Boolean = false) {
    val ink = tiles.getValue(BlockKind.Dj).ink
    val name = when {
        large -> 44.sp
        compact -> 24.sp
        else -> 32.sp
    }
    Tile(tiles, BlockKind.Dj, modifier, padding = if (compact) 12.dp else 16.dp) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                Text(
                    text = widgetString(R.string.blocks_dj),
                    style = TextStyle(color = ink, fontSize = name, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = title ?: widgetString(R.string.widget_blocks_dj_caption),
                    style = TextStyle(color = ink, fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            LevelMeter(ink, if (large) 40.dp else if (compact) 18.dp else 28.dp)
        }
    }
}

/** Heights of the meter's bars, as fractions of its height, left to right: Home's DJ tile's, scaled. */
private val METER = listOf(0.35f, 0.77f, 0.54f, 1f, 0.65f, 0.42f, 0.85f)

@Composable
private fun LevelMeter(ink: ColorProvider, height: Dp) {
    val bar = (height.value / 7.5f).dp.coerceAtLeast(3.dp)
    Row(modifier = GlanceModifier.height(height), verticalAlignment = Alignment.Bottom) {
        METER.forEachIndexed { index, level ->
            if (index > 0) Spacer(modifier = GlanceModifier.width(bar * 0.7f))
            Box(modifier = GlanceModifier.width(bar).height(height * level).shaped(WidgetShape.Full, ink)) {}
        }
    }
}

/** For you, Liked or New episodes: its glyph, its name, and a line about it where there is room. */
@Composable
private fun SmallTile(
    tiles: Map<BlockKind, WidgetBlockTile>,
    kind: BlockKind,
    modifier: GlanceModifier,
    iconOnly: Boolean = false,
    withCaption: Boolean = false,
    pill: Boolean = false,
) {
    val ink = tiles.getValue(kind).ink
    val (glyph, label) = when (kind) {
        BlockKind.ForYou -> R.drawable.widget_glyph_sparkles to R.string.filter_recommended
        BlockKind.Liked -> R.drawable.widget_glyph_heart to R.string.filter_liked
        else -> R.drawable.widget_glyph_broadcast to
            if (withCaption || pill || LocalSize.current.width >= 400.dp) R.string.home_section_new_episodes else R.string.widget_blocks_episodes
    }
    Tile(tiles, kind, modifier, shape = if (pill) WidgetShape.Full else WidgetShape.Tile, padding = if (pill) 18.dp else 14.dp) {
        if (iconOnly) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Glyph(glyph, ink, 30.dp)
            }
        } else if (pill) {
            Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Glyph(glyph, ink, 26.dp)
                Spacer(modifier = GlanceModifier.width(10.dp))
                TileLabel(widgetString(label), ink, 16.sp)
            }
        } else {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Glyph(glyph, ink, if (withCaption) 34.dp else 24.dp)
                Spacer(modifier = GlanceModifier.defaultWeight())
                TileLabel(widgetString(label), ink, if (withCaption) 17.sp else 14.sp)
                if (withCaption && kind == BlockKind.ForYou) {
                    Text(
                        text = widgetString(R.string.blocks_for_you_caption),
                        style = TextStyle(color = ink, fontSize = 12.sp),
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun Glyph(@DrawableRes res: Int, ink: ColorProvider, size: Dp) {
    Image(
        provider = ImageProvider(res),
        contentDescription = null,
        colorFilter = ColorFilter.tint(ink),
        modifier = GlanceModifier.size(size),
    )
}

@Composable
private fun TileLabel(text: String, ink: ColorProvider, fontSize: TextUnit) {
    Text(text = text, style = TextStyle(color = ink, fontSize = fontSize, fontWeight = FontWeight.Bold), maxLines = 1)
}

class BlocksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BlocksWidget()
}
