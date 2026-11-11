package com.ivor.ivormusic.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.home.BlockKind
import com.ivor.ivormusic.ui.home.BlockPageHeader
import com.ivor.ivormusic.ui.home.BlockReveal
import com.ivor.ivormusic.ui.home.BlocksHome
import com.ivor.ivormusic.ui.home.SpotlightSectionHeader
import com.ivor.ivormusic.ui.home.colors
import com.ivor.ivormusic.ui.theme.IvorMusicTheme
import com.ivor.ivormusic.ui.theme.RONDO_PALETTE_ID
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

    private fun shoot(name: String, dark: Boolean, page: BlockKind? = null, recent: Int = 4) {
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
                    )
                    // A block's page, open: the block's color as the ground and
                    // everything on it drawn in the scheme made from that color.
                    val (ground, ink) = (page ?: BlockKind.ForYou).colors(MaterialTheme.colorScheme)
                    BlockReveal(open = page != null, origin = null, container = ground, content = ink, onDismiss = {}) {
                        Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
                            BlockPageHeader("For you", onBack = {})
                            SpotlightSectionHeader(
                                title = "New for you",
                                subtitle = "Songs you have not played",
                                actionLabel = "Play all",
                                onAction = {},
                            )
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
                            Button(onClick = {}, modifier = Modifier.padding(20.dp)) { Text("Shuffle") }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun blocks_dark() = shoot("blocks_dark", dark = true)

    @Test
    fun blocks_light() = shoot("blocks_light", dark = false, recent = 2)

    @Test
    fun block_page_dark() = shoot("block_page_dark", dark = true, page = BlockKind.ForYou)

    @Test
    fun block_page_light() = shoot("block_page_light", dark = false, page = BlockKind.Episodes)
}
