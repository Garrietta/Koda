package com.ivor.ivormusic.ui.home

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Pause
import com.ivor.ivormusic.ui.theme.vividNeighbor
import com.ivor.ivormusic.ui.theme.grainGlow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song

/*
 * Blocks: Rondo's Home.
 *
 * The page is a few large flat tiles, each one a place to go, and color
 * is what tells them apart: artwork appears only as small accents inside
 * a tile. The large tiles are what gets opened every day (the DJ, For
 * you, Liked, New episodes); the small ones are for going deeper.
 *
 * Every color is a scheme role, so the tiles are Rondo's iris, orange,
 * cream and orchid on its own palette and follow whichever palette the
 * listener has chosen instead. A tile reports where it is on the screen
 * so the page it opens can grow out of it (BlockReveal).
 *
 * This file only draws. What a tile opens is the caller's business, so
 * the whole page can be rendered from plain lists in a test.
 */

/** Everything on Home that can be opened. */
internal enum class BlockKind { Dj, ForYou, Liked, Episodes, Recent, QuickPicks, Playlists, Artists, Albums, Charts, NewReleases, Explore, Downloads }

/**
 * The name a home screen widget opens this block by. It is written into the
 * intent of every placed widget, so these are fixed: renaming one leaves an
 * old widget's tile opening nothing.
 */
internal val BlockKind.widgetKey: String
    get() = when (this) {
        BlockKind.Dj -> "dj"
        BlockKind.ForYou -> "for_you"
        BlockKind.Liked -> "liked"
        BlockKind.Episodes -> "episodes"
        BlockKind.Recent -> "recent"
        BlockKind.QuickPicks -> "quick_picks"
        BlockKind.Playlists -> "playlists"
        BlockKind.Artists -> "artists"
        BlockKind.Albums -> "albums"
        BlockKind.Charts -> "charts"
        BlockKind.NewReleases -> "new"
        BlockKind.Explore -> "explore"
        BlockKind.Downloads -> "downloads"
    }

internal fun blockKindForWidgetKey(key: String): BlockKind? = BlockKind.entries.firstOrNull { it.widgetKey == key }

/** The color a block has on Home, and so the color of the page it opens. */
internal fun BlockKind.colors(scheme: ColorScheme): Pair<Color, Color> = when (this) {
    BlockKind.Dj, BlockKind.QuickPicks, BlockKind.Explore -> scheme.primaryContainer to scheme.onPrimaryContainer
    BlockKind.ForYou, BlockKind.NewReleases, BlockKind.Albums -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    BlockKind.Episodes, BlockKind.Charts, BlockKind.Artists, BlockKind.Downloads -> scheme.secondaryContainer to scheme.onSecondaryContainer
    BlockKind.Liked, BlockKind.Playlists -> scheme.inverseSurface to scheme.inverseOnSurface
    BlockKind.Recent -> scheme.surfaceContainer to scheme.onSurface
}

private val Tile = 40.dp
private val Tight = 14.dp

/** Heights of the bars in the DJ tile's level meter, left to right. */
private val METER = listOf(18, 40, 28, 52, 34, 22, 44)

/**
 * Each tile's face, as it was last drawn, kept in a layer the tile itself
 * records into, so a page opening from the tile can draw the tile over
 * itself while it grows out from under it. Also where each tile is.
 */
@androidx.compose.runtime.Stable
class BlockFaces internal constructor(private val layers: Map<BlockKind, androidx.compose.ui.graphics.layer.GraphicsLayer>) {
    internal val bounds = mutableMapOf<BlockKind, Rect>()

    /** Each tile's own outline, so a page closing into a tile takes the tile's shape as it lands. */
    internal val shapes = mutableMapOf<BlockKind, Shape>()
    internal fun layer(kind: BlockKind) = layers[kind]

    /** The outline of the tile at [rect]. */
    internal fun shapeAt(rect: Rect?) = rect?.let { r -> bounds.entries.firstOrNull { it.value == r }?.key?.let(shapes::get) }

    /** The face of the tile at [rect], the tile a page opened from. */
    internal fun faceAt(rect: Rect?) = rect?.let { r -> bounds.entries.firstOrNull { it.value == r }?.key?.let(layers::get) }
}

@Composable
fun rememberBlockFaces(): BlockFaces {
    val layers = BlockKind.entries.associateWith { androidx.compose.ui.graphics.rememberGraphicsLayer() }
    return remember { BlockFaces(layers) }
}

