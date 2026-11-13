package com.ivor.ivormusic.ui.home
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.library.songRowClick
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import com.ivor.ivormusic.ui.components.MusicVideoToggleState
import com.ivor.ivormusic.ui.components.rememberMusicVideoToggleState

/**
 * Spotlight: an alternative music Home built the way music apps are actually
 * built, off by default and chosen in onboarding or from Settings.
 *
 * It takes the two ideas that make Spotify's and YouTube Music's homes work and
 * puts them in one screen:
 *
 * **The shortcut grid** (Spotify). Two columns of wide, short tiles at the very
 * top - the six things you reach for most. It is the densest part of any music
 * home and the part that gets used, because it is above the fold and needs no
 * scrolling.
 *
 * **Quick picks** (YouTube Music). A *paged* block of four song rows. This is
 * the one horizontal gesture on the screen that earns its place: paging snaps,
 * so it does not compete with vertical scrolling the way a free-scrolling
 * carousel does, and it fits twelve songs into the height of four.
 *
 * Then artwork shelves, which is what the rest of every music home is, and what
 * the previous attempt at an alternative Home wrongly threw away.
 *
 * **Same data, different composition.** Every value comes from a flow
 * [HomeViewModel] already exposes. No new ViewModel, no new fetches. The moment
 * Spotlight owns data the classic Home does not, there are two Homes to keep
 * working and one quietly falls behind.
 *
 * The chips are honest about that: they scope the page to material that really
 * exists on the device rather than pretending to be YouTube Music's mood chips,
 * which are a browse call this screen deliberately does not make.
 */
