package com.ivor.ivormusic.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/** How many pictures one pass fetches ahead. */
private const val PREFETCH_LIMIT = 240

/** How long Home is left to draw before fetching ahead starts. */
private const val PREFETCH_DELAY_MS = 1500L

/**
 * Fetches the pictures Home is about to need into the disk cache: the
 * covers of the likes, of what was played lately and of the playlists,
 * and the artists' photos. They are not decoded into memory, so nothing
 * on screen is pushed out; a list or page opened afterwards finds them on
 * disk and draws them at once, offline too. Runs again whenever [key]
 * changes, which is when there is something new to fetch.
 */
@Composable
internal fun PrefetchArt(key: Any, urls: () -> List<String?>) {
    val context = LocalContext.current
    LaunchedEffect(key) {
        delay(PREFETCH_DELAY_MS)
        val loader = context.imageLoader
        urls().asSequence()
            .filterNotNull()
            .filter { it.isNotBlank() }
            .distinct()
            .take(PREFETCH_LIMIT)
            .forEach { url ->
                loader.enqueue(
                    ImageRequest.Builder(context)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .build(),
                )
            }
    }
}
