package com.ivor.ivormusic.ui.home

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
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
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
internal enum class BlockKind { Dj, ForYou, Liked, Episodes, Recent, QuickPicks, Playlists, Library, Charts, NewReleases, Explore }

/** The color a block has on Home, and so the color of the page it opens. */
internal fun BlockKind.colors(scheme: ColorScheme): Pair<Color, Color> = when (this) {
    BlockKind.Dj, BlockKind.QuickPicks, BlockKind.Explore -> scheme.primaryContainer to scheme.onPrimaryContainer
    BlockKind.ForYou, BlockKind.NewReleases -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    BlockKind.Episodes, BlockKind.Charts -> scheme.secondaryContainer to scheme.onSecondaryContainer
    BlockKind.Liked, BlockKind.Playlists, BlockKind.Library -> scheme.inverseSurface to scheme.inverseOnSurface
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
    internal fun layer(kind: BlockKind) = layers[kind]

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
    /** Where the tiles record their faces for the pages that open from them; null records none. */
    faces: BlockFaces? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val djFill = djColors?.container ?: scheme.primaryContainer
    val djInk = djColors?.onContainer ?: scheme.onPrimaryContainer
    fun Modifier.block(kind: BlockKind, shape: Shape, color: Color): Modifier = this
        .onGloballyPositioned {
            onBounds(kind, it.boundsInRoot())
            faces?.bounds?.set(kind, it.boundsInRoot())
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
        .background(color)
        .clickable(role = Role.Button) { onOpen(kind) }

    Column(
        modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The DJ: the widest tile, because it is the one thing on Home that needs no choosing.
        Box(
            Modifier
                .fillMaxWidth()
                .height(172.dp)
                .block(
                    BlockKind.Dj,
                    RoundedCornerShape(topStart = Tile, topEnd = Tile, bottomEnd = Tile, bottomStart = Tight),
                    djFill,
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

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .height(230.dp)
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
                        .height(110.dp)
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
                        .height(110.dp)
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

        // The last few things played, as covers in four different shapes. A tap plays one.
        if (recent.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { onBounds(BlockKind.Recent, it.boundsInRoot()) }
                    .clip(RoundedCornerShape(32.dp))
                    .background(scheme.surfaceContainer)
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
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
                                .height(72.dp)
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

        // For going deeper: each a badge in the color its page will be, on a plain tile.
        for (pair in SMALL_BLOCKS.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (small in pair) {
                    val (badge, onBadge) = small.kind.colors(scheme)
                    Row(
                        Modifier
                            .weight(1f)
                            .height(72.dp)
                            .block(small.kind, RoundedCornerShape(28.dp), scheme.surfaceContainer)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(small.badgeShape).background(badge),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(small.icon, contentDescription = null, tint = onBadge, modifier = Modifier.size(24.dp))
                        }
                        Text(
                            stringResource(small.label),
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

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
    SmallBlock(BlockKind.Explore, Icons.Rounded.Explore, R.string.filter_explore, CircleShape),
    SmallBlock(BlockKind.Playlists, Icons.AutoMirrored.Rounded.QueueMusic, R.string.filter_playlists, SquareBadge),
    SmallBlock(BlockKind.Library, Icons.Rounded.LibraryMusic, R.string.tab_library, LeafBadge),
    SmallBlock(BlockKind.Charts, Icons.Rounded.BarChart, R.string.filter_charts, SquareBadge),
    SmallBlock(BlockKind.QuickPicks, Icons.Rounded.Shuffle, R.string.spotlight_quick_picks, LeafBadge),
    SmallBlock(BlockKind.NewReleases, Icons.Rounded.NewReleases, R.string.filter_new_releases, CircleShape),
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
private fun BlockCover(song: Song) {
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
