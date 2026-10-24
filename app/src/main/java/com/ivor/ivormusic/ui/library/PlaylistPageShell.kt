package com.ivor.ivormusic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.ui.theme.MontserratFamily
import com.ivor.ivormusic.ui.theme.windowDpSize

// The playlist page's visual shell, shared by the music PlaylistDetailScreen
// and the AI DJ page so the two pages cannot drift apart again. The hero
// banner, the title block and the ground are the page's look; anything that
// changes here changes on both pages by construction.

/**
 * The hero banner: the cover fills the top of the page edge to edge under
 * the status bar, then dissolves into the page's own tint. Scroll-driven
 * parallax (the cover trails the list) and a plain ground-color fade at the
 * foot — never a mask, which once composited to black instead of
 * transparency. The title sits *below* the banner, never on it: a cover is
 * any image at all, and no scrim tuning makes type over it reliable.
 *
 * [heroContent] is the cover itself (a photo, or the DJ page's abstract art);
 * [onArtClick] makes the whole banner a target (a local playlist's cover
 * picker); [heroBadge] is the corner affordance on the banner.
 */
@Composable
internal fun PlaylistHeroBanner(
    headerCollapse: State<Float>,
    onArtClick: (() -> Unit)? = null,
    heroBadge: @Composable BoxScope.() -> Unit = {},
    heroContent: @Composable BoxScope.() -> Unit,
) {
    val ground = playlistPageGround()
    val windowHeight = windowDpSize().height
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Near-square on a phone, but never more than a comfortable share
        // of the window: the title, the controls and the first track all
        // have to fit under it, in landscape as much as in portrait.
        val heroHeight = (maxWidth * 0.94f)
            .coerceAtMost(windowHeight * 0.44f)
            .coerceAtLeast(220.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                // The cover drifts under the scroll; clipped here so it
                // never draws over the list.
                .clipToBounds()
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        // Scroll-driven, not time-driven: the cover trails
                        // the list rather than animating on its own, so
                        // there is nothing here to reduce.
                        val collapse = headerCollapse.value
                        translationY = collapse * size.height * 0.24f
                        alpha = 1f - collapse * 0.55f
                    }
                    .then(
                        if (onArtClick != null) {
                            Modifier.clickable(onClick = onArtClick)
                        } else Modifier
                    )
            ) {
                heroContent()
            }

            // Where the cover hands the page over: a plain fade, in the
            // page's exact ground color. Painting the fade is only correct
            // because the ground is one flat color: there is exactly one
            // value to match, so a plain SrcOver gradient lands on it
            // precisely, with no blend mode and no offscreen buffer.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to ground.copy(alpha = 0f),
                            0.58f to ground.copy(alpha = 0f),
                            0.86f to ground.copy(alpha = 0.85f),
                            1f to ground
                        )
                    )
            )

            // Top fade: back, search and the overflow need ground to sit
            // on whatever the cover is. Faded to the same color at zero
            // alpha rather than to Transparent, which is transparent
            // *black* and greys the band in a light theme.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                ground.copy(alpha = 0.55f),
                                ground.copy(alpha = 0f)
                            )
                        )
                    )
            )

            heroBadge()
        }
    }
}

/**
 * Title and facts, set the way the artist page sets a name: poster face,
 * start-aligned, sized down rather than cut when the name is a long one.
 * Stands on the page's ground, never on a theme surface.
 */
@Composable
internal fun PlaylistPageTitle(
    title: String,
    facts: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PLAYLIST_GUTTER)
            .padding(top = 14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall.copy(
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02).em,
                lineHeight = 1.08.em
            ),
            color = playlistOnGround(),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            // Big for "Focus", smaller rather than cut for
            // "Songs to sing in the shower at 3am".
            autoSize = TextAutoSize.StepBased(
                minFontSize = 24.sp,
                maxFontSize = 38.sp,
                stepSize = 2.sp
            )
        )
        Text(
            text = facts,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = playlistOnGround().copy(alpha = 0.8f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
