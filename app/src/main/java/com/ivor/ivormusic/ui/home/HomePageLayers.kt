package com.ivor.ivormusic.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.library.LibraryContent
import com.ivor.ivormusic.ui.player.PlayerViewModel

/**
 * Draws every page open over Home, bottom first, each in a [BlockReveal]
 * growing out of where it was tapped. A page that came from a tile opens
 * in the tile's color; the rest open in a plain one that gives way to
 * the page's own (a cover's colors, the app's).
 *
 * An album, playlist or artist is the Library's own page for it, drawn
 * alone ([LibraryContent] with `popupPage`); anything opened from inside
 * one is pushed as a page of its own, so Back walks back through them.
 */
@Composable
internal fun HomePageLayers(
    stack: HomePageStack,
    faces: BlockFaces,
    songs: List<Song>,
    isLocalLibrary: Boolean,
    viewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    isDarkMode: Boolean,
    contentPadding: PaddingValues,
    onShowPlayer: () -> Unit,
    onDownloadsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onOpenAiDjSettings: () -> Unit,
    onOpenChannel: ((String) -> Unit)?,
    onSongLongPress: (Song) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val playQueue: (List<Song>, Song?) -> Unit = { queue, start ->
        playerViewModel.playQueue(queue, start)
        onShowPlayer()
    }
    val shuffleQueue: (List<Song>) -> Unit = { queue ->
        playerViewModel.playQueueShuffled(queue)
        onShowPlayer()
    }
    for (entry in stack.entries) {
        key(entry.id) {
            val (ground, ink) = entry.windowColors(scheme.surfaceContainerHigh, scheme.onSurface)
            BlockReveal(
                open = entry.open,
                origin = entry.origin,
                face = faces.faceAt(entry.origin),
                originShape = entry.shape,
                container = ground,
                content = ink,
                onDismiss = { entry.open = false },
                onClosed = { stack.remove(entry) },
                appColors = true,
            ) {
                // Only a page grown from a tile wears the tile; one grown
                // from a fingertip keeps its own colors.
                CompositionLocalProvider(LocalBlockTile provides entry.tile) {
                    when (val page = entry.page) {
                        HomePage.Explore -> ExplorePage(
                            songs = songs,
                            viewModel = viewModel,
                            contentPadding = contentPadding,
                            onPlaySongs = playQueue,
                            onShuffleSongs = shuffleQueue,
                            onSongLongPress = onSongLongPress,
                            onOpenPage = { stack.push(it) },
                        )
                        else -> LibraryContent(
                            songs = songs,
                            isLocalLibrary = isLocalLibrary,
                            onDownloadsClick = onDownloadsClick,
                            onSongClick = { song -> playQueue(songs, song) },
                            onPlayQueue = playQueue,
                            onPlayDjMix = { mix ->
                                playerViewModel.playDjMix(mix)
                                onShowPlayer()
                            },
                            onDjSessionStarted = { title, seed, recent, liked, voice ->
                                playerViewModel.startDjInfiniteSession(title, seed, recent, liked, voice)
                            },
                            onShuffleQueue = shuffleQueue,
                            contentPadding = contentPadding,
                            viewModel = viewModel,
                            isDarkMode = isDarkMode,
                            initialReturnToCaller = true,
                            onReturnToCaller = { entry.open = false },
                            initialPlaylist = (page as? HomePage.Playlist)?.item,
                            initialArtist = (page as? HomePage.Artist)?.name,
                            initialArtistId = (page as? HomePage.Artist)?.id,
                            initialAlbum = (page as? HomePage.DeviceAlbum)?.name,
                            onStatsClick = onStatsClick,
                            onOpenAiDjSettings = onOpenAiDjSettings,
                            onOpenChannel = onOpenChannel,
                            onSongLongPress = onSongLongPress,
                            onEnqueueSong = { song, playNext ->
                                if (playNext) playerViewModel.playNext(song) else playerViewModel.addToQueue(song)
                            },
                            popupPage = true,
                            onOpenPage = { stack.push(it) },
                        )
                    }
                }
            }
        }
    }
}

/** Explore as a page over Home: search at its top, then the shelves to find things on. */
@Composable
private fun ExplorePage(
    songs: List<Song>,
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    onPlaySongs: (List<Song>, Song?) -> Unit,
    onShuffleSongs: (List<Song>) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onOpenPage: (HomePage) -> Unit,
) {
    val liked by viewModel.likedSongs.collectAsState()
    val downloaded by viewModel.downloadedSongs.collectAsState()
    val recent by viewModel.recentlyPlayed.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val artistPhotos by viewModel.artistPhotos.collectAsState()
    val newFromLiked by viewModel.exploreNewFromLiked.collectAsState()
    val subgenreShelves by viewModel.exploreSubgenreShelves.collectAsState()
    val recommendations by viewModel.exploreRecommendations.collectAsState()
    val playlists by viewModel.userPlaylists.collectAsState()
    ExploreContent(
        songs = songs,
        likedSongs = liked,
        downloadedSongs = downloaded,
        recentlyPlayed = recent,
        topArtists = topArtists,
        artistPhotos = artistPhotos,
        newFromLiked = newFromLiked,
        subgenreShelves = subgenreShelves,
        recommendations = recommendations,
        playlists = playlists,
        onPlaySongs = onPlaySongs,
        onSongLongPress = onSongLongPress,
        onArtistClick = { artist -> onOpenPage(HomePage.Artist(artist)) },
        onPlaylistClick = { playlist -> onOpenPage(HomePage.Playlist(playlist)) },
        onAlbumClick = { album -> onOpenPage(HomePage.DeviceAlbum(album)) },
        onArtistRadio = { artist ->
            val artistSongs = (songs + liked).distinctBy { it.id }
                .filter { it.artist.equals(artist, ignoreCase = true) }
            if (artistSongs.isNotEmpty()) onShuffleSongs(artistSongs)
        },
        viewModel = viewModel,
        contentPadding = contentPadding,
    )
}
