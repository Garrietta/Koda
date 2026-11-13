package com.ivor.ivormusic.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.TasteArtist
import com.ivor.ivormusic.data.groupSongsByAlbum
import com.ivor.ivormusic.data.groupSongsByArtist
import com.ivor.ivormusic.data.isUnknownAlbum
import com.ivor.ivormusic.data.isUnknownArtist

/** An artist on the Artists page: one the user follows, or one their songs credit. */
internal data class LibraryArtist(val name: String, val id: String?, val photo: String?, val songCount: Int)

/** An album on the Albums page, made of the songs the user has from it. */
internal data class LibraryAlbum(
    val name: String,
    val artist: String,
    val cover: String?,
    val songs: List<Song>,
    /** A sharper copy of [cover], drawn over it once it loads (it may not exist). */
    val sharpCover: String? = null,
)

/**
 * The Artists page: the artists followed on this device first, in the
 * order they were followed, then every artist the songs credit, most
 * played first. An artist in both is listed once, as followed.
 */
internal fun libraryArtists(
    songs: List<Song>,
    followed: List<TasteArtist>,
    photos: Map<String, String>,
    playCounts: Map<String, Int>,
): List<LibraryArtist> {
    val fromFollows = followed.map { artist ->
        LibraryArtist(
            name = artist.name,
            id = artist.id.takeIf { it.startsWith("UC") },
            photo = artist.thumbnailUrl ?: photos[artist.name],
            songCount = songs.count { it.artist.equals(artist.name, ignoreCase = true) },
        )
    }
    val followedNames = followed.mapTo(HashSet()) { it.name.trim().lowercase() }
    val fromSongs = songs.filterNot { isUnknownArtist(it.artist) }
        .groupSongsByArtist()
        .filterNot { (name, _) -> name.lowercase() in followedNames }
        .sortedWith(
            compareByDescending<Pair<String, List<Song>>> { (_, group) -> group.sumOf { playCounts[it.id] ?: 0 } }
                .thenByDescending { (_, group) -> group.size }
                .thenBy { (name, _) -> name.lowercase() },
        )
        .map { (name, group) ->
            LibraryArtist(
                name = name,
                id = null,
                photo = photos[name] ?: group.firstNotNullOfOrNull { it.albumArtUri?.toString() ?: it.thumbnailUrl },
                songCount = group.size,
            )
        }
    return fromFollows + fromSongs
}

/** The Albums page: every album the songs come from, most played first. */
internal fun libraryAlbums(songs: List<Song>, playCounts: Map<String, Int>): List<LibraryAlbum> =
    songs.filterNot { isUnknownAlbum(it.album) }
        .groupSongsByAlbum()
        .sortedWith(
            compareByDescending<Pair<String, List<Song>>> { (_, group) -> group.sumOf { playCounts[it.id] ?: 0 } }
                .thenBy { (name, _) -> name.lowercase() },
        )
        .map { (name, group) ->
            val first = group.first()
            LibraryAlbum(
                name = name,
                artist = first.artist,
                cover = group.firstNotNullOfOrNull { it.thumbnailUrl ?: it.albumArtUri?.toString() },
                songs = group,
                sharpCover = group.firstNotNullOfOrNull { it.highResThumbnailUrl },
            )
        }

/**
 * What an album opens: the whole release from YouTube Music when its songs
 * name one (and none of them is a file), so an album of which only one
 * song was ever played still opens as the album; else the files themselves.
 */
internal fun LibraryAlbum.page(): HomePage {
    val releaseId = songs.mapNotNull { it.albumId?.takeIf { id -> id.startsWith("MPRE") } }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    if (releaseId == null || songs.any { it.source == SongSource.LOCAL }) return HomePage.DeviceAlbum(name)
    val first = songs.first()
    return HomePage.Playlist(
        PlaylistDisplayItem(
            name = name,
            url = releaseId,
            uploaderName = first.artist,
            thumbnailUrl = cover,
            releaseType = first.releaseType,
            releaseYear = first.releaseYear,
        ),
    )
}

/** The artists, a row of round photos at a time, each named under it. */
internal fun LazyListScope.artistGridItems(
    artists: List<LibraryArtist>,
    columns: Int,
    onClick: (LibraryArtist) -> Unit,
) {
    artists.chunked(columns).forEachIndexed { row, group ->
        item(key = "artist-row-$row") {
            GridRow(columns, group.size) {
                for (artist in group) {
                    GridCell(
                        title = artist.name,
                        art = artist.photo,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        placeholder = Icons.Rounded.Person,
                        centered = true,
                        onClick = { onClick(artist) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The albums, a row at a time, each cover cut to one of Material's expressive shapes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyListScope.albumGridItems(
    albums: List<LibraryAlbum>,
    columns: Int,
    onClick: (LibraryAlbum) -> Unit,
) {
    albums.chunked(columns).forEachIndexed { row, group ->
        item(key = "album-row-$row") {
            GridRow(columns, group.size) {
                group.forEachIndexed { index, album ->
                    GridCell(
                        title = album.name,
                        subtitle = album.artist,
                        art = album.cover,
                        sharpArt = album.sharpCover,
                        shape = ALBUM_SHAPES[(row * columns + index) % ALBUM_SHAPES.size].toShape(),
                        placeholder = Icons.Rounded.Album,
                        onClick = { onClick(album) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The shapes album covers are cut to, in turn, so no two neighbours share one. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val ALBUM_SHAPES = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Square,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Arch,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Slanted,
    MaterialShapes.Sunny,
    MaterialShapes.Gem,
)

@Composable
private fun GridRow(
    columns: Int,
    filled: Int,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content()
        // A short last row keeps its cells the size of a full row's.
        repeat(columns - filled) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun GridCell(
    title: String,
    art: String?,
    shape: Shape,
    sharpArt: String? = null,
    placeholder: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    centered: Boolean = false,
) {
    Column(
        modifier.clickable(onClickLabel = title, role = Role.Button, onClick = onClick),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)) {
            com.ivor.ivormusic.ui.components.VideoThumbnail(
                thumbnailUrl = art,
                highResThumbnailUrl = sharpArt?.takeIf { it != art },
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                showProgress = false,
                emptyIcon = placeholder,
                placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
