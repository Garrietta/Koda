package com.ivor.ivormusic.ui.home

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
 * a tile. Every color is a scheme role, so the tiles are Rondo's iris,
 * orange, cream and orchid on its own palette and follow whichever
 * palette or album the listener has chosen instead.
 *
 * This file only draws. What a tile opens is the caller's business, so
 * the whole page can be rendered from plain lists in a test.
 */

/** The pages a small tile opens. The large tiles have callbacks of their own. */
internal enum class BlocksDestination { QuickPicks, ForYou, Playlists, Charts, NewReleases, Explore, Recent }

/** An On repeat tile needs at least this many songs to be worth counting. */
private const val ON_REPEAT_MIN = 3

private val Tile = 40.dp
private val Tight = 14.dp

/** Heights of the bars in the DJ tile's level meter, left to right. */
private val METER = listOf(18, 40, 28, 52, 34, 22, 44)

@Composable
internal fun BlocksHome(
    onRepeat: List<Song>,
    likedCount: Int,
    newEpisodes: List<Song>,
    recent: List<Song>,
    onDj: () -> Unit,
    onPlayOnRepeat: () -> Unit,
    onLiked: () -> Unit,
    onPodcasts: () -> Unit,
    onRecent: (Song) -> Unit,
    onOpen: (BlocksDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DjBlock(onDj)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onRepeat.size >= ON_REPEAT_MIN) {
                OnRepeatBlock(onRepeat, onPlayOnRepeat)
            } else {
                ForYouBlock { onOpen(BlocksDestination.ForYou) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(CircleShape)
                        .background(scheme.inverseSurface)
                        .clickable(role = Role.Button, onClick = onLiked)
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
                        .clip(RoundedCornerShape(topStart = Tight, topEnd = Tile, bottomEnd = Tile, bottomStart = Tile))
                        .background(scheme.secondaryContainer)
                        .clickable(role = Role.Button, onClick = onPodcasts)
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

        if (recent.isNotEmpty()) JumpBackInBlock(recent, onRecent) { onOpen(BlocksDestination.Recent) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallBlock(
                Icons.Rounded.Shuffle, stringResource(R.string.spotlight_quick_picks),
                scheme.primaryContainer, scheme.onPrimaryContainer, BADGE_SHAPES[0],
            ) { onOpen(BlocksDestination.QuickPicks) }
            SmallBlock(
                Icons.Rounded.AutoAwesome, stringResource(R.string.filter_recommended),
                scheme.tertiaryContainer, scheme.onTertiaryContainer, BADGE_SHAPES[1],
            ) { onOpen(BlocksDestination.ForYou) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallBlock(
                Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.filter_playlists),
                scheme.inverseSurface, scheme.inverseOnSurface, BADGE_SHAPES[2],
            ) { onOpen(BlocksDestination.Playlists) }
            SmallBlock(
                Icons.Rounded.BarChart, stringResource(R.string.filter_charts),
                scheme.secondaryContainer, scheme.onSecondaryContainer, BADGE_SHAPES[3],
            ) { onOpen(BlocksDestination.Charts) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallBlock(
                Icons.Rounded.NewReleases, stringResource(R.string.filter_new_releases),
                scheme.tertiaryContainer, scheme.onTertiaryContainer, BADGE_SHAPES[0],
            ) { onOpen(BlocksDestination.NewReleases) }
            SmallBlock(
                Icons.Rounded.Explore, stringResource(R.string.filter_explore),
                scheme.primaryContainer, scheme.onPrimaryContainer, BADGE_SHAPES[1],
            ) { onOpen(BlocksDestination.Explore) }
        }
    }
}

/** The DJ: the widest tile, because it is the one thing on Home that needs no choosing. */
@Composable
private fun DjBlock(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(172.dp)
            .clip(RoundedCornerShape(topStart = Tile, topEnd = Tile, bottomEnd = Tile, bottomStart = Tight))
            .background(scheme.primaryContainer)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            stringResource(R.string.blocks_dj),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp),
            color = scheme.onPrimaryContainer,
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
            stringResource(R.string.blocks_dj_caption),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onPrimaryContainer,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 18.dp).width(180.dp),
        )
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (bar in METER) {
                Box(Modifier.width(7.dp).height(bar.dp).clip(CircleShape).background(scheme.onPrimaryContainer))
            }
        }
    }
}

/** How many songs the listener keeps returning to, with three of their covers. Plays them. */
@Composable
private fun RowScope.OnRepeatBlock(songs: List<Song>, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .weight(1f)
            .height(230.dp)
            .clip(RoundedCornerShape(topStart = Tile, topEnd = Tile, bottomEnd = Tight, bottomStart = Tile))
            .background(scheme.tertiaryContainer)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            stringResource(R.string.home_section_on_repeat),
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onTertiaryContainer,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 18.dp),
        )
        Text(
            songs.size.toString(),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 92.sp),
            color = scheme.onTertiaryContainer,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 46.dp),
        )
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 15.dp, bottom = 15.dp),
            horizontalArrangement = Arrangement.spacedBy((-14).dp),
        ) {
            for (song in songs.take(3)) {
                // The ring is the tile's own color, so overlapping covers read as separate discs.
                Box(Modifier.size(52.dp).clip(CircleShape).background(scheme.tertiaryContainer).padding(3.dp)) {
                    BlockCover(song, CircleShape)
                }
            }
        }
    }
}

/** Stands in for On repeat until there is enough history to count: music not heard yet. */
@Composable
private fun RowScope.ForYouBlock(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f)
            .height(230.dp)
            .clip(RoundedCornerShape(topStart = Tile, topEnd = Tile, bottomEnd = Tight, bottomStart = Tile))
            .background(scheme.tertiaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
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
}

/** The last few things played, as covers in four different shapes. A tap plays one. */
@Composable
private fun JumpBackInBlock(recent: List<Song>, onRecent: (Song) -> Unit, onSeeAll: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
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
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.action_see_all)) }
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
                    BlockCover(song, COVER_SHAPES[index])
                }
            }
            // Fewer than four keeps the covers the size they would be in a full row.
            repeat(COVER_SHAPES.size - shown.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private val COVER_SHAPES: List<Shape> = listOf(
    RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 8.dp),
    CircleShape,
    RoundedCornerShape(topStart = 22.dp, topEnd = 8.dp, bottomEnd = 22.dp, bottomStart = 22.dp),
    RoundedCornerShape(22.dp),
)

private val BADGE_SHAPES: List<Shape> = listOf(
    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 6.dp),
    CircleShape,
    RoundedCornerShape(16.dp),
    RoundedCornerShape(topStart = 6.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
)

/** A page to open: a colored badge and a name on a plain tile. */
@Composable
private fun RowScope.SmallBlock(
    icon: ImageVector,
    label: String,
    badge: Color,
    onBadge: Color,
    badgeShape: Shape,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .weight(1f)
            .height(72.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(badgeShape).background(badge), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = onBadge, modifier = Modifier.size(24.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

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
private fun BlockCover(song: Song, shape: Shape) {
    Box(Modifier.fillMaxSize().clip(shape)) {
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
}

/**
 * The top of a page opened from a tile: the way back, and what the page
 * is. It stands where the filter chips used to lead the whole Home.
 */
@Composable
internal fun BlocksPageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