@Composable
internal fun BlocksHome(
    likedCount: Int,
    newEpisodes: List<Song>,
    recent: List<Song>,
    onOpen: (BlockKind) -> Unit,
    onRecent: (Song) -> Unit,
    modifier: Modifier = Modifier,
    /** Where each block is on the screen, in root pixels, as it is laid out. */
    onBounds: (BlockKind, Rect) -> Unit = { _, _ -> },
    /** The DJ's colors, from its current mix. Null keeps the scheme's own. */
    djColors: com.ivor.ivormusic.ui.theme.VividBlock? = null,
    /** The current mix's name, shown on the DJ tile. Null shows what the DJ does. */
    djTitle: String? = null,
    /**
     * Lay the blocks out as steps ([BlockSteps]) where the screen is a phone's
     * width: two in view, a swipe turning one over. Needs a bounded height.
     */
    stepped: Boolean = false,
    /** What is playing, for the Now playing block that ends the steps; null leaves it out. */
    nowPlaying: NowPlaying? = null,
    /** Where the steps stand, shared with the screen that hosts the now-playing bar. */
    steps: HomeSteps? = null,
    /** The now-playing bar and the gap above it, which the last block grows over. */
    barReserve: Dp = STEP_BAR + STEP_GAP,
    /** Where the tiles record their faces for the pages that open from them; null records none. */
    faces: BlockFaces? = null,
    /**
     * Step the blocks in their unfolded form: wider blocks that show more of
     * what each holds (For you and Liked show covers, New episodes lists the
     * newest, Jump back in names each cover), with no Now playing block,
     * since the unfolded Home has its panel for that.
     */
    wideSteps: Boolean = false,
    /** A few songs For you, shown on its tile when the blocks step unfolded. */
    forYou: List<Song> = emptyList(),
    /** The latest liked songs, shown on the Liked tile when the blocks step unfolded. */
    liked: List<Song> = emptyList(),
    /** Plays a song shown on a tile, from the list it is shown in: a cover on For you or Liked, a row of New episodes. */
    onPlay: (List<Song>, Song) -> Unit = { _, song -> onRecent(song) },
) {
    val scheme = MaterialTheme.colorScheme
    val djFill = djColors?.container ?: scheme.primaryContainer
    val djInk = djColors?.onContainer ?: scheme.onPrimaryContainer
    val djGlow = remember(djFill) { djFill.vividNeighbor()?.let { androidx.compose.ui.graphics.lerp(djFill, it, 0.8f) } }
    fun Modifier.block(kind: BlockKind, shape: Shape, color: Color, glow: Color? = null): Modifier = this
        .onGloballyPositioned {
            onBounds(kind, it.boundsInRoot())
            faces?.bounds?.set(kind, it.boundsInRoot())
            faces?.shapes?.set(kind, shape)
        }
        .then(
            faces?.layer(kind)?.let { layer ->
                Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
            } ?: Modifier,
        )
        .clip(shape)
        .then(
            if (glow != null) {
                Modifier.grainGlow(hot = glow, ground = color, from = androidx.compose.ui.geometry.Offset(0.92f, 1f), reach = 0.95f)
            } else {
                Modifier.background(color)
            },
        )
        .clickable(role = Role.Button) { onOpen(kind) }

    // The pieces of Home, each a function of its size, so a phone stacks
    // them in one column and an unfolded screen sets them in two.
    val djTile: @Composable (Modifier) -> Unit = { size ->
        Box(
            size
                .block(
                    BlockKind.Dj,
                    RoundedCornerShape(topStart = Tile, topEnd = Tile, bottomEnd = Tile, bottomStart = Tight),
                    djFill,
                    // Home's one bright thing: the DJ glows from its meter toward
                    // the hue beside its own, with grain over it.
                    glow = djGlow,
                ),
        ) {
            Text(
                stringResource(R.string.blocks_dj),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp),
                color = djInk,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 12.dp),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(scheme.inverseSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = scheme.inverseOnSurface,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                djTitle ?: stringResource(R.string.blocks_dj_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = djInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 18.dp).width(180.dp),
            )
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                for (bar in METER) {
                    Box(Modifier.width(7.dp).height(bar.dp).clip(CircleShape).background(djInk))
                }
            }
        }

    }

    // For you, with Liked and New episodes stacked beside it.
    val pairTiles: @Composable (Dp) -> Unit = { height ->
        val half = (height - 10.dp) / 2
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .height(height)
                    .block(
                        BlockKind.ForYou,
                        RoundedCornerShape(topStart = Tile, topEnd = Tile, bottomEnd = Tight, bottomStart = Tile),
                        scheme.tertiaryContainer,
                    )
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = scheme.onTertiaryContainer,
                    modifier = Modifier.size(56.dp),
                )
                BlockLabel(
                    stringResource(R.string.filter_recommended),
                    stringResource(R.string.blocks_for_you_caption),
                    scheme.onTertiaryContainer,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(half)
                        .block(BlockKind.Liked, CircleShape, scheme.inverseSurface)
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = scheme.inverseOnSurface,
                        modifier = Modifier.size(28.dp),
                    )
                    BlockLabel(
                        stringResource(R.string.filter_liked),
                        if (likedCount > 0) {
                            stringResource(R.string.blocks_liked_count, likedCount)
                        } else {
                            stringResource(R.string.blocks_liked_empty)
                        },
                        scheme.inverseOnSurface,
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(half)
                        .block(
                            BlockKind.Episodes,
                            RoundedCornerShape(topStart = Tight, topEnd = Tile, bottomEnd = Tile, bottomStart = Tile),
                            scheme.secondaryContainer,
                        )
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Icon(
                        Icons.Rounded.Podcasts,
                        contentDescription = null,
                        tint = scheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                    BlockLabel(
                        stringResource(R.string.home_section_new_episodes),
                        if (newEpisodes.isNotEmpty()) {
                            stringResource(R.string.blocks_episodes_count, newEpisodes.size)
                        } else {
                            stringResource(R.string.blocks_episodes_empty)
                        },
                        scheme.onSecondaryContainer,
                    )
                }
            }
        }
    }

        // The last few things played, as covers in four different shapes. A tap plays one.
    val recentPanel: @Composable (Dp, Boolean) -> Unit = { coverHeight, fill ->
        if (recent.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (fill) Modifier.fillMaxHeight() else Modifier)
                    .onGloballyPositioned { onBounds(BlockKind.Recent, it.boundsInRoot()) }
                    .clip(RoundedCornerShape(32.dp))
                    .background(scheme.surfaceContainer)
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = if (fill) Arrangement.SpaceBetween else Arrangement.Top,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.home_section_jump_back_in),
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onOpen(BlockKind.Recent) }) { Text(stringResource(R.string.action_see_all)) }
                }
                Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val shown = recent.take(COVER_SHAPES.size)
                    shown.forEachIndexed { index, song ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(coverHeight)
                                .clip(COVER_SHAPES[index])
                                .clickable(onClickLabel = song.title, role = Role.Button) { onRecent(song) },
                        ) {
                            BlockCover(song)
                        }
                    }
                    // Fewer than four keeps the covers the size they would be in a full row.
                    repeat(COVER_SHAPES.size - shown.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    // For going deeper: each a badge in the color its page will be, on a plain tile.
    val smallTiles: @Composable (List<SmallBlock>, Dp, Int) -> Unit = { blocks, rowHeight, perRow ->
        for (pair in blocks.chunked(perRow)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (small in pair) {
                    val (badge, onBadge) = small.kind.colors(scheme)
                    val badgeBox: @Composable () -> Unit = {
                        Box(
                            Modifier.size(44.dp).clip(small.badgeShape).background(badge),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(small.icon, contentDescription = null, tint = onBadge, modifier = Modifier.size(24.dp))
                        }
                    }
                    val label: @Composable () -> Unit = {
                        Text(
                            stringResource(small.label),
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val tile = Modifier
                        .weight(1f)
                        .height(rowHeight)
                        .block(small.kind, RoundedCornerShape(28.dp), scheme.surfaceContainer)
                    if (perRow > 2) {
                        // Four to a row: too narrow for the name beside the
                        // badge, so it sits under it.
                        Column(
                            tile.padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            badgeBox()
                            label()
                        }
                    } else {
                        Row(
                            tile.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            badgeBox()
                            label()
                        }
                    }
                }
            }
        }
    }


    // ---- Unfolded steps: the same blocks, wider, each showing more ----

    // A row of covers across a tile, as many as fit at [minCover], at most
    // [most], each as large as the room left allows, named under it when
    // [ink] is given, and a tap playing it from [songs].
    val coverRow: @Composable (List<Song>, Int, Dp, Color?) -> Unit = { songs, most, minCover, ink ->
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            val fit = ((maxWidth + 10.dp) / (minCover + 10.dp)).toInt().coerceIn(1, most)
            val label = if (ink != null) 26.dp else 0.dp
            val cover = minOf((maxWidth - 10.dp * (fit - 1)) / fit, maxHeight - label).coerceAtLeast(0.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                songs.take(fit).forEachIndexed { index, song ->
                    Column(
                        Modifier
                            .width(cover)
                            .clickable(onClickLabel = song.title, role = Role.Button) { onPlay(songs, song) },
                    ) {
                        Box(
                            Modifier
                                .size(cover)
                                .clip(COVER_SHAPES[index % COVER_SHAPES.size]),
                        ) { BlockCover(song) }
                        if (ink != null) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // For you, with a few of its songs.
    val forYouWide: @Composable (Modifier) -> Unit = { size ->
        Column(
            size
                .block(
                    BlockKind.ForYou,
                    RoundedCornerShape(topStart = Tile, topEnd = Tile, bottomEnd = Tight, bottomStart = Tile),
                    scheme.tertiaryContainer,
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(40.dp))
                BlockLabel(
                    stringResource(R.string.filter_recommended),
                    stringResource(R.string.blocks_for_you_caption),
                    scheme.onTertiaryContainer,
                )
            }
            if (forYou.isNotEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                    coverRow(forYou, 3, 72.dp, scheme.onTertiaryContainer)
                }
            }
        }
    }

    // Liked, with the latest songs liked.
    val likedWide: @Composable (Modifier) -> Unit = { size ->
        Column(
            size
                .block(BlockKind.Liked, RoundedCornerShape(Tile), scheme.inverseSurface)
                .padding(18.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = scheme.inverseOnSurface, modifier = Modifier.size(32.dp))
                BlockLabel(
                    stringResource(R.string.filter_liked),
                    if (likedCount > 0) {
                        stringResource(R.string.blocks_liked_count, likedCount)
                    } else {
                        stringResource(R.string.blocks_liked_empty)
                    },
                    scheme.inverseOnSurface,
                )
            }
            if (liked.isNotEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                    coverRow(liked, 4, 64.dp, null)
                }
            }
        }
    }

    // New episodes, listing the newest as many as fit.
    val episodesWide: @Composable (Modifier) -> Unit = { size ->
        androidx.compose.foundation.layout.BoxWithConstraints(
            size.block(
                BlockKind.Episodes,
                RoundedCornerShape(topStart = Tight, topEnd = Tile, bottomEnd = Tile, bottomStart = Tile),
                scheme.secondaryContainer,
            ),
        ) {
            val rows = ((maxHeight - 96.dp) / 56.dp).toInt().coerceIn(0, 4)
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Podcasts, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
                    BlockLabel(
                        stringResource(R.string.home_section_new_episodes),
                        if (newEpisodes.isNotEmpty()) {
                            stringResource(R.string.blocks_episodes_count, newEpisodes.size)
                        } else {
                            stringResource(R.string.blocks_episodes_empty)
                        },
                        scheme.onSecondaryContainer,
                    )
                }
                for (episode in newEpisodes.take(rows)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClickLabel = episode.title, role = Role.Button) { onPlay(newEpisodes, episode) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))) { BlockCover(episode) }
                        Column(Modifier.weight(1f)) {
                            Text(
                                episode.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                episode.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    // Jump back in across the width, each cover named, a tap playing it.
    val recentWide: @Composable () -> Unit = {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { onBounds(BlockKind.Recent, it.boundsInRoot()) }
                .clip(RoundedCornerShape(32.dp))
                .background(scheme.surfaceContainer),
        ) {
            // As many covers as fit, square, with room for the header and two lines under them.
            val inner = maxWidth - 32.dp
            val byHeight = maxHeight - 56.dp - 52.dp
            val count = ((inner + 12.dp) / (100.dp + 12.dp)).toInt().coerceIn(3, 6)
            val cover = minOf((inner - 12.dp * (count - 1)) / count, byHeight).coerceAtLeast(0.dp)
            Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.home_section_jump_back_in),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onOpen(BlockKind.Recent) }) { Text(stringResource(R.string.action_see_all)) }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    recent.take(count).forEachIndexed { index, song ->
                        Column(
                            Modifier
                                .width(cover)
                                .clickable(onClickLabel = song.title, role = Role.Button) { onRecent(song) },
                        ) {
                            Box(Modifier.size(cover).clip(COVER_SHAPES[index % COVER_SHAPES.size])) { BlockCover(song) }
                            Text(
                                song.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(if (stepped) modifier.fillMaxSize() else modifier.fillMaxWidth()) {
        if (wideSteps && stepped && steps != null && constraints.hasBoundedHeight) {
            // Unfolded: the same steps, two blocks in view, each block a
            // wider row that shows more of what its tiles hold.
            val wideBlocks = buildList<@Composable () -> Unit> {
                add {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(STEP_GAP)) {
                        djTile(Modifier.weight(1.3f).fillMaxHeight())
                        forYouWide(Modifier.weight(1f).fillMaxHeight())
                    }
                }
                add {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(STEP_GAP)) {
                        likedWide(Modifier.weight(1f).fillMaxHeight())
                        episodesWide(Modifier.weight(1f).fillMaxHeight())
                    }
                }
                if (recent.isNotEmpty()) add { recentWide() }
                add {
                    androidx.compose.foundation.layout.BoxWithConstraints {
                        val row = (maxHeight - STEP_GAP) / 2
                        Column(verticalArrangement = Arrangement.spacedBy(STEP_GAP)) {
                            smallTiles(SMALL_BLOCKS, row, 4)
                        }
                    }
                }
            }
            BlockSteps(
                steps = steps,
                count = wideBlocks.size,
                endsWithBar = false,
                barReserve = 0.dp,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp),
            ) { index, _ -> wideBlocks.getOrNull(index)?.invoke() }
        } else if (maxWidth >= WIDE_HOME) {
            // Unfolded: two columns side by side, so the whole width is used
            // rather than one column stretched across it.
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    djTile(Modifier.fillMaxWidth().height(200.dp))
                    pairTiles(300.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    recentPanel(128.dp, false)
                    smallTiles(SMALL_BLOCKS, 72.dp, 2)
                }
            }
        } else if (stepped && steps != null && constraints.hasBoundedHeight) {
            // A phone: the blocks as steps, two in view above the bar, each
            // filling the height the stepper gives it.
            val blocks = buildList<@Composable (() -> Float) -> Unit> {
                add { _ -> djTile(Modifier.fillMaxSize()) }
                add { _ -> androidx.compose.foundation.layout.BoxWithConstraints { pairTiles(maxHeight) } }
                // Jump back in fills its slot: larger covers, the row at its foot.
                if (recent.isNotEmpty()) add { _ ->
                    androidx.compose.foundation.layout.BoxWithConstraints { recentPanel((maxHeight - 76.dp).coerceIn(56.dp, 160.dp), true) }
                }
                add { _ ->
                    androidx.compose.foundation.layout.BoxWithConstraints {
                        val row = (maxHeight - STEP_GAP) / 2
                        Column(verticalArrangement = Arrangement.spacedBy(STEP_GAP)) {
                            smallTiles(SMALL_BLOCKS.take(4), row, 2)
                        }
                    }
                }
                add { _ ->
                    androidx.compose.foundation.layout.BoxWithConstraints {
                        val row = (maxHeight - STEP_GAP) / 2
                        Column(verticalArrangement = Arrangement.spacedBy(STEP_GAP)) {
                            smallTiles(SMALL_BLOCKS.drop(4), row, 2)
                        }
                    }
                }
                nowPlaying?.let { playing -> add { morph -> NowPlayingBlock(playing, morph) } }
            }
            BlockSteps(
                steps = steps,
                count = blocks.size,
                endsWithBar = nowPlaying != null,
                barReserve = barReserve,
                modifier = Modifier.padding(horizontal = 14.dp),
            ) { index, morph -> blocks.getOrNull(index)?.invoke(morph) }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                djTile(Modifier.fillMaxWidth().height(172.dp))
                pairTiles(230.dp)
                recentPanel(72.dp, false)
                smallTiles(SMALL_BLOCKS, 72.dp, 2)
            }
        }
    }
}

