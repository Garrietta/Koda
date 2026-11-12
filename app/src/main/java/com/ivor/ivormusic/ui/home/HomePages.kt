package com.ivor.ivormusic.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.ivor.ivormusic.data.PlaylistDisplayItem

/**
 * Something on Home that opens as a page of its own over it: an album, a
 * playlist, an artist, Explore. Every one of them grows out of what was
 * tapped and closes by a pull down from its top or by Back, onto whatever
 * was under it, which can be another page.
 */
sealed interface HomePage {
    /** A playlist or a release, by its YouTube item; Your Likes is the `LM` one. */
    data class Playlist(val item: PlaylistDisplayItem) : HomePage

    /** An artist, by name, and by channel id when the tap knew it. */
    data class Artist(val name: String, val id: String? = null) : HomePage

    /** An album made of the device's own files, by its name. */
    data class DeviceAlbum(val name: String) : HomePage

    /** Search, and the shelves to find things on. */
    data object Explore : HomePage
}

/**
 * One open page: what it shows, where it grew from, and the colors of the
 * tile it grew out of when it came from one (Your Likes is the Liked
 * tile, grown). [open] goes false to send it back; it leaves the stack
 * once it has shrunk away.
 */
internal class HomePageEntry(
    val id: Int,
    val page: HomePage,
    val origin: Rect?,
    val tile: BlockTile?,
) {
    var open by mutableStateOf(true)
}

/** The pages open over Home, bottom first. */
internal class HomePageStack {
    val entries = mutableStateListOf<HomePageEntry>()
    private var nextId = 0

    /** The last place a finger came down, in root pixels: where a page with no tile grows from. */
    var lastTouch: Offset? = null

    fun push(page: HomePage, origin: Rect? = null, tile: BlockTile? = null) {
        entries.add(HomePageEntry(nextId++, page, origin ?: touchOrigin(), tile))
    }

    /** Sends the top page back into what it grew from. */
    fun closeTop(): Boolean {
        val top = entries.lastOrNull { it.open } ?: return false
        top.open = false
        return true
    }

    fun remove(entry: HomePageEntry) {
        entries.remove(entry)
    }

    val anyOpen: Boolean get() = entries.any { it.open }

    private fun touchOrigin(): Rect? = lastTouch?.let { Rect(it, TOUCH_ORIGIN_RADIUS) }
}

/** Half the side of the square a page grows from when it grows from a fingertip, in pixels. */
private const val TOUCH_ORIGIN_RADIUS = 36f

/**
 * Notes where every finger comes down inside this layout, in root
 * pixels, without taking the touch from what is under it.
 */
internal fun Modifier.noteTouches(stack: HomePageStack): Modifier {
    var origin = Offset.Zero
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(stack) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                stack.lastTouch = origin + down.position
            }
        }
}

/**
 * True inside a page opened over Home. Such a page has no back arrow of
 * its own: a pull down from its top closes it, and so does Back.
 */
val LocalPopupPage = compositionLocalOf { false }

/** The ground of a page's window while it grows: the tile's, or a plain one. */
internal fun HomePageEntry.windowColors(plain: Color, onPlain: Color): Pair<Color, Color> =
    tile?.let { it.ground to it.ink } ?: (plain to onPlain)
