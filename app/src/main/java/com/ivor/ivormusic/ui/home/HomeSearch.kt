package com.ivor.ivormusic.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ArtistItem
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/** An artist among the search results: one the library holds, or one YouTube Music found. */
internal data class SearchArtist(val name: String, val id: String?, val photo: String?)

/**
 * Home's search: what is typed, and what it finds. Results come from the
 * library at once and from YouTube Music once typing pauses; while there
 * is a query they take the place of the tiles.
 */
@Stable
internal class HomeSearchState {
    var query by mutableStateOf("")
    var loading by mutableStateOf(false)
    var songs by mutableStateOf<List<Song>>(emptyList())
    var artists by mutableStateOf<List<SearchArtist>>(emptyList())
    var albums by mutableStateOf<List<HomePage>>(emptyList())
    var albumCovers by mutableStateOf<Map<HomePage, Pair<String, String?>>>(emptyMap())
    var playlists by mutableStateOf<List<PlaylistDisplayItem>>(emptyList())

    val active: Boolean get() = query.isNotBlank()
}

/** How long typing has to pause before YouTube Music is asked. */
private const val SEARCH_DEBOUNCE_MS = 350L

/**
 * Keeps [HomeSearchState]'s results in step with its query: the library's
 * own matches straight away, YouTube Music's after a pause, the slower
 * answer to an older query never replacing a newer one.
 */
@Composable
internal fun rememberHomeSearch(
    viewModel: HomeViewModel,
    library: List<Song>,
    playlists: List<PlaylistDisplayItem>,
): HomeSearchState {
    val state = remember { HomeSearchState() }
    val query = state.query.trim()
    LaunchedEffect(query, library, playlists) {
        if (query.isBlank()) {
            state.songs = emptyList(); state.artists = emptyList(); state.albums = emptyList()
            state.playlists = emptyList(); state.loading = false
            return@LaunchedEffect
        }
        val local = librarySearch(query, library, playlists)
        state.songs = local.songs
        state.artists = local.artists
        state.albums = local.albums.map { it.page() }
        state.albumCovers = local.albums.associate { it.page() to ((it.cover ?: "") to it.artist) }
        state.playlists = local.playlists
        state.loading = true
        delay(SEARCH_DEBOUNCE_MS)
        val remote = coroutineScope {
            val songs = async { viewModel.searchYouTube(query) }
            val artists = async { viewModel.searchArtists(query) }
            val albums = async { viewModel.searchAlbums(query) }
            val lists = async { viewModel.searchPlaylists(query) }
            RemoteResults(songs.await(), artists.await(), albums.await(), lists.await())
        }
        if (state.query.trim() != query) return@LaunchedEffect
        state.songs = (local.songs + remote.songs).distinctBy { it.id }.take(SEARCH_SONGS)
        val knownArtists = local.artists.mapTo(HashSet()) { it.name.lowercase() }
        state.artists = (local.artists + remote.artists.filter { it.name.lowercase() !in knownArtists }
            .map { SearchArtist(it.name, it.id.takeIf { id -> id.startsWith("UC") }, it.thumbnailUrl) }).take(SEARCH_SHELF)
        val knownAlbums = local.albums.mapTo(HashSet()) { it.name.lowercase() }
        val remoteAlbums = remote.albums.filter { it.name.lowercase() !in knownAlbums }
        state.albums = (local.albums.map { it.page() } + remoteAlbums.map { HomePage.Playlist(it) }).take(SEARCH_SHELF)
        state.albumCovers = local.albums.associate { it.page() to ((it.cover ?: "") to it.artist) } +
            remoteAlbums.associate { HomePage.Playlist(it) to ((it.thumbnailUrl ?: "") to it.uploaderName) }
        val knownLists = local.playlists.mapTo(HashSet()) { it.id }
        state.playlists = (local.playlists + remote.playlists.filter { it.id !in knownLists }).take(SEARCH_SHELF)
        state.loading = false
    }
    return state
}

private class RemoteResults(
    val songs: List<Song>,
    val artists: List<ArtistItem>,
    val albums: List<PlaylistDisplayItem>,
    val playlists: List<PlaylistDisplayItem>,
)

/** What the library itself holds that matches a query, before anything is asked of the network. */
internal class LocalResults(
    val songs: List<Song>,
    val artists: List<SearchArtist>,
    val albums: List<LibraryAlbum>,
    val playlists: List<PlaylistDisplayItem>,
)

private const val SEARCH_SONGS = 12
private const val SEARCH_SHELF = 12

