package com.ivor.ivormusic.screenshots

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
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.home.BlockKind
import com.ivor.ivormusic.ui.home.BlockPageHero
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
    fun liked_page() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IvorMusicTheme(darkTheme = false, colorPalette = RONDO_PALETTE_ID) {
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
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/liked_page.png")
    }

    @Test
    fun blocks_dark() = shoot("blocks_dark", dark = true, djTitle = "A Sunny Day with Kendrick Lamar")

    @Test
    fun blocks_light() = shoot("blocks_light", dark = false, recent = 2, djTitle = "Midnight Rain")

    @Test
    fun block_page_dark() = shoot("block_page_dark", dark = true, page = BlockKind.ForYou)

    @Test
    fun block_page_light() = shoot("block_page_light", dark = false, page = BlockKind.Episodes)
}
