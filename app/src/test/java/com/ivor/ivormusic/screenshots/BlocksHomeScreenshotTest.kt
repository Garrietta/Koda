package com.ivor.ivormusic.screenshots

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.home.BlockKind
import com.ivor.ivormusic.ui.home.BlockPageHero
import com.ivor.ivormusic.ui.home.albumGridItems
import com.ivor.ivormusic.ui.home.homeSearchResults
import com.ivor.ivormusic.ui.home.HERO_BUTTON_HEIGHT
import com.ivor.ivormusic.ui.home.BlockReveal
import com.ivor.ivormusic.ui.home.BlocksHome
import com.ivor.ivormusic.ui.home.SpotlightSectionHeader
import com.ivor.ivormusic.ui.home.colors
import com.ivor.ivormusic.ui.theme.IvorMusicTheme
import com.ivor.ivormusic.ui.theme.RONDO_PALETTE_ID
import com.ivor.ivormusic.ui.theme.withVividRoles
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws Rondo's Home, the blocks, on the JVM so a change to it can be
 * looked at without a phone:
 *
 *     ./gradlew recordRoborazziDebug --tests '*BlocksHomeScreenshotTest'
 *
 * writes PNGs to app/build/outputs/roborazzi. The page is drawn from
 * plain lists, with no network and no ViewModel, so covers are their
 * placeholders. Motion, real artwork and insets still need the phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one starts background work the JVM has no use for.
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi", application = android.app.Application::class)
class BlocksHomeScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun song(index: Int) =
        Song.fromYouTube("song$index", "Song $index", "Artist $index", "Album", 200_000L, null)

    private fun shoot(name: String, dark: Boolean, page: BlockKind? = null, recent: Int = 4, djTitle: String? = null) {
        // Something is always animating in the theme, so the clock is driven by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IvorMusicTheme(darkTheme = dark, colorPalette = RONDO_PALETTE_ID) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    BlocksHome(
                        likedCount = 482,
                        newEpisodes = List(3, ::song),
                        recent = List(recent, ::song),
                        onOpen = {},
                        onRecent = {},
                        modifier = Modifier.padding(top = 24.dp),
                        djColors = com.ivor.ivormusic.data.djMoodHue(djTitle)?.let { com.ivor.ivormusic.ui.theme.vividBlock(it) },
                        djTitle = djTitle,
                    )
                    // A block's page, open: the block's color as the ground and
                    // everything on it drawn in the scheme made from that color.
                    val (ground, ink) = (page ?: BlockKind.ForYou).colors(MaterialTheme.colorScheme)
                    BlockReveal(open = page != null, origin = null, container = ground, content = ink, onDismiss = {}) {
                        Column(Modifier.fillMaxSize()) {
                            BlockPageHero(
                                title = "For you",
                                covers = emptyList(),
                                topInset = 24.dp,
                                onPlay = {},
                                onShuffle = {},
                            )
                            SpotlightSectionHeader(title = "New for you", subtitle = "Songs you have not played")
                            for (index in 1..4) {
                                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                    Text("Song $index", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Artist $index",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /**
     * Home on a phone as steps: the bar and search fixed (stood in for here
     * by a band of the right height), two blocks under them, the pager on
     * [page] and [fraction] of the way into the next step - 0 settled,
     * 0.5 halfway through a turn, where the top block is falling back and
     * the next is opening into the bottom slot.
     */
    private fun steps(name: String, page: Int, fraction: Float, dark: Boolean = true, playing: Boolean = true) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IvorMusicTheme(darkTheme = dark, colorPalette = RONDO_PALETTE_ID) {
                val steps = androidx.compose.runtime.remember { com.ivor.ivormusic.ui.home.HomeSteps() }
                // After the stepper's first frame, which lands the blocks on their target.
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    androidx.compose.runtime.withFrameNanos { }
                    steps.position.snapTo(page + fraction)
                }
                // The bar's lower edge, from the screen's foot: what Home passes as stepsBottom.
                val barBottom = 30.dp
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(150.dp).padding(20.dp)) {
                            Text("Saturday evening", style = MaterialTheme.typography.titleLarge)
                        }
                        BlocksHome(
                            likedCount = 614,
                            newEpisodes = List(12, ::song),
                            recent = List(4, ::song),
                            onOpen = {},
                            onRecent = {},
                            modifier = Modifier.weight(1f).padding(top = 4.dp, bottom = if (playing) barBottom else 24.dp),
                            djColors = com.ivor.ivormusic.data.djMoodHue("Midnight Drift")?.let { com.ivor.ivormusic.ui.theme.vividBlock(it) },
                            djTitle = "Midnight Drift: Smoke & Timberline",
                            stepped = true,
                            nowPlaying = if (playing) com.ivor.ivormusic.ui.home.NowPlaying(song(7), true, {}, {}, {}) else null,
                            steps = steps,
                        )
                    }
                    // A stand-in for the player's bar, fading as Home's player will.
                    if (playing) {
                        Box(
                            Modifier
                                .align(androidx.compose.ui.Alignment.BottomCenter)
                                .padding(start = 16.dp, end = 16.dp, bottom = barBottom)
                                .fillMaxWidth()
                                .height(68.dp)
                                .graphicsLayer { alpha = 1f - steps.morph; translationY = -steps.morph * 24.dp.toPx() }
                                .background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape)
                                .padding(horizontal = 24.dp),
                            contentAlignment = androidx.compose.ui.Alignment.CenterStart,
                        ) {
                            Text("Song 7 · Artist 7", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun steps_0() = steps("steps_0", 0, 0f)
    @Test fun steps_0_half() = steps("steps_0_half", 0, 0.5f)
    @Test fun steps_1() = steps("steps_1", 1, 0f)
    @Test fun steps_2() = steps("steps_2", 2, 0f)
    /** The step before the last: nothing of the Now playing block may show around the bar. */
    @Test fun steps_3() = steps("steps_3", 3, 0f)
    @Test fun steps_3_half() = steps("steps_3_half", 3, 0.5f)
    @Test fun steps_last() = steps("steps_last", 4, 0f)
    @Test fun steps_nothing_playing() = steps("steps_nothing_playing", 0, 0f, playing = false)
    @Test fun steps_light() = steps("steps_light", 0, 0f, dark = false)

    /**
     * The player's colors for covers of nine colors: the pill and the
     * three blocks each cover gives, on the app's own ground.
     */
    @Test
    fun player_colors() {
        compose.mainClock.autoAdvance = false
        val covers = listOf(
            0xFFD32F2F, 0xFFF57C00, 0xFFFBC02D, 0xFF388E3C, 0xFF00897B,
            0xFF1E88E5, 0xFF5E35B1, 0xFFD81B60, 0xFF6D4C41,
        ).map { androidx.compose.ui.graphics.Color(it) }
        compose.setContent {
            IvorMusicTheme(darkTheme = true, colorPalette = RONDO_PALETTE_ID) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                ) {
                    for (cover in covers) {
                        val s = MaterialTheme.colorScheme.withVividRoles(cover, null, dark = true)
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(36.dp).background(cover, androidx.compose.foundation.shape.CircleShape))
                            for ((fill, ink, w) in listOf(
                                Triple(s.primaryContainer, s.onPrimaryContainer, 170),
                                Triple(s.secondaryContainer, s.onSecondaryContainer, 64),
                                Triple(s.tertiaryContainer, s.onTertiaryContainer, 64),
                            )) {
                                Box(
                                    Modifier.size(w.dp, 52.dp).background(fill, androidx.compose.foundation.shape.RoundedCornerShape(26.dp)),
                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                ) { Text(if (w > 100) "Now playing" else "Aa", color = ink, style = MaterialTheme.typography.titleMedium) }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/player_colors.png")
    }

    /**
     * A page early in its opening: the For you tile's own face still drawn
     * where the tile is, over the page growing out from under it.
     */
    @Test
    fun block_page_opening() {
        compose.mainClock.autoAdvance = false
        val openState = androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {
            IvorMusicTheme(darkTheme = false, colorPalette = RONDO_PALETTE_ID) {
                val faces = com.ivor.ivormusic.ui.home.rememberBlockFaces()
                val bounds = androidx.compose.runtime.remember { mutableMapOf<BlockKind, androidx.compose.ui.geometry.Rect>() }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    BlocksHome(
                        likedCount = 482, newEpisodes = List(3, ::song), recent = List(4, ::song),
                        onOpen = {}, onRecent = {}, modifier = Modifier.padding(top = 24.dp),
                        onBounds = { kind, rect -> bounds[kind] = rect }, faces = faces,
                    )
                    val (ground, ink) = BlockKind.ForYou.colors(MaterialTheme.colorScheme)
                    BlockReveal(
                        open = openState.value, origin = bounds[BlockKind.ForYou], container = ground, content = ink,
                        onDismiss = {}, face = faces.layer(BlockKind.ForYou),
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            BlockPageHero(title = "For you", covers = emptyList(), topInset = 24.dp, onPlay = {}, onShuffle = {})
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        openState.value = true
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(48)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/block_page_opening.png")
        compose.mainClock.advanceTimeBy(64)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/block_page_opening_2.png")
    }

    /**
     * The DJ page's header: a long mix name shrinks onto two lines, and
     * New mix and Download join Play all and Shuffle as filled pills,
     * wrapping to a second line.
     */
    @Test
    fun dj_page_header() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IvorMusicTheme(darkTheme = false, colorPalette = RONDO_PALETTE_ID) {
                val (ground, ink) = BlockKind.Dj.colors(MaterialTheme.colorScheme)
                BlockReveal(open = true, origin = null, container = ground, content = ink, onDismiss = {}) {
                    Column(Modifier.fillMaxSize()) {
                        BlockPageHero(
                            title = "Midnight Drift: Smoke & Timberline",
                            covers = List(6) { com.ivor.ivormusic.ui.home.HeroCover("https://example.invalid/$it.jpg") },
                            topInset = 24.dp,
                            onPlay = {},
                            onShuffle = {},
                            extras = {
                                androidx.compose.material3.FilledTonalButton(onClick = {}, modifier = Modifier.height(HERO_BUTTON_HEIGHT), contentPadding = com.ivor.ivormusic.ui.home.HERO_BUTTON_PADDING) { Text("New mix") }
                                androidx.compose.material3.FilledTonalButton(onClick = {}, modifier = Modifier.height(HERO_BUTTON_HEIGHT), contentPadding = com.ivor.ivormusic.ui.home.HERO_BUTTON_PADDING) { Text("Download") }
                            },
                            pillsPerRow = 2,
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/dj_page_header.png")
    }

    /** A page with many records: every shape of the hero filled, the small ones in the gaps. */
    @Test
    fun block_page_dense_hero() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IvorMusicTheme(darkTheme = false, colorPalette = RONDO_PALETTE_ID) {
                val (ground, ink) = BlockKind.NewReleases.colors(MaterialTheme.colorScheme)
                BlockReveal(open = true, origin = null, container = ground, content = ink, onDismiss = {}) {
                    Column(Modifier.fillMaxSize()) {
                        BlockPageHero(
                            title = "New",
                            covers = List(10) { com.ivor.ivormusic.ui.home.HeroCover("https://example.invalid/$it.jpg") },
                            topInset = 24.dp,
                            onPlay = null,
                            onShuffle = null,
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/block_page_dense_hero.png")
    }

    /**
     * Your Likes, opened from the Liked tile: the tile's own dark ground
     * and the scheme made from it, Download as a third pill, and the list
     * headed by the count and running time with Edit in the page's ink.
     */
    @Test
    fun liked_page() = likedPage("liked_page", dark = false)

    /** Your Likes in dark mode: the tile turned inside out, a black page written in the Liked tile's cream. */
    @Test
    fun liked_page_dark() = likedPage("liked_page_dark", dark = true)

    private fun likedPage(name: String, dark: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IvorMusicTheme(darkTheme = dark, colorPalette = RONDO_PALETTE_ID) {
                val (ground, ink) = BlockKind.Liked.colors(MaterialTheme.colorScheme)
                BlockReveal(open = true, origin = null, container = ground, content = ink, onDismiss = {}) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.ivor.ivormusic.ui.library.LocalPageGround provides com.ivor.ivormusic.ui.home.LocalBlockTile.current,
                    ) {
                        Column(Modifier.fillMaxSize().background(com.ivor.ivormusic.ui.library.playlistPageGround())) {
                            BlockPageHero(
                                title = "Your Likes",
                                covers = List(8) { com.ivor.ivormusic.ui.home.HeroCover("https://example.invalid/$it.jpg") },
                                topInset = 24.dp,
                                onPlay = {},
                                onShuffle = {},
                                extras = {
                                    androidx.compose.material3.FilledTonalButton(onClick = {}, modifier = Modifier.height(HERO_BUTTON_HEIGHT), contentPadding = com.ivor.ivormusic.ui.home.HERO_BUTTON_PADDING) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Download, null, Modifier.size(20.dp)); androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp)); Text("Download") }
                                },
                            )
                            com.ivor.ivormusic.ui.library.TrackSectionHeader(
                                countLabel = com.ivor.ivormusic.ui.library.trackCountLabel(List(614) { song(it) }),
                                manageEnabled = false,
                                canEdit = true,
                                onToggleEdit = {},
                            )
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** Home unfolded: two columns, the whole width, the recent covers grown to fill their half. */
    @Test
    @Config(sdk = [35], qualifiers = "w880dp-h740dp-xhdpi", application = android.app.Application::class)
    fun blocks_wide() = shoot("blocks_wide", dark = false, djTitle = "Midnight Rain")

    /** The Albums page: the albums the songs come from, covers cut to expressive shapes. */
    @Test
    fun albums_page() {
        compose.mainClock.autoAdvance = false
        val albums = List(7) { index ->
            com.ivor.ivormusic.ui.home.LibraryAlbum("Album $index", "Artist $index", null, listOf(song(index)))
        }
        compose.setContent {
            IvorMusicTheme(darkTheme = false, colorPalette = RONDO_PALETTE_ID) {
                val (ground, ink) = BlockKind.Albums.colors(MaterialTheme.colorScheme)
                BlockReveal(open = true, origin = null, container = ground, content = ink, onDismiss = {}) {
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            BlockPageHero(title = "Albums", covers = emptyList(), topInset = 24.dp, onPlay = null, onShuffle = null)
                        }
                        albumGridItems(albums, 2) {}
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/albums_page.png")
    }

    /** An artist over Home: picture and records in shapes, Shuffle, Radio and Follow, then the shelves. */
    @Test
    fun artist_page() {
        compose.mainClock.autoAdvance = false
        val releases = List(4) { index ->
            com.ivor.ivormusic.data.PlaylistDisplayItem("Record $index", "MPREb_$index", "Artist", releaseYear = 2025 - index * 3)
        }
        compose.setContent {
            IvorMusicTheme(darkTheme = false, colorPalette = RONDO_PALETTE_ID) {
                androidx.compose.runtime.CompositionLocalProvider(com.ivor.ivormusic.ui.home.LocalPopupPage provides true) {
                    com.ivor.ivormusic.ui.artist.ArtistPopupOverview(
                        name = "Tyler Childers",
                        photo = null,
                        isLoading = false,
                        topSongs = List(3, ::song),
                        canSeeAll = true,
                        onSeeAll = {},
                        onPlaySong = {},
                        onSongLongPress = {},
                        deviceSongs = emptyList(),
                        onPlayDeviceSong = {},
                        albums = releases,
                        singles = emptyList(),
                        localAlbums = emptyList(),
                        similar = listOf(com.ivor.ivormusic.data.SimilarArtist("UC1", "The Red Clay Strays")),
                        featured = emptyList(),
                        about = "From Kentucky.",
                        facts = listOf("2.2M monthly audience"),
                        isFollowing = false,
                        onFollowToggle = {},
                        onShuffle = {},
                        onRadio = {},
                        onOpenRelease = {},
                        onOpenLocalAlbum = { _, _ -> },
                        onOpenArtist = { _, _ -> },
                        onOpenPlaylist = {},
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/artist_page.png")
    }

    /** An album over Home: its cover large in an expressive shape, what it is under its name, the pills. */
    @Test
    fun album_page_header() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IvorMusicTheme(darkTheme = true, colorPalette = RONDO_PALETTE_ID) {
                val (ground, ink) = BlockKind.Albums.colors(MaterialTheme.colorScheme)
                BlockReveal(open = true, origin = null, container = ground, content = ink, onDismiss = {}) {
                    Column(Modifier.fillMaxSize()) {
                        BlockPageHero(
                            title = "Deep Thoughts",
                            covers = listOf(com.ivor.ivormusic.ui.home.HeroCover(null)),
                            topInset = 24.dp,
                            onPlay = {},
                            onShuffle = {},
                            subtitle = "Album  ·  Lil Durk  ·  2025  ·  17 tracks  ·  45 min",
                            singleCover = true,
                            extras = {
                                androidx.compose.material3.FilledTonalButton(onClick = {}, modifier = Modifier.height(HERO_BUTTON_HEIGHT), contentPadding = com.ivor.ivormusic.ui.home.HERO_BUTTON_PADDING) { Text("Save") }
                            },
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/album_page_header.png")
    }

    /** Home while searching: the field at the top, the results where the tiles were. */
    @Test
    fun home_search() {
        compose.mainClock.autoAdvance = false
        val search = com.ivor.ivormusic.ui.home.HomeSearchState().apply {
            query = "tyler"
            songs = List(3, ::song)
            artists = listOf(
                com.ivor.ivormusic.ui.home.SearchArtist("Tyler Childers", null, null),
                com.ivor.ivormusic.ui.home.SearchArtist("Tyler, The Creator", null, null),
            )
            albums = listOf(com.ivor.ivormusic.ui.home.HomePage.DeviceAlbum("Rustin' in the Rain"), com.ivor.ivormusic.ui.home.HomePage.DeviceAlbum("Can I Take My Hounds"))
        }
        compose.setContent {
            IvorMusicTheme(darkTheme = false, colorPalette = RONDO_PALETTE_ID) {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = 24.dp)) {
                    item { com.ivor.ivormusic.ui.home.HomeSearchField(search) }
                    homeSearchResults(search, onPlaySongs = { _, _ -> }, onSongLongPress = null, onOpenPage = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_search.png")
    }

    @Test
    fun blocks_dark() = shoot("blocks_dark", dark = true, djTitle = "A Sunny Day with Kendrick Lamar")

    @Test
    fun blocks_light() = shoot("blocks_light", dark = false, recent = 2, djTitle = "Midnight Rain")

    @Test
    fun block_page_dark() = shoot("block_page_dark", dark = true, page = BlockKind.ForYou)

    @Test
    fun block_page_light() = shoot("block_page_light", dark = false, page = BlockKind.Episodes)

    // ---- Unfolded: the steps on the left, the Now playing panel on the right ----

    /**
     * The unfolded Home at the Fold's inner screen: the blocks stepping on
     * the left three quarters (stood in for above them by a band for the bar
     * and search) and the Now playing panel down the right quarter. No cover
     * art on the JVM, so the panel keeps the scheme's container.
     */
    private fun unfolded(name: String, page: Int, fraction: Float = 0f, playing: Boolean = true) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IvorMusicTheme(darkTheme = true, colorPalette = RONDO_PALETTE_ID) {
                val steps = androidx.compose.runtime.remember { com.ivor.ivormusic.ui.home.HomeSteps() }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    androidx.compose.runtime.withFrameNanos { }
                    steps.position.snapTo(page + fraction)
                }
                androidx.compose.foundation.layout.Row(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(start = 0.dp, end = 14.dp, top = 24.dp, bottom = 24.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().height(120.dp).padding(20.dp)) {
                            Text("Sunday evening", style = MaterialTheme.typography.titleLarge)
                        }
                        BlocksHome(
                            likedCount = 614,
                            newEpisodes = List(12, ::song),
                            recent = List(6, ::song),
                            onOpen = {},
                            onRecent = {},
                            modifier = Modifier.weight(1f).padding(top = 4.dp),
                            djColors = com.ivor.ivormusic.data.djMoodHue("Midnight Drift")?.let { com.ivor.ivormusic.ui.theme.vividBlock(it) },
                            djTitle = "Midnight Drift: Smoke & Timberline",
                            stepped = true,
                            steps = steps,
                            wideSteps = true,
                            forYou = List(6) { song(it + 20) },
                            liked = List(8) { song(it + 40) },
                        )
                    }
                    val queue = List(9) { com.ivor.ivormusic.data.MusicQueueItem(id = "q$it", song = song(it + 60)) }
                    com.ivor.ivormusic.ui.home.NowPlayingPanel(
                        song = if (playing) song(7) else null,
                        isPlaying = true,
                        upNext = com.ivor.ivormusic.ui.home.upNextOf(queue, "q0"),
                        progress = { 0.35f },
                        onToggle = {},
                        onNext = {},
                        onPrevious = {},
                        onPlayQueueItem = {},
                        onOpenPlayer = {},
                        onStartDj = {},
                        onOpenLiked = {},
                        modifier = Modifier.width(260.dp).fillMaxHeight(),
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Config(qualifiers = "w830dp-h690dp-xhdpi")
    @Test fun unfolded_0() = unfolded("unfolded_0", 0)

    @Config(qualifiers = "w830dp-h690dp-xhdpi")
    @Test fun unfolded_0_half() = unfolded("unfolded_0_half", 0, 0.5f)

    @Config(qualifiers = "w830dp-h690dp-xhdpi")
    @Test fun unfolded_last() = unfolded("unfolded_last", 2)

    @Config(qualifiers = "w830dp-h690dp-xhdpi")
    @Test fun unfolded_nothing_playing() = unfolded("unfolded_nothing_playing", 0, playing = false)

    /** One turn from the first step to the second, frame by frame, to judge the masks. */
    @Test fun steps_turn_frames() {
        compose.mainClock.autoAdvance = false
        var shown by androidx.compose.runtime.mutableFloatStateOf(0f)
        compose.setContent {
            IvorMusicTheme(darkTheme = true, colorPalette = RONDO_PALETTE_ID) {
                val steps = androidx.compose.runtime.remember { com.ivor.ivormusic.ui.home.HomeSteps() }
                androidx.compose.runtime.LaunchedEffect(shown) {
                    androidx.compose.runtime.withFrameNanos { }
                    steps.position.snapTo(shown)
                }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(150.dp).padding(20.dp)) {
                            Text("Saturday evening", style = MaterialTheme.typography.titleLarge)
                        }
                        BlocksHome(
                            likedCount = 614,
                            newEpisodes = List(12, ::song),
                            recent = List(4, ::song),
                            onOpen = {},
                            onRecent = {},
                            modifier = Modifier.weight(1f).padding(top = 4.dp, bottom = 24.dp),
                            djColors = com.ivor.ivormusic.data.djMoodHue("Midnight Drift")?.let { com.ivor.ivormusic.ui.theme.vividBlock(it) },
                            djTitle = "Midnight Drift: Smoke & Timberline",
                            stepped = true,
                            steps = steps,
                        )
                    }
                }
            }
        }
        for (f in listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)) {
            shown = f
            compose.mainClock.advanceTimeBy(200)
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/steps_turn_${(f * 100).toInt()}.png")
        }
    }
}