/** The library's own matches for [query]: songs by title or artist, artists, albums and playlists by name. */
internal fun librarySearch(query: String, library: List<Song>, playlists: List<PlaylistDisplayItem>): LocalResults {
    val q = query.trim()
    if (q.isBlank()) return LocalResults(emptyList(), emptyList(), emptyList(), emptyList())
    val songs = library.filter { it.title.contains(q, true) || it.artist.contains(q, true) }
        .distinctBy { it.id }.take(SEARCH_SONGS)
    val artists = libraryArtists(library.filter { it.artist.contains(q, true) }, emptyList(), emptyMap(), emptyMap())
        .map { SearchArtist(it.name, it.id, it.photo) }.take(SEARCH_SHELF)
    val albums = libraryAlbums(library.filter { it.album.contains(q, true) }, emptyMap()).take(SEARCH_SHELF)
    val lists = playlists.filter { it.name.contains(q, true) }.take(SEARCH_SHELF)
    return LocalResults(songs, artists, albums, lists)
}

/**
 * The search field at the top of Home, a pill with the search badge in
 * it. Typing searches at once; the cross clears it and the tiles return.
 */
@Composable
internal fun HomeSearchField(state: HomeSearchState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (badge, onBadge) = BlockKind.Explore.colors(scheme)
    val focus = LocalFocusManager.current
    androidx.activity.compose.BackHandler(enabled = state.active) {
        state.query = ""
        focus.clearFocus()
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(64.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainer)
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = onBadge, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (state.query.isEmpty()) {
                Text(
                    stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = state.query,
                onValueChange = { state.query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleSmall.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.active) {
            IconButton(onClick = { state.query = ""; focus.clearFocus() }) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear))
            }
        }
    }
}

/**
 * The results, in place of the tiles: songs to play, then artists,
 * albums and playlists to open as pages over Home.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
internal fun LazyListScope.homeSearchResults(
    state: HomeSearchState,
    onPlaySongs: (List<Song>, Song?) -> Unit,
    onSongLongPress: ((Song) -> Unit)?,
    onOpenPage: (HomePage) -> Unit,
) {
    if (state.songs.isNotEmpty()) {
        item(key = "search-songs-header") { SpotlightSectionHeader(title = stringResource(R.string.cat_songs)) }
        items(state.songs, key = { "search-song-${it.id}" }) { song ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onPlaySongs(state.songs, song) },
                        onLongClick = onSongLongPress?.let { press -> { press(song) } },
                    )
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.ivor.ivormusic.ui.components.SongArtwork(
                    song,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    if (state.artists.isNotEmpty()) {
        item(key = "search-artists-header") { SpotlightSectionHeader(title = stringResource(R.string.cat_artists)) }
        item(key = "search-artists") {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(state.artists, key = { it.id ?: it.name }) { artist ->
                    ResultCell(artist.name, null, artist.photo, CircleShape, Icons.Rounded.Person, centered = true) {
                        onOpenPage(HomePage.Artist(artist.name, artist.id))
                    }
                }
            }
        }
    }
    if (state.albums.isNotEmpty()) {
        item(key = "search-albums-header") { SpotlightSectionHeader(title = stringResource(R.string.cat_albums)) }
        item(key = "search-albums") {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(state.albums) { index, page ->
                    val (cover, artist) = state.albumCovers[page] ?: ("" to null)
                    val title = when (page) {
                        is HomePage.Playlist -> page.item.name
                        is HomePage.DeviceAlbum -> page.name
                        else -> ""
                    }
                    ResultCell(title, artist, cover.ifBlank { null }, ALBUM_SHAPES[index % ALBUM_SHAPES.size].toShape(), Icons.Rounded.Album) {
                        onOpenPage(page)
                    }
                }
            }
        }
    }
    if (state.playlists.isNotEmpty()) {
        item(key = "search-playlists-header") { SpotlightSectionHeader(title = stringResource(R.string.filter_playlists)) }
        item(key = "search-playlists") {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(state.playlists, key = { it.id }) { list ->
                    ResultCell(list.name, list.uploaderName, list.thumbnailUrl, RoundedCornerShape(28.dp), Icons.AutoMirrored.Rounded.QueueMusic) {
                        onOpenPage(HomePage.Playlist(list))
                    }
                }
            }
        }
    }
    if (state.loading) {
        item(key = "search-loading") {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        }
    } else if (state.songs.isEmpty() && state.artists.isEmpty() && state.albums.isEmpty() && state.playlists.isEmpty()) {
        item(key = "search-empty") {
            Text(
                stringResource(R.string.search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun ResultCell(
    title: String,
    subtitle: String?,
    art: String?,
    shape: androidx.compose.ui.graphics.Shape,
    placeholder: androidx.compose.ui.graphics.vector.ImageVector,
    centered: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(132.dp).clickable(onClickLabel = title, role = Role.Button, onClick = onClick),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(Modifier.size(132.dp).clip(shape)) {
            com.ivor.ivormusic.ui.components.VideoThumbnail(
                thumbnailUrl = art,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                showProgress = false,
                emptyIcon = placeholder,
                placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
