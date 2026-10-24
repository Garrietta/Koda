package com.ivor.ivormusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ArtistItem
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SubgenreShelf
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

private enum class ExploreFilter {
    All, Liked, Downloads, Recent
}

/** An album matched by Explore search, with its songs ready to play. */
private data class ExploreAlbumResult(
    val name: String,
    val artist: String,
    val art: Any?,
    val songs: List<Song>,
    /** Set for YouTube Music albums: opens via the playlist hand-off. */
    val ytmItem: PlaylistDisplayItem? = null,
)

/**
 * The Explore tab: a place to discover what's in the library, separate from
 * Search. Quick Picks filtered by source, plus an Artist Spotlight for the
 * most-played artist.
 */
@Composable
fun ExploreContent(
    songs: List<Song>,
    likedSongs: List<Song>,
    downloadedSongs: List<Song>,
    recentlyPlayed: List<Song>,
    topArtists: List<TopArtist>,
    artistPhotos: Map<String, String>,
    newFromLiked: List<Song> = emptyList(),
    subgenreShelves: List<SubgenreShelf> = emptyList(),
    recommendations: List<Song> = emptyList(),
    playlists: List<PlaylistDisplayItem> = emptyList(),
    onPlaySongs: (List<Song>, Song?) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null,
    onArtistClick: (String) -> Unit,
    onArtistRadio: (String) -> Unit,
    onPlaylistClick: (PlaylistDisplayItem) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState(),
) {
    var filter by rememberSaveable { mutableStateOf(ExploreFilter.All) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // Debounced, like the search screen: the sections below recompute once
    // the user pauses typing, not on every keystroke.
    var debouncedQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        delay(400)
        debouncedQuery = searchQuery
    }
    val isSearching = debouncedQuery.trim().isNotBlank()

    // YouTube Music results: Explore search queries all of YouTube Music,
    // not just the local library — the same APIs the Search screen uses,
    // fired once the query settles. Local matches merge in below; the
    // token guard keeps a slow earlier fetch from clobbering a newer one.
    var ytmSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var ytmArtists by remember { mutableStateOf<List<ArtistItem>>(emptyList()) }
    var ytmAlbums by remember { mutableStateOf<List<PlaylistDisplayItem>>(emptyList()) }
    var ytmPlaylists by remember { mutableStateOf<List<PlaylistDisplayItem>>(emptyList()) }
    var ytmLoading by remember { mutableStateOf(false) }
    LaunchedEffect(debouncedQuery) {
        val q = debouncedQuery.trim()
        if (q.isBlank()) {
            ytmSongs = emptyList()
            ytmArtists = emptyList()
            ytmAlbums = emptyList()
            ytmPlaylists = emptyList()
            ytmLoading = false
            return@LaunchedEffect
        }
        ytmLoading = true
        val songsDeferred = async { viewModel.searchYouTube(q) }
        val artistsDeferred = async { viewModel.searchArtists(q) }
        val albumsDeferred = async { viewModel.searchAlbums(q) }
        val playlistsDeferred = async { viewModel.searchPlaylists(q) }
        val fetchedSongs = songsDeferred.await()
        val fetchedArtists = artistsDeferred.await()
        val fetchedAlbums = albumsDeferred.await()
        val fetchedPlaylists = playlistsDeferred.await()
        if (debouncedQuery.trim() == q) {
            ytmSongs = fetchedSongs
            ytmArtists = fetchedArtists
            ytmAlbums = fetchedAlbums
            ytmPlaylists = fetchedPlaylists
            ytmLoading = false
        }
    }

    // Live search sections: the whole page becomes results while searching.
    // Local library matches first, YouTube Music results filling the rest.
    val searchArtists = remember(debouncedQuery, songs, likedSongs, topArtists, ytmArtists) {
        val q = debouncedQuery.trim()
        if (q.isBlank()) emptyList()
        else {
            val fromTop = topArtists.filter { it.name.contains(q, ignoreCase = true) }
            val topNames = fromTop.map { it.name }.toSet()
            val fromLibrary = (songs + likedSongs).map { it.artist }.distinct()
                .filter { it.contains(q, ignoreCase = true) && it !in topNames }
                .map { TopArtist(name = it, plays = 0, fallbackArtwork = null) }
            val known = (fromTop + fromLibrary).map { it.name.lowercase() }.toSet()
            val fromYtm = ytmArtists
                .filter { it.name.lowercase() !in known }
                .map { TopArtist(name = it.name, plays = 0, fallbackArtwork = it.thumbnailUrl) }
            (fromTop + fromLibrary + fromYtm).take(10)
        }
    }
    val searchAlbums = remember(debouncedQuery, songs, likedSongs, ytmAlbums) {
        val q = debouncedQuery.trim()
        if (q.isBlank()) emptyList()
        else {
            val local = (songs + likedSongs).distinctBy { it.id }
                .groupBy { it.album }
                .filter { (album, _) -> album.contains(q, ignoreCase = true) }
                .map { (album, albumSongs) ->
                    ExploreAlbumResult(
                        name = album,
                        artist = albumSongs.first().artist,
                        art = albumSongs.firstNotNullOfOrNull {
                            it.albumArtUri ?: it.highResThumbnailUrl ?: it.thumbnailUrl
                        },
                        songs = albumSongs,
                    )
                }
                .sortedByDescending { it.songs.size }
            val localNames = local.map { it.name.lowercase() }.toSet()
            val fromYtm = ytmAlbums
                .filter { it.name.lowercase() !in localNames }
                .map {
                    ExploreAlbumResult(
                        name = it.name,
                        artist = it.uploaderName,
                        art = it.thumbnailUrl,
                        songs = emptyList(),
                        ytmItem = it,
                    )
                }
            (local + fromYtm).take(10)
        }
    }
    val searchSongs = remember(debouncedQuery, songs, likedSongs, ytmSongs) {
        val q = debouncedQuery.trim()
        if (q.isBlank()) emptyList()
        else {
            val local = (songs + likedSongs).distinctBy { it.id }.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
            }
            val localIds = local.map { it.id }.toSet()
            (local + ytmSongs.filter { it.id !in localIds }).take(50)
        }
    }
    val searchPlaylists = remember(debouncedQuery, playlists, ytmPlaylists) {
        val q = debouncedQuery.trim()
        if (q.isBlank()) emptyList()
        else {
            val local = playlists.filter { it.name.contains(q, ignoreCase = true) }
            val localIds = local.map { it.id }.toSet()
            (local + ytmPlaylists.filter { it.id !in localIds }).take(10)
        }
    }

    val quickPicks = remember(filter, songs, likedSongs, downloadedSongs, recentlyPlayed) {
        when (filter) {
            ExploreFilter.All -> songs
            ExploreFilter.Liked -> likedSongs
            ExploreFilter.Downloads -> downloadedSongs
            ExploreFilter.Recent -> recentlyPlayed
        }.distinctBy { it.id }.take(24)
    }

    val spotlightArtist = remember(topArtists) { topArtists.firstOrNull() }
    val spotlightSongs = remember(spotlightArtist, songs, likedSongs) {
        val name = spotlightArtist?.name ?: return@remember emptyList()
        (songs + likedSongs).distinctBy { it.id }
            .filter { it.artist.equals(name, ignoreCase = true) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "explore_header") {
            Text(
                stringResource(R.string.tab_explore),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // Search bar: no auto-focus, keyboard only opens on tap.
        item(key = "explore_search") {
            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        androidx.compose.material3.IconButton(
                            onClick = { searchQuery = "" }
                        ) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = null,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
        }

        // Browse content hides while searching; the results take the whole page.
        if (!isSearching) {
        item(key = "explore_chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ExploreFilter.entries) { entry ->
                    FilterChip(
                        selected = filter == entry,
                        onClick = { filter = entry },
                        label = {
                            Text(
                                when (entry) {
                                    ExploreFilter.All -> stringResource(R.string.explore_filter_all)
                                    ExploreFilter.Liked -> stringResource(R.string.explore_filter_liked)
                                    ExploreFilter.Downloads -> stringResource(R.string.explore_filter_downloads)
                                    ExploreFilter.Recent -> stringResource(R.string.explore_filter_recent)
                                }
                            )
                        },
                    )
                }
            }
        }

        if (quickPicks.isNotEmpty()) {
            item(key = "quick_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.explore_quick_picks),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "quick_picks") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(quickPicks, key = { it.id }) { song ->
                        ExplorePickCard(
                            song = song,
                            onClick = { onPlaySongs(quickPicks, song) },
                        )
                    }
                }
            }
        }

        if (spotlightArtist != null) {
            item(key = "spotlight") {
                val photo = artistPhotos[spotlightArtist.name]
                    ?: spotlightArtist.fallbackArtwork
                val topTrack = spotlightSongs.firstOrNull()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.explore_artist_spotlight),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (photo != null) {
                                AsyncImage(
                                    model = photo,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(16.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    spotlightArtist.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (topTrack != null) {
                                    Text(
                                        stringResource(
                                            R.string.explore_top_track,
                                            topTrack.title,
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onArtistRadio(spotlightArtist.name) },
                                enabled = spotlightSongs.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.explore_artist_radio))
                            }
                            OutlinedButton(
                                onClick = { onArtistClick(spotlightArtist.name) },
                            ) {
                                Text(stringResource(R.string.explore_view_artist))
                            }
                        }
                    }
                }
            }

            // New tracks by the listener's most-liked artists.
            if (newFromLiked.isNotEmpty()) {
                item(key = "explore_new_header") {
                    Text(
                        stringResource(R.string.explore_new_from_liked),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                    )
                }
                item(key = "explore_new_rail") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(newFromLiked, key = { "new_${it.id}" }) { song ->
                            ExplorePickCard(
                                song = song,
                                onClick = { onPlaySongs(newFromLiked, song) },
                            )
                        }
                    }
                }
            }

            // Subgenre shelves: popular songs from similar artists.
            subgenreShelves.forEachIndexed { index, shelf ->
                item(key = "explore_subgenre_header_$index") {
                    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
                        Text(
                            stringResource(R.string.explore_subgenre_title, shelf.subgenre),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (shelf.basedOn.isNotEmpty()) {
                            Text(
                                stringResource(
                                    R.string.explore_subgenre_sub,
                                    shelf.basedOn.take(2).joinToString(", "),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item(key = "explore_subgenre_rail_$index") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shelf.songs, key = { "sub_${index}_${it.id}" }) { song ->
                            ExplorePickCard(
                                song = song,
                                onClick = { onPlaySongs(shelf.songs, song) },
                            )
                        }
                    }
                }
            }

            // Top artists rail: the listener's most-played artists.
            if (topArtists.isNotEmpty()) {
                item(key = "explore_artists_header") {
                    Text(
                        stringResource(R.string.explore_top_artists),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                    )
                }
                item(key = "explore_artists_rail") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(topArtists.take(10), key = { "artist_${it.name}" }) { artist ->
                            ExploreArtistCard(
                                artist = artist,
                                photo = artistPhotos[artist.name] ?: artist.fallbackArtwork,
                                onClick = { onArtistClick(artist.name) },
                            )
                        }
                    }
                }
            }

            // Recommended for you.
            if (recommendations.isNotEmpty()) {
                item(key = "explore_recommended_header") {
                    Text(
                        stringResource(R.string.explore_recommended),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                    )
                }
                item(key = "explore_recommended_rail") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recommendations, key = { "rec_${it.id}" }) { song ->
                            ExplorePickCard(
                                song = song,
                                onClick = { onPlaySongs(recommendations, song) },
                            )
                        }
                    }
                }
            }
        }
        } // !isSearching

        if (isSearching) {
            // Live results: Artists, Albums, Songs, Playlists sections.
            if (searchArtists.isNotEmpty()) {
                item(key = "search_artists_header") {
                    ExploreSearchSectionHeader(R.string.section_artists)
                }
                item(key = "search_artists_rail") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(searchArtists, key = { "sartist_${it.name}" }) { artist ->
                            ExploreArtistCard(
                                artist = artist,
                                photo = artistPhotos[artist.name] ?: artist.fallbackArtwork,
                                onClick = { onArtistClick(artist.name) },
                            )
                        }
                    }
                }
            }
            if (searchAlbums.isNotEmpty()) {
                item(key = "search_albums_header") {
                    ExploreSearchSectionHeader(R.string.section_albums)
                }
                items(searchAlbums, key = { "salbum_${it.name}" }) { album ->
                    ExploreAlbumRow(
                        album = album,
                        onClick = {
                            album.ytmItem?.let { onPlaylistClick(it) }
                                ?: onAlbumClick(album.name)
                        },
                    )
                }
            }
            if (searchSongs.isNotEmpty()) {
                item(key = "search_songs_header") {
                    ExploreSearchSectionHeader(R.string.section_songs)
                }
                items(searchSongs, key = { "ssong_${it.id}" }) { song ->
                    ExploreSongRow(
                        song = song,
                        onClick = { onPlaySongs(searchSongs, song) },
                    )
                }
            }
            if (searchPlaylists.isNotEmpty()) {
                item(key = "search_playlists_header") {
                    ExploreSearchSectionHeader(R.string.section_playlists)
                }
                items(searchPlaylists, key = { "splaylist_${it.id}" }) { playlist ->
                    ExplorePlaylistRow(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                    )
                }
            }
            if (searchArtists.isEmpty() && searchAlbums.isEmpty() &&
                searchSongs.isEmpty() && searchPlaylists.isEmpty() &&
                !ytmLoading
            ) {
                item(key = "search_empty") {
                    Text(
                        stringResource(R.string.search_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp),
                    )
                }
            }
        } else if (quickPicks.isEmpty() && spotlightArtist == null) {
            item(key = "explore_empty") {
                Text(
                    stringResource(R.string.explore_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun ExploreSearchSectionHeader(titleRes: Int) {
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun ExploreAlbumRow(
    album: ExploreAlbumResult,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (album.art != null) {
            AsyncImage(
                model = album.art,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Spacer(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                album.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                album.ytmItem?.let { item ->
                    if (item.itemCount >= 0) "${album.artist} • ${item.itemCount} songs"
                    else album.artist
                } ?: "${album.artist} • ${album.songs.size} songs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExploreSongRow(
    song: Song,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExplorePlaylistRow(
    playlist: PlaylistDisplayItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playlist.thumbnailUrl != null) {
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Spacer(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildString {
                append(playlist.uploaderName)
                if (playlist.itemCount >= 0) append(" • ${playlist.itemCount} songs")
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExplorePickCard(
    song: Song,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(128.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        val art = song.albumArtUri ?: song.highResThumbnailUrl ?: song.thumbnailUrl
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                modifier = Modifier
                    .size(128.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Spacer(
                Modifier
                    .size(128.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            song.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A circular artist card for the Top Artists rail: photo, name, play count.
 */
@Composable
private fun ExploreArtistCard(
    artist: TopArtist,
    photo: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Spacer(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.explore_artist_plays, artist.plays),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