@Composable
fun SpotlightHomeContent(
    songs: List<Song>,
    recentlyPlayed: List<Song>,
    likedSongs: List<Song>,
    playlists: List<PlaylistDisplayItem>,
    isInitialLoading: Boolean = false,
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null,
    onPlaySongs: (List<Song>, Song?) -> Unit,
    /**
     * Opens Explore, where search lives, from where its tile is. Required:
     * with the navigation bar turned off, the Explore tile is the only way there.
     */
    onOpenExplore: (androidx.compose.ui.geometry.Rect?) -> Unit,
    /** Plays these with shuffle on. Required: a button left unwired is a dead button. */
    onShuffleSongs: (List<Song>) -> Unit,
    onRecentClick: (Song) -> Unit,
    onPlaylistClick: (PlaylistDisplayItem) -> Unit = {},
    /** Opens Your Likes, growing out of the Liked tile at the given place. */
    onOpenLiked: (androidx.compose.ui.geometry.Rect?) -> Unit = {},
    /**
     * Opens an artist or album as a page over Home, growing out of the given
     * place, or out of where the finger came down when it is null.
     */
    onOpenPage: (HomePage, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    /**
     * Opens the DJ, given where its tile is on the screen so the page can
     * grow out of it. Required: a tile left unwired is a dead tile.
     */
    onOpenDj: (androidx.compose.ui.geometry.Rect?) -> Unit,
    /** The DJ tile's colors and the name of the mix it would play. */
    djColors: com.ivor.ivormusic.ui.theme.VividBlock? = null,
    djTitle: String? = null,
    /** The tiles' recorded faces, for the pages that grow out from under them. */
    blockFaces: BlockFaces? = null,
    /** Opens Spin on the pool the quick picks are showing. */
    onSpinClick: (SpinSource) -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    isDarkMode: Boolean,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    excludedFolders: Set<String> = emptySet(),
    manualScan: Boolean = false,
    videoMode: Boolean = false,
    onVideoModeToggle: (Boolean) -> Unit = {},
    showModeToggle: Boolean = true,
    modeToggleState: MusicVideoToggleState = rememberMusicVideoToggleState(videoMode),
    listState: LazyListState = rememberLazyListState(),
    /**
     * The chip row's choice, held by the caller beside [listState]. A Home tab
     * switch takes this screen out of composition, so state kept in here -
     * even saveable state - came back as All every time the Library was
     * visited and left. No default, so a new call site cannot forget it.
     */
    filter: SpotlightFilter,
    onFilterChange: (SpotlightFilter) -> Unit,
) {
    val isRefreshing by viewModel.isLoading.collectAsState()

    val discoverySongs by viewModel.discoverySongs.collectAsState()
    val discoveryCollections by viewModel.discoveryCollections.collectAsState()
    val isDiscoveryLoading by viewModel.isDiscoveryLoading.collectAsState()
    val musicBrowse by viewModel.musicBrowse.collectAsState()
    val onRepeat by viewModel.onRepeat.collectAsState()
    val newEpisodes by viewModel.newEpisodes.collectAsState()
    val podcastFeeds by viewModel.podcastFeeds.collectAsState()
    // The show opened from the Podcasts page, over the page itself.
    var openShow by remember { mutableStateOf<com.ivor.ivormusic.data.PodcastFeed?>(null) }

    // Home is the blocks. A tile opens a page of its own, which grows out
    // of the tile in the tile's color (BlockReveal). What a page lists is
    // one of the filters, so opening a block sets the filter, and closing
    // it lets the filter go once the page has shrunk away.
    var openBlock by rememberSaveable { mutableStateOf<BlockKind?>(null) }
    // The block the page belongs to, kept while the page closes.
    var pageBlock by rememberSaveable { mutableStateOf(BlockKind.ForYou) }
    val blockBounds = remember { mutableMapOf<BlockKind, androidx.compose.ui.geometry.Rect>() }
    val pageListState = rememberLazyListState()
    val openBlockPage: (BlockKind) -> Unit = { kind ->
        when (kind) {
            BlockKind.Dj -> onOpenDj(blockBounds[kind])
            BlockKind.Liked -> onOpenLiked(blockBounds[kind])
            BlockKind.Explore -> onOpenExplore(blockBounds[kind])
            else -> {
                onFilterChange(kind.filter())
                pageBlock = kind
                openBlock = kind
            }
        }
    }
    LaunchedEffect(openBlock) {
        if (openBlock != null) {
            pageListState.scrollToItem(0)
        } else if (filter != SpotlightFilter.All && pageListState.layoutInfo.totalItemsCount == 0) {
            // Restored with a filter but no page open: Home is the blocks.
            onFilterChange(SpotlightFilter.All)
        }
    }

    // Fetched when its tab is opened rather than with the rest of Home: it is
    // several searches plus a radio call, and most sessions never look at it.
    LaunchedEffect(filter) {
        if (filter == SpotlightFilter.Recommended) viewModel.loadDiscovery()
        filter.browseKey()?.let { viewModel.loadMusicBrowse(it) }
    }

    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val quickPicks = remember(filter, songs, likedSongs, recentlyPlayed, discoverySongs, newEpisodes, downloadedSongs) {
        when (filter) {
            SpotlightFilter.Liked -> likedSongs
            SpotlightFilter.Recent -> recentlyPlayed
            SpotlightFilter.Recommended -> discoverySongs
            SpotlightFilter.Podcasts -> newEpisodes
            SpotlightFilter.Downloads -> downloadedSongs
            SpotlightFilter.MusicHome, SpotlightFilter.Explore, SpotlightFilter.Charts, SpotlightFilter.NewReleases,
            SpotlightFilter.Artists, SpotlightFilter.Albums -> emptyList()
            else -> songs
        }.take(QUICK_PICK_PAGES * QUICK_PICK_ROWS)
    }

    // YouTube Music mints a handful of auto-generated mixes per account and
    // they arrive alongside real playlists. They are not something the user
    // made or saved, they churn on their own, and giving them a slot in a grid
    // of six pushes out things that were actually chosen. Dropped everywhere on
    // this screen rather than only from the grid, so they cannot come back in
    // through a shelf.
    val ownPlaylists = remember(playlists) { playlists.filterNot { isAutoMix(it.name) } }

    // The Artists and Albums pages: what the Library's tabs held, from the
    // songs Home already has (the device's, the liked, the played) and the
    // artists followed on this device.
    val playCounts by viewModel.playCounts.collectAsState()
    val artistPhotos by viewModel.artistPhotos.collectAsState()
    val tasteContext = androidx.compose.ui.platform.LocalContext.current
    val tasteStore = remember(tasteContext) { com.ivor.ivormusic.data.TasteProfileStore(tasteContext) }
    val tasteProfile by tasteStore.profile.collectAsState()
    val librarySongs = remember(songs, likedSongs, recentlyPlayed) {
        (songs + likedSongs + recentlyPlayed).distinctBy { it.id }
    }
    val libArtists = remember(filter, librarySongs, tasteProfile.artists, artistPhotos, playCounts) {
        if (filter == SpotlightFilter.Artists) {
            libraryArtists(librarySongs, tasteProfile.artists, artistPhotos, playCounts)
        } else emptyList()
    }
    val libAlbums = remember(filter, librarySongs, playCounts) {
        if (filter == SpotlightFilter.Albums) libraryAlbums(librarySongs, playCounts) else emptyList()
    }
    val gridWidth = com.ivor.ivormusic.ui.theme.windowDpSize().width
    val homeSearch = rememberHomeSearch(viewModel, librarySongs, ownPlaylists)


    // What a block's page lists, for whichever filter the block set.
    val pageSections: LazyListScope.() -> Unit = {
        if (filter == SpotlightFilter.Artists) {
            artistGridItems(libArtists, columns = (gridWidth / 120.dp).toInt().coerceAtLeast(3)) { artist ->
                onOpenPage(HomePage.Artist(artist.name, artist.id), null)
            }
        }
        if (filter == SpotlightFilter.Albums) {
            albumGridItems(libAlbums, columns = (gridWidth / 170.dp).toInt().coerceAtLeast(2)) { album ->
                onOpenPage(album.page(), null)
            }
        }
        // AI DJ moved to a floating action button next to the nav pill
        // (see HomeScreen) — no banner on Home anymore.

        // First load of the discovery tab: the grid below is empty and
        // will be for a second or two of network, and an empty page reads
        // as "nothing to suggest" rather than "still asking".
        if (filter == SpotlightFilter.Recommended && isDiscoveryLoading && quickPicks.isEmpty()) {
            item(key = "discovery-loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }
        }

        // The Podcasts page is a library of shows: the newest episodes as a
        // carousel to pick from, then every show as its artwork, which opens
        // that show's whole run of episodes.
        if (filter == SpotlightFilter.Podcasts) {
            if (newEpisodes.isNotEmpty()) {
                item(key = "podcast-new-header") {
                    SpotlightSectionHeader(
                        title = stringResource(R.string.home_section_new_episodes),
                        subtitle = stringResource(R.string.spotlight_caption_podcasts),
                    )
                }
                item(key = "podcast-new") {
                    SpotlightShelf(
                        items = newEpisodes.take(PODCAST_CAROUSEL_EPISODES).map {
                            ShelfItem(
                                it.id,
                                it.title,
                                it.artist,
                                it.albumArtUri?.toString() ?: it.thumbnailUrl,
                                it.albumArtUri?.toString() ?: it.highResThumbnailUrl,
                            )
                        },
                        onClick = { id -> newEpisodes.find { it.id == id }?.let { onPlaySongs(newEpisodes, it) } },
                    )
                }
            }
            if (podcastFeeds.isNotEmpty()) {
                item(key = "podcast-shows-header") {
                    SpotlightSectionHeader(
                        title = stringResource(R.string.spotlight_your_shows),
                        subtitle = stringResource(R.string.spotlight_your_shows_sub),
                    )
                }
                item(key = "podcast-shows") {
                    PodcastShowGrid(podcastFeeds.sortedBy { it.title.lowercase() }, onOpen = { openShow = it })
                }
            }
        }

        if (quickPicks.isNotEmpty() && filter != SpotlightFilter.Podcasts) {
            item(key = "quick-header") {
                SpotlightSectionHeader(
                    title = if (filter == SpotlightFilter.Recommended) {
                        stringResource(R.string.spotlight_new_for_you)
                    } else if (filter == SpotlightFilter.Podcasts) {
                        stringResource(R.string.home_section_new_episodes)
                    } else {
                        stringResource(R.string.spotlight_quick_picks)
                    },
                    subtitle = spotlightQuickPickCaption(filter),
                    // Spin these: the wheel opens on the pool this header
                    // is showing, beside the other way to start it.
                    onSpin = if (filter == SpotlightFilter.Podcasts) {
                        null
                    } else {
                        { onSpinClick(filter.spinSource()) }
                    },
                )
            }
            item(key = "quick-picks") {
                SpotlightQuickPicks(
                    songs = quickPicks,
                    onSongClick = { onPlaySongs(quickPicks, it) },
                    onSongLongPress = onSongLongPress,
                )
            }
        }

        // The discovery tab's shelves. A grid of songs answers "what do I
        // play next" and nothing else; a record, an hour somebody curated
        // and a whole catalogue are the other three ways people find music,
        // and none of them was reachable from this tab.
        if (filter == SpotlightFilter.Recommended) {
            if (discoveryCollections.albums.isNotEmpty()) {
                item(key = "discovery-albums-header") {
                    SpotlightSectionHeader(
                        title = stringResource(R.string.spotlight_albums_to_explore),
                        subtitle = stringResource(R.string.spotlight_albums_to_explore_sub),
                    )
                }
                item(key = "discovery-albums") {
                    SpotlightShelf(
                        items = discoveryCollections.albums.map { it.toShelfItem() },
                        onClick = { id ->
                            discoveryCollections.albums.find { it.id == id }
                                ?.let(onPlaylistClick)
                        },
                    )
                }
            }

            if (discoveryCollections.artists.isNotEmpty()) {
                item(key = "discovery-artists-header") {
                    SpotlightSectionHeader(
                        title = stringResource(R.string.spotlight_artists_for_you),
                        subtitle = stringResource(R.string.spotlight_artists_for_you_sub),
                    )
                }
                item(key = "discovery-artists") {
                    SpotlightArtistRail(
                        artists = discoveryCollections.artists,
                        onClick = { onOpenPage(HomePage.Artist(it.name, it.id.takeIf { id -> id.startsWith("UC") }), null) },
                    )
                }
            }

            if (discoveryCollections.playlists.isNotEmpty()) {
                item(key = "discovery-playlists-header") {
                    SpotlightSectionHeader(
                        title = stringResource(R.string.spotlight_playlists_to_try),
                        subtitle = stringResource(R.string.spotlight_playlists_to_try_sub),
                    )
                }
                item(key = "discovery-playlists") {
                    SpotlightShelf(
                        items = discoveryCollections.playlists.map { it.toShelfItem() },
                        onClick = { id ->
                            discoveryCollections.playlists.find { it.id == id }
                                ?.let(onPlaylistClick)
                        },
                    )
                }
            }
        }

        // The two shelves Home works out for itself, on the unfiltered
        // page only: every filter is already one view of the library.
        if (filter == SpotlightFilter.All && onRepeat.size >= ON_REPEAT_SHELF_MIN) {
            item(key = "repeat-header") {
                SpotlightSectionHeader(
                    title = stringResource(R.string.home_section_on_repeat),
                    subtitle = stringResource(R.string.home_section_on_repeat_caption),
                    actionLabel = stringResource(R.string.action_play_all),
                    onAction = { onPlaySongs(onRepeat, onRepeat.firstOrNull()) },
                )
            }
            item(key = "repeat-shelf") {
                SpotlightShelf(
                    items = onRepeat.map {
                        ShelfItem(
                            it.id,
                            it.title,
                            it.artist,
                            it.albumArtUri?.toString() ?: it.thumbnailUrl,
                            it.albumArtUri?.toString() ?: it.highResThumbnailUrl,
                        )
                    },
                    onClick = { id ->
                        onRepeat.find { it.id == id }?.let { onPlaySongs(onRepeat, it) }
                    },
                )
            }
        }

        if (filter == SpotlightFilter.All && newEpisodes.isNotEmpty()) {
            item(key = "episodes-header") {
                SpotlightSectionHeader(
                    title = stringResource(R.string.home_section_new_episodes),
                    subtitle = stringResource(R.string.spotlight_caption_podcasts),
                    actionLabel = stringResource(R.string.action_see_all),
                    onAction = { onFilterChange(SpotlightFilter.Podcasts) },
                )
            }
            item(key = "episodes-shelf") {
                SpotlightShelf(
                    items = newEpisodes.map {
                        ShelfItem(
                            it.id,
                            it.title,
                            it.artist,
                            it.albumArtUri?.toString() ?: it.thumbnailUrl,
                            it.albumArtUri?.toString() ?: it.highResThumbnailUrl,
                        )
                    },
                    onClick = { id ->
                        newEpisodes.find { it.id == id }?.let { onPlaySongs(newEpisodes, it) }
                    },
                )
            }
        }

        if (filter != SpotlightFilter.Playlists &&
            filter != SpotlightFilter.Recommended &&
            filter != SpotlightFilter.Podcasts &&
            filter.browseKey() == null &&
            recentlyPlayed.isNotEmpty()
        ) {
            item(key = "recent-header") {
                SpotlightSectionHeader(title = stringResource(R.string.home_section_jump_back_in))
            }
            item(key = "recent-shelf") {
                SpotlightShelf(
                    items = recentlyPlayed.take(SHELF_ITEMS).map {
                        ShelfItem(
                            it.id,
                            it.title,
                            it.artist,
                            it.albumArtUri?.toString() ?: it.thumbnailUrl,
                            it.albumArtUri?.toString() ?: it.highResThumbnailUrl,
                        )
                    },
                    onClick = { id ->
                        recentlyPlayed.find { it.id == id }?.let(onRecentClick)
                    },
                )
            }
        }

        if (filter == SpotlightFilter.All || filter == SpotlightFilter.Playlists) {
            if (ownPlaylists.isNotEmpty()) {
                item(key = "playlists-header") {
                    SpotlightSectionHeader(title = stringResource(R.string.spotlight_your_playlists))
                }
                item(key = "playlists-shelf") {
                    SpotlightShelf(
                        items = ownPlaylists.take(SHELF_ITEMS).map {
                            ShelfItem(
                                it.id,
                                it.name,
                                if (it.itemCount >= 0) "${it.itemCount} songs" else it.uploaderName,
                                it.thumbnailUrl,
                                com.ivor.ivormusic.data.googleImageAtSize(it.thumbnailUrl, 512),
                            )
                        },
                        onClick = { id ->
                            ownPlaylists.find { it.id == id }?.let(onPlaylistClick)
                        },
                    )
                }
            }
        }

        if (filter == SpotlightFilter.All && songs.isNotEmpty()) {
            item(key = "library-header") {
                SpotlightSectionHeader(
                    title = stringResource(R.string.spotlight_from_your_library),
                )
            }
            item(key = "library-shelf") {
                SpotlightShelf(
                    items = songs.take(SHELF_ITEMS).map {
                        ShelfItem(
                            it.id,
                            it.title,
                            it.artist,
                            it.albumArtUri?.toString() ?: it.thumbnailUrl,
                            it.albumArtUri?.toString() ?: it.highResThumbnailUrl,
                        )
                    },
                    onClick = { id -> songs.find { it.id == id }?.let(onSongClick) },
                )
            }
        }

        // A filter that turned up nothing is a real state, and a blank page
        // reads as a bug rather than as an answer.
        filter.browseKey()?.let { key ->
            val mood = musicBrowse[BROWSE_MOOD]
            val showingMood = filter == SpotlightFilter.Explore && mood != null
            musicBrowseItems(
                state = if (showingMood) mood!! else musicBrowse[key],
                moodTitle = if (showingMood) mood!!.title else null,
                onCloseMood = { viewModel.closeMood() },
                onRetry = { viewModel.loadMusicBrowse(if (showingMood) BROWSE_MOOD else key, force = true) },
                onLoadMore = { viewModel.loadMoreMusicBrowse(if (showingMood) BROWSE_MOOD else key) },
                onPlaylistClick = onPlaylistClick,
                onArtistClick = { onOpenPage(HomePage.Artist(it.name, it.id.takeIf { id -> id.startsWith("UC") }), null) },
                onPlayTracks = onPlaySongs,
                onMoodClick = { viewModel.openMood(it) },
                onSongLongPress = onSongLongPress,
            )
        }

        if (filter.browseKey() == null && !isInitialLoading && !isDiscoveryLoading && quickPicks.isEmpty() &&
            (filter != SpotlightFilter.Recommended || discoveryCollections.isEmpty()) &&
            (filter != SpotlightFilter.Playlists || ownPlaylists.isEmpty()) &&
            (filter != SpotlightFilter.Artists || libArtists.isEmpty()) &&
            (filter != SpotlightFilter.Albums || libAlbums.isEmpty())
        ) {
            item(key = "empty") { SpotlightEmptyState(filter) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ExpressivePullToRefresh(
            // The discovery tab has its own fetch, so its own spinner: pulling on
            // it and watching the library reload would be the gesture doing
            // something other than what the screen is showing.
            isRefreshing = (isRefreshing || isDiscoveryLoading) && !isInitialLoading,
            onRefresh = {
                val browseKey = filter.browseKey()
                if (browseKey != null) {
                    viewModel.loadMusicBrowse(browseKey, force = true)
                } else if (filter == SpotlightFilter.Recommended) {
                    viewModel.loadDiscovery(force = true)
                } else {
                    viewModel.refresh(excludedFolders, manualScan)
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = contentPadding,
            ) {
                item(key = "topbar") {
                    TopBarSection(
                        onProfileClick = onProfileClick,
                        onSettingsClick = onSettingsClick,
                        onDownloadsClick = onDownloadsClick,
                        isDarkMode = isDarkMode,
                        viewModel = viewModel,
                        videoMode = videoMode,
                        onVideoModeToggle = onVideoModeToggle,
                        showModeToggle = showModeToggle,
                        modeToggleState = modeToggleState,
                    )
                }

                // Search at the top of Home, searching as it is typed into:
                // while there is a query, its results take the tiles' place.
                item(key = "search") {
                    HomeSearchField(homeSearch, Modifier.padding(top = 4.dp, bottom = 6.dp))
                }
                if (homeSearch.active) {
                    homeSearchResults(
                        homeSearch,
                        onPlaySongs = onPlaySongs,
                        onSongLongPress = onSongLongPress,
                        onOpenPage = { page -> onOpenPage(page, null) },
                    )
                } else item(key = "blocks") {
                    BlocksHome(
                        likedCount = likedSongs.size,
                        newEpisodes = newEpisodes,
                        recent = recentlyPlayed,
                        onOpen = openBlockPage,
                        onRecent = onRecentClick,
                        modifier = Modifier.padding(top = 4.dp),
                        onBounds = { kind, bounds -> blockBounds[kind] = bounds },
                        djColors = djColors,
                        djTitle = djTitle,
                        faces = blockFaces,
                    )
                }

                item(key = "tail") { Spacer(Modifier.height(24.dp)) }
            }
        }

        val (blockColor, onBlockColor) = pageBlock.colors(MaterialTheme.colorScheme)
        BlockReveal(
            open = openBlock != null,
            origin = blockBounds[pageBlock],
            face = blockFaces?.layer(pageBlock),
            originShape = blockFaces?.shapes?.get(pageBlock),
            container = blockColor,
            content = onBlockColor,
            onDismiss = { openBlock = null },
            onClosed = {
                openShow = null
                onFilterChange(SpotlightFilter.All)
            },
        ) {
          Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = pageListState,
                modifier = Modifier.fillMaxSize(),
                // No top padding: the hero draws up under the status bar itself.
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                item(key = "block-hero") {
                    // The covers the hero is made of: the page's songs, or a
                    // playlist page's playlists. A page of shelves has none
                    // and gets the shapes alone.
                    val browseShelves = filter.browseKey()?.let { musicBrowse[it]?.shelves }.orEmpty()
                    val heroCovers = remember(quickPicks, ownPlaylists, filter, browseShelves, libArtists, libAlbums) {
                        when {
                            quickPicks.isNotEmpty() -> quickPicks.distinctBy { it.albumArtUri ?: it.thumbnailUrl }.take(HERO_COVER_COUNT).map {
                                HeroCover(
                                    it.albumArtUri?.toString() ?: it.thumbnailUrl,
                                    it.albumArtUri?.toString() ?: it.highResThumbnailUrl,
                                )
                            }
                            filter == SpotlightFilter.Playlists -> ownPlaylists.take(HERO_COVER_COUNT).map { HeroCover(it.thumbnailUrl) }
                            filter == SpotlightFilter.Artists -> libArtists.mapNotNull { it.photo }.take(HERO_COVER_COUNT).map { HeroCover(it) }
                            filter == SpotlightFilter.Albums -> libAlbums.mapNotNull { it.cover }.take(HERO_COVER_COUNT).map { HeroCover(it) }
                            // A page of shelves (New, Charts, Explore) is made of its own records.
                            else -> heroCoversFromShelves(browseShelves, HERO_COVER_COUNT)
                        }
                    }
                    BlockPageHero(
                        title = if (pageBlock == BlockKind.QuickPicks) {
                            stringResource(R.string.spotlight_quick_picks)
                        } else {
                            spotlightFilterLabel(pageBlock.filter())
                        },
                        covers = heroCovers,
                        topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                        // Podcasts are a library of shows, not a queue to start from the top.
                        onPlay = if (quickPicks.isNotEmpty() && filter != SpotlightFilter.Podcasts) {
                            { onPlaySongs(quickPicks, quickPicks.firstOrNull()) }
                        } else {
                            null
                        },
                        onShuffle = if (quickPicks.size > 1 && filter != SpotlightFilter.Podcasts) {
                            { onShuffleSongs(quickPicks) }
                        } else {
                            null
                        },
                    )
                }
                pageSections()
                item(key = "tail") { Spacer(Modifier.height(24.dp)) }
            }
            // A show opened from the page slides in over it, in the page's
            // colors; Back or its own back arrow returns to the page.
            var shownShow by remember { mutableStateOf<com.ivor.ivormusic.data.PodcastFeed?>(null) }
            if (openShow != null) shownShow = openShow
            androidx.activity.compose.BackHandler(enabled = openShow != null) { openShow = null }
            androidx.compose.animation.AnimatedVisibility(
                visible = openShow != null,
                enter = androidx.compose.animation.slideInHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
                    androidx.compose.animation.fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = androidx.compose.animation.slideOutHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
                    androidx.compose.animation.fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                shownShow?.let { feed ->
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        com.ivor.ivormusic.ui.podcast.PodcastDetailScreen(
                            feed = feed,
                            onBack = { openShow = null },
                            onPlayQueue = onPlaySongs,
                            onShuffleQueue = onShuffleSongs,
                            contentPadding = PaddingValues(
                                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding(),
                            ),
                        )
                    }
                }
            }
          }
        }
    }
}

/** The followed shows as a grid of their artwork, three to a row, each opening the show. */
@Composable
private fun PodcastShowGrid(feeds: List<com.ivor.ivormusic.data.PodcastFeed>, onOpen: (com.ivor.ivormusic.data.PodcastFeed) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (row in feeds.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (feed in row) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable { onOpen(feed) },
                    ) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            ShortcutImage(feed.artworkUrl, feed.artworkUrl)
                        }
                        Text(
                            feed.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp, end = 4.dp, bottom = 4.dp),
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** How many of the newest episodes the Podcasts page's carousel holds. */
private const val PODCAST_CAROUSEL_EPISODES = 20

/**
 * YouTube Music's own auto-generated mixes, by name.
 *
 * There is no flag on the playlist saying "we made this for you" - the only
 * signal is the title, so this matches the shapes YouTube ships: "My Supermix",
 * "Discover Mix", "New Release Mix", "Replay Mix", and the per-artist
 * "<Artist> Mix" / "My Mix 3" forms. Matched case-insensitively and anchored on
 * the word "mix", so a user's own playlist called "Late night mixtape" is kept.
 */
private val AUTO_MIX_PATTERNS = listOf(
    Regex("""^my\s+supermix$""", RegexOption.IGNORE_CASE),
    Regex("""^supermix$""", RegexOption.IGNORE_CASE),
    Regex("""^my\s+mix(\s+\d+)?$""", RegexOption.IGNORE_CASE),
    Regex("""^(discover|new\s+release|replay)\s+mix$""", RegexOption.IGNORE_CASE),
    Regex("""\bradio$""", RegexOption.IGNORE_CASE),
)

/**
 * The covers a page of shelves is made of: albums and singles first, as
 * the records the page is about, then tracks and playlists, each once.
 */
internal fun heroCoversFromShelves(shelves: List<com.ivor.ivormusic.data.MusicShelf>, count: Int): List<HeroCover> {
    val items = shelves.flatMap { it.items }
    val albums = items.filterIsInstance<com.ivor.ivormusic.data.MusicShelfItem.Collection>().filter { it.isAlbum }
        .mapNotNull { it.playlist.thumbnailUrl }
    val rest = items.mapNotNull { item ->
        when (item) {
            is com.ivor.ivormusic.data.MusicShelfItem.Collection -> if (item.isAlbum) null else item.playlist.thumbnailUrl
            is com.ivor.ivormusic.data.MusicShelfItem.Track -> item.song.highResThumbnailUrl ?: item.song.thumbnailUrl
            else -> null
        }
    }
    return (albums + rest).filter { it.isNotBlank() }.distinct().take(count).map { HeroCover(it) }
}

internal fun isAutoMix(name: String): Boolean {
    val trimmed = name.trim()
    return AUTO_MIX_PATTERNS.any { it.containsMatchIn(trimmed) }
}

private const val QUICK_PICK_ROWS = 4

/**
 * One quick-picks page per 360dp of width, at most three: a phone shows one
 * page of four rows as it always has, a tablet shows two or three columns of
 * them instead of one row stretched across the whole screen.
 */
private object QuickPickPageSize : PageSize {
    override fun androidx.compose.ui.unit.Density.calculateMainAxisPageSize(
        availableSpace: Int,
        pageSpacing: Int,
    ): Int {
        val columns = (availableSpace / 360.dp.roundToPx()).coerceIn(1, 3)
        return (availableSpace - pageSpacing * (columns - 1)) / columns
    }
}
private const val QUICK_PICK_PAGES = 3
private const val SHELF_ITEMS = 12

/** Fewer than this on repeat is a coincidence, not a shelf. */
private const val ON_REPEAT_SHELF_MIN = 3
private const val SHORTCUT_COUNT = 6

/* ------------------------------------------------------------------ */
/* Filters                                                             */
/* ------------------------------------------------------------------ */

/**
 * Deliberately not YouTube Music's mood chips. Those are a browse call, and
 * this screen resolves everything from flows that already exist, so the chips
 * scope what is genuinely on the device instead of implying a catalogue.
 */
enum class SpotlightFilter(val label: String, val quickPickCaption: String?) {
    All("All", null),

    /**
     * The one filter that is not a view of what the user already has.
     *
     * It sits second because it is a peer of All rather than a corner of the
     * screen: everything else here scopes the library, and this is the only
     * entry that answers "what should I listen to that I have not heard".
     */
    Recommended("For you", "Not in your library yet"),

    /** The newest episodes of the shows followed in the Library. */
    Podcasts("Podcasts", "Newest from the shows you follow"),
    MusicHome("Home", null),
    Explore("Explore", null),
    Charts("Charts", null),
    NewReleases("New", null),
    Liked("Liked", "From songs you liked"),
    Recent("Recent", "From what you played lately"),
    Playlists("Playlists", null),
    Artists("Artists", null),
    Albums("Albums", null),
    Downloads("Downloads", null),
}

/** The filter a block's page lists. Quick picks is the unfiltered page. */
private fun BlockKind.filter(): SpotlightFilter = when (this) {
    BlockKind.ForYou -> SpotlightFilter.Recommended
    BlockKind.Episodes -> SpotlightFilter.Podcasts
    BlockKind.Recent -> SpotlightFilter.Recent
    BlockKind.Playlists -> SpotlightFilter.Playlists
    BlockKind.Charts -> SpotlightFilter.Charts
    BlockKind.NewReleases -> SpotlightFilter.NewReleases
    BlockKind.Explore -> SpotlightFilter.Explore
    BlockKind.Artists -> SpotlightFilter.Artists
    BlockKind.Albums -> SpotlightFilter.Albums
    BlockKind.Downloads -> SpotlightFilter.Downloads
    else -> SpotlightFilter.All
}

internal fun SpotlightFilter.browseKey(): String? = when (this) {
    SpotlightFilter.MusicHome -> BROWSE_HOME
    SpotlightFilter.Explore -> BROWSE_EXPLORE
    SpotlightFilter.Charts -> BROWSE_CHARTS
    SpotlightFilter.NewReleases -> BROWSE_NEW
    else -> null
}

/** The Spin pool matching what a filter shows, so "spin these" means these. */
private fun SpotlightFilter.spinSource(): SpinSource = when (this) {
    SpotlightFilter.Recommended -> SpinSource.ForYou
    SpotlightFilter.Liked -> SpinSource.Liked
    SpotlightFilter.Recent -> SpinSource.Recent
    else -> SpinSource.Mix
}

@Composable
private fun spotlightFilterLabel(filter: SpotlightFilter): String = when (filter) {
    SpotlightFilter.All -> stringResource(R.string.filter_all)
    SpotlightFilter.Recommended -> stringResource(R.string.filter_recommended)
    SpotlightFilter.Podcasts -> stringResource(R.string.filter_podcasts)
    SpotlightFilter.Liked -> stringResource(R.string.filter_liked)
    SpotlightFilter.Recent -> stringResource(R.string.filter_recent)
    SpotlightFilter.Playlists -> stringResource(R.string.filter_playlists)
    SpotlightFilter.MusicHome -> stringResource(R.string.filter_music_home)
    SpotlightFilter.Explore -> stringResource(R.string.filter_explore)
    SpotlightFilter.Charts -> stringResource(R.string.filter_charts)
    SpotlightFilter.NewReleases -> stringResource(R.string.filter_new_releases)
    SpotlightFilter.Artists -> stringResource(R.string.section_artists)
    SpotlightFilter.Albums -> stringResource(R.string.section_albums)
    SpotlightFilter.Downloads -> stringResource(R.string.cd_downloads)
}

@Composable
private fun spotlightQuickPickCaption(filter: SpotlightFilter): String? = when (filter) {
    SpotlightFilter.Recommended -> stringResource(R.string.spotlight_new_for_you_caption)
    SpotlightFilter.Podcasts -> stringResource(R.string.spotlight_caption_podcasts)
    SpotlightFilter.Liked -> stringResource(R.string.spotlight_caption_liked)
    SpotlightFilter.Recent -> stringResource(R.string.spotlight_caption_recent)
    else -> null
}

/* ------------------------------------------------------------------ */
/* Shortcut grid                                                       */
/* ------------------------------------------------------------------ */

internal sealed interface Shortcut {
    val key: String
    val title: String

    data class Liked(val count: Int) : Shortcut {
        override val key = "liked"
        override val title = "Liked songs"
    }

    data class Playlist(val playlist: PlaylistDisplayItem) : Shortcut {
        override val key = "pl-${playlist.id}"
        override val title = playlist.name
    }

    data class Track(val song: Song) : Shortcut {
        override val key = "tr-${song.id}"
        override val title = song.title
    }
}

/**
 * Liked songs first when there are any - it is the one destination every music
 * app puts in this position - then playlists and recent tracks interleaved,
 * so the grid is not six of the same thing. (The AI DJ lives in the pill bar
 * now, not in this grid.)
 */
private fun buildShortcuts(
    liked: List<Song>,
    recent: List<Song>,
    playlists: List<PlaylistDisplayItem>,
): List<Shortcut> = buildList {
    // Position 1 is always Liked songs: locked, never moves or disappears,
    // even with an empty library.
    add(Shortcut.Liked(liked.size))
    val tracks = recent.map { Shortcut.Track(it) }
    val lists = playlists.map { Shortcut.Playlist(it) }
    var t = 0
    var l = 0
    while (size < SHORTCUT_COUNT && (t < tracks.size || l < lists.size)) {
        if (l < lists.size) add(lists[l++])
        if (size < SHORTCUT_COUNT && t < tracks.size) add(tracks[t++])
    }
}.take(SHORTCUT_COUNT)

/**
 * Two columns of wide, short tiles on a phone, three from 600dp of page, so
 * the six shortcuts are two rows rather than three across a tablet and each
 * tile stays a readable shape instead of a 500dp-wide bar. Built from Rows
 * rather than a LazyGrid, because a lazy grid nested in this LazyColumn has
 * unbounded height - the same constraint PlayerStylePicker works around.
 */
@Composable
private fun SpotlightShortcutGrid(
    shortcuts: List<Shortcut>,
    onClick: (Shortcut) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val columns = if (maxWidth >= 600.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shortcuts.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { shortcut ->
                        SpotlightShortcutTile(
                            shortcut = shortcut,
                            onClick = { onClick(shortcut) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // A short last row must not stretch its tiles across it.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SpotlightShortcutTile(
    shortcut: Shortcut,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(60.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(60.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (shortcut) {
                    is Shortcut.Liked -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    is Shortcut.Playlist -> ShortcutImage(
                        shortcut.playlist.thumbnailUrl,
                        com.ivor.ivormusic.data.googleImageAtSize(shortcut.playlist.thumbnailUrl, 512),
                    )
                    is Shortcut.Track -> ShortcutImage(
                        shortcut.song.albumArtUri?.toString() ?: shortcut.song.thumbnailUrl,
                        shortcut.song.albumArtUri?.toString() ?: shortcut.song.highResThumbnailUrl,
                    )
                }
            }
            Text(
                text = if (shortcut is Shortcut.Liked) stringResource(R.string.shortcut_liked_songs) else shortcut.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
    }
}

/**
 * Artwork for a shortcut tile or a shelf card.
 *
 * Goes through [VideoThumbnail] rather than a bare `AsyncImage` for the same
 * reasons the feed cards do: the sharper URL is layered over the one the feed
 * returned so a 404 upstairs is invisible, a dropped fetch is retried twice
 * instead of leaving a permanently blank card, and the Material 3 loading
 * indicator is held off long enough that a memory-cache hit never flashes one
 * during a fling.
 */
@Composable
private fun ShortcutImage(model: String?, highRes: String? = null) {
    com.ivor.ivormusic.ui.components.VideoThumbnail(
        thumbnailUrl = model,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        highResThumbnailUrl = highRes,
        contentScale = ContentScale.Crop,
        indicatorSize = 24.dp,
        emptyIcon = Icons.AutoMirrored.Rounded.QueueMusic,
        placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

/* ------------------------------------------------------------------ */
/* Quick picks                                                         */
/* ------------------------------------------------------------------ */

/**
 * Four song rows per page, paged horizontally.
 *
 * Paging rather than free scrolling is the whole point: it snaps, so the
 * gesture resolves to one page or the next instead of leaving the reader
 * halfway between two, and it never steals a vertical drag the way a
 * free-scrolling carousel nested in a scrolling page does.
 */
@Composable
private fun SpotlightQuickPicks(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null,
) {
    val pages = songs.chunked(QUICK_PICK_ROWS)
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 8.dp,
            // Several pages side by side on a wide page, still snapping one
            // page at a time.
            pageSize = QuickPickPageSize,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                pages[page].forEach { song ->
                    SpotlightQuickPickRow(
                        song = song,
                        onClick = { onSongClick(song) },
                        onLongClick = onSongLongPress?.let { press -> { press(song) } }
                    )
                }
            }
        }

        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotlightQuickPickRow(
    song: Song,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .songRowClick(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            // A quick-pick row draws at 48dp, so the feed's own thumbnail is
            // already enough: asking for 1080 here would fetch a megabyte to
            // fill a thumbnail and evict the covers that do need it.
            ShortcutImage(song.albumArtUri?.toString() ?: song.thumbnailUrl)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Shelves                                                             */
/* ------------------------------------------------------------------ */

internal data class ShelfItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val artwork: String?,
    /**
     * The same picture asked for at a size worth drawing. Every shelf card is
     * 140dp - 420 physical pixels on a 3x screen - and a feed thumbnail
     * arrives at 120px or less, so the low one alone is being blown up three
     * to four times. Drawn over [artwork] rather than instead of it, because
     * `maxresdefault` does not exist for every video and a channel may have no
     * large avatar: a miss then costs sharpness, not an empty card.
     */
    val artworkHighRes: String? = null,
)

/**
 * The artwork row every music home is mostly made of. Cards are large enough
 * that the art is the content rather than a thumbnail, which is the difference
 * between a shelf and a list with pictures.
 */
@Composable
internal fun SpotlightShelf(items: List<ShelfItem>, onClick: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onClick(item.id) }
                    .padding(bottom = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    ShortcutImage(item.artwork, item.artworkHighRes)
                }
                Column(
                    modifier = Modifier.padding(
                        start = 8.dp,
                        top = 8.dp,
                        end = 8.dp,
                        bottom = 10.dp,
                    ),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * A playlist or album as a shelf card.
 *
 * The high-res field is the point: an InnerTube search hands back covers at
 * whatever size the surface it was built for wanted - often 120px - and a shelf
 * card is 140dp, which is 420 physical pixels on a 3x screen. Asking for the
 * size actually being drawn is what separates a cover from a blur.
 */
private fun PlaylistDisplayItem.toShelfItem(): ShelfItem = ShelfItem(
    id = id,
    title = name,
    subtitle = when {
        uploaderName.isNotBlank() -> uploaderName
        itemCount >= 0 -> "$itemCount songs"
        else -> ""
    },
    artwork = thumbnailUrl,
    artworkHighRes = com.ivor.ivormusic.data.googleImageAtSize(thumbnailUrl, 512),
)

/**
 * Artists as a rail of circles.
 *
 * Round rather than the square shelf card above, and that is the whole reason
 * it is a separate composable: a person is drawn as a circle everywhere else in
 * this app - the account avatar, the channel page, the artist page it opens -
 * and a square card of a face in a row of square records reads as another
 * album. The name sits centred under the circle with the subscriber count
 * beneath it, so the rail scans as "people" at a glance.
 */
@Composable
internal fun SpotlightArtistRail(
    artists: List<com.ivor.ivormusic.data.ArtistItem>,
    onClick: (com.ivor.ivormusic.data.ArtistItem) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(artists, key = { it.id }) { artist ->
            Column(
                modifier = Modifier
                    .width(112.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onClick(artist) }
                    // Sides as well as ends: a name or subscriber count long
                    // enough to span the card otherwise ran into the rounded
                    // clip, which shaved its first and last letters.
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    com.ivor.ivormusic.ui.components.AvatarImage(
                        url = artist.thumbnailUrl,
                        contentDescription = null,
                        targetPx = 288,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                artist.subscriberCount?.takeIf { it.isNotBlank() }?.let { subscribers ->
                    Text(
                        text = subscribers,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Chrome                                                              */
/* ------------------------------------------------------------------ */

@Composable
internal fun SpotlightSectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onSpin: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onSpin != null) {
            SpinButton(onClick = onSpin, size = 40.dp)
            Spacer(Modifier.width(8.dp))
        }
        if (actionLabel != null && onAction != null) {
            Surface(
                onClick = onAction,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotlightEmptyState(filter: SpotlightFilter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = when (filter) {
                SpotlightFilter.Liked -> Icons.Rounded.Favorite
                SpotlightFilter.Recommended -> Icons.Rounded.AutoAwesome
                SpotlightFilter.Podcasts -> Icons.Rounded.Podcasts
                else -> Icons.Rounded.History
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = when (filter) {
                SpotlightFilter.Liked -> stringResource(R.string.spotlight_empty_no_liked)
                SpotlightFilter.Recent -> stringResource(R.string.spotlight_empty_no_recent)
                SpotlightFilter.Playlists -> stringResource(R.string.spotlight_empty_no_playlists)
                SpotlightFilter.Podcasts -> stringResource(R.string.spotlight_empty_no_podcasts)
                SpotlightFilter.Recommended -> stringResource(R.string.spotlight_discovery_empty)
                else -> stringResource(R.string.spotlight_empty_generic)
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (filter) {
                SpotlightFilter.Liked -> stringResource(R.string.spotlight_hint_liked)
                SpotlightFilter.Playlists -> stringResource(R.string.spotlight_hint_playlists)
                SpotlightFilter.Podcasts -> stringResource(R.string.spotlight_hint_podcasts)
                SpotlightFilter.Recommended -> stringResource(R.string.spotlight_discovery_empty_sub)
                else -> stringResource(R.string.spotlight_hint_all)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