/** The song playing, for the Now playing block that ends Home's steps. */
class NowPlaying(
    val song: Song,
    val isPlaying: Boolean,
    val onToggle: () -> Unit,
    val onNext: () -> Unit,
    val onOpen: () -> Unit,
)

/**
 * What is playing, as the last of Home's steps: the cover large, in the
 * cover's own vivid color, with the title, the artist, and play and next.
 * A tap anywhere else opens the player.
 *
 * It grows out of the now-playing bar ([morph] 0 is the bar's rectangle,
 * 1 the block): the ground turns from the bar's color to the cover's and
 * the contents come up as it opens, so the bar reads as turning into it.
 */
@Composable
private fun NowPlayingBlock(playing: NowPlaying, morph: () -> Float) {
    val scheme = MaterialTheme.colorScheme
    val song = playing.song
    val art = song.albumArtUri?.toString() ?: song.highResThumbnailUrl ?: song.thumbnailUrl
    val seed = com.ivor.ivormusic.ui.player.rememberArtworkSeed(enabled = art != null, albumArtUri = art)
    val block = seed?.let { com.ivor.ivormusic.ui.theme.vividBlockOf(it) }
    val ground = block?.container ?: scheme.surfaceContainerHigh
    val ink = block?.onContainer ?: scheme.onSurface
    val barColor = scheme.primaryContainer
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = Tile, topEnd = Tile, bottomEnd = Tile, bottomStart = Tight))
            .drawBehind { drawRect(androidx.compose.ui.graphics.lerp(barColor, ground, morph())) }
            .clickable(onClickLabel = song.title, role = Role.Button, onClick = playing.onOpen)
            .padding(14.dp)
            .graphicsLayer { alpha = (morph() * 1.6f - 0.6f).coerceIn(0f, 1f) },
    ) {
        // The cover as tall as the block, but never more than two fifths of its
        // width, so the words beside it keep a line.
        val cover = minOf(maxHeight, maxWidth * 0.42f).coerceAtLeast(0.dp)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(cover).clip(RoundedCornerShape(28.dp))) { BlockCover(song) }
            Spacer(Modifier.width(16.dp))
            // The words and the buttons as one group, centred beside the cover.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text(stringResource(R.string.blocks_now_playing), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.8f))
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.FilledIconButton(
                        onClick = playing.onToggle,
                        modifier = Modifier.size(56.dp),
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = ink, contentColor = ground),
                    ) {
                        Icon(
                            if (playing.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(if (playing.isPlaying) R.string.widget_action_pause else R.string.widget_action_play),
                        )
                    }
                    androidx.compose.material3.FilledTonalIconButton(
                        onClick = playing.onNext,
                        modifier = Modifier.size(48.dp),
                        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = androidx.compose.ui.graphics.lerp(ground, ink, 0.16f),
                            contentColor = ink,
                        ),
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.widget_action_next))
                    }
                }
            }
        }
    }
}

