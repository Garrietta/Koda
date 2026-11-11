package com.ivor.ivormusic.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.home.BlocksHome
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

    private fun shoot(name: String, dark: Boolean, onRepeat: Int, recent: Int) {
        // Something is always animating in the theme, so the clock is driven by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IvorMusicTheme(darkTheme = dark, colorPalette = RONDO_PALETTE_ID) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(top = 24.dp),
                ) {
                    BlocksHome(
                        onRepeat = List(onRepeat, ::song),
                        likedCount = if (onRepeat > 0) 482 else 0,
                        newEpisodes = List(if (onRepeat > 0) 3 else 0, ::song),
                        recent = List(recent, ::song),
                        onDj = {},
                        onPlayOnRepeat = {},
                        onLiked = {},
                        onPodcasts = {},
                        onRecent = {},
                        onOpen = {},
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun blocks_dark() = shoot("blocks_dark", dark = true, onRepeat = 12, recent = 4)

    @Test
    fun blocks_light_new_listener() = shoot("blocks_light_new_listener", dark = false, onRepeat = 0, recent = 2)
}