/** From this width Home is two columns side by side, and never steps. */
internal val WIDE_HOME = 600.dp

private val COVER_SHAPES: List<Shape> = listOf(
    RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 8.dp),
    CircleShape,
    RoundedCornerShape(topStart = 22.dp, topEnd = 8.dp, bottomEnd = 22.dp, bottomStart = 22.dp),
    RoundedCornerShape(22.dp),
)

private val LeafBadge = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 6.dp)
private val SquareBadge = RoundedCornerShape(16.dp)

private class SmallBlock(val kind: BlockKind, val icon: ImageVector, val label: Int, val badgeShape: Shape)

private val SMALL_BLOCKS = listOf(
    SmallBlock(BlockKind.Playlists, Icons.AutoMirrored.Rounded.QueueMusic, R.string.filter_playlists, SquareBadge),
    SmallBlock(BlockKind.Artists, Icons.Rounded.Person, R.string.section_artists, CircleShape),
    SmallBlock(BlockKind.Albums, Icons.Rounded.Album, R.string.section_albums, LeafBadge),
    SmallBlock(BlockKind.Charts, Icons.Rounded.BarChart, R.string.filter_charts, SquareBadge),
    SmallBlock(BlockKind.QuickPicks, Icons.Rounded.Shuffle, R.string.spotlight_quick_picks, LeafBadge),
    SmallBlock(BlockKind.NewReleases, Icons.Rounded.NewReleases, R.string.filter_new_releases, CircleShape),
    SmallBlock(BlockKind.Explore, Icons.Rounded.Explore, R.string.filter_explore, SquareBadge),
    SmallBlock(BlockKind.Downloads, Icons.Rounded.Download, R.string.cd_downloads, LeafBadge),
)

@Composable
private fun BlockLabel(title: String, caption: String, color: Color) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
        Text(
            caption,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun BlockCover(song: Song) {
    com.ivor.ivormusic.ui.components.VideoThumbnail(
        thumbnailUrl = song.albumArtUri?.toString() ?: song.thumbnailUrl,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        highResThumbnailUrl = song.albumArtUri?.toString() ?: song.highResThumbnailUrl,
        contentScale = ContentScale.Crop,
        indicatorSize = 16.dp,
        emptyIcon = Icons.AutoMirrored.Rounded.QueueMusic,
        placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}
