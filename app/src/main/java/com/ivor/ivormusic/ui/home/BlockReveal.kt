package com.ivor.ivormusic.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R

/*
 * A block opens as a page of its own: the tile grows until it is the
 * whole screen, keeping its color, and what is inside is drawn in a
 * scheme made from that color. Back shrinks it into the tile again.
 *
 * The page is laid out once at full size and revealed through a growing
 * rounded window, so nothing inside is measured again while it moves.
 */

/** The tile's corner at rest. The window's corner eases from this to square. */
private val TILE_CORNER = 40.dp

/** The page's content stays hidden until the window is this far open, then fades in. */
private const val CONTENT_FROM = 0.3f
private const val CONTENT_BY = 0.8f

private class RevealWindow(private val window: Rect, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(window, CornerRadius(corner)))
}

/**
 * A complete scheme for a page that sits on [container]: the block's
 * color is the ground, [content] is the ink, and every other role is a
 * mix of the two. Controls invert (ink with the ground showing through)
 * so a button reads on the color whichever block it is.
 */
internal fun ColorScheme.onBlock(container: Color, content: Color): ColorScheme {
    fun step(amount: Float) = lerp(container, content, amount)
    return copy(
        background = container,
        onBackground = content,
        surface = container,
        onSurface = content,
        surfaceVariant = step(0.12f),
        onSurfaceVariant = step(0.78f),
        surfaceDim = container,
        surfaceBright = step(0.10f),
        surfaceContainerLowest = container,
        surfaceContainerLow = step(0.05f),
        surfaceContainer = step(0.09f),
        surfaceContainerHigh = step(0.13f),
        surfaceContainerHighest = step(0.17f),
        primary = content,
        onPrimary = container,
        primaryContainer = content,
        onPrimaryContainer = container,
        secondary = content,
        onSecondary = container,
        secondaryContainer = step(0.17f),
        onSecondaryContainer = content,
        tertiary = content,
        onTertiary = container,
        tertiaryContainer = step(0.17f),
        onTertiaryContainer = content,
        inverseSurface = content,
        inverseOnSurface = container,
        inversePrimary = container,
        outline = step(0.55f),
        outlineVariant = step(0.24f),
        surfaceTint = content,
    )
}

/**
 * Shows [page] growing out of [origin], the tile's bounds in the root,
 * while [open] is true, and shrinking back into it when it turns false.
 * [onClosed] runs when the page has finished shrinking, which is the
 * moment to let go of whatever the page was showing. With no [origin]
 * the page grows from the middle of the screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BlockReveal(
    open: Boolean,
    origin: Rect?,
    container: Color,
    content: Color,
    onDismiss: () -> Unit,
    onClosed: () -> Unit = {},
    page: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(false) }
    // The tile and its colors are held from the moment it opened, so the
    // page shrinks back to where it came from in the color it had.
    var from by remember { mutableStateOf<Rect?>(null) }
    var ground by remember { mutableStateOf(container) }
    var ink by remember { mutableStateOf(content) }
    val openSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val closeSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(open) {
        if (open) {
            from = origin
            ground = container
            ink = content
            shown = true
            progress.animateTo(1f, openSpec)
        } else if (shown) {
            progress.animateTo(0f, closeSpec)
            shown = false
            onClosed()
        }
    }
    if (!shown) return

    // The page's color is what sits behind the status bar while it is up,
    // so it decides whether the icons are dark or light: white icons on
    // the cream Library page would be unreadable.
    com.ivor.ivormusic.ui.theme.StatusBarAppearance(ground)
    BackHandler(enabled = open, onBack = onDismiss)
    var hostInRoot by remember { mutableStateOf(Offset.Zero) }
    val scheme = MaterialTheme.colorScheme
    val pageScheme = remember(scheme, ground, ink) { scheme.onBlock(ground, ink) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { hostInRoot = it.positionInRoot() }
            .graphicsLayer {
                // A spring may pass its target; the window may not.
                val p = progress.value.coerceIn(0f, 1f)
                val whole = Rect(0f, 0f, size.width, size.height)
                val start = from?.translate(-hostInRoot.x, -hostInRoot.y)
                    ?: Rect(center = whole.center, radius = TILE_CORNER.toPx())
                shape = RevealWindow(lerp(start, whole, p), TILE_CORNER.toPx() * (1f - p))
                clip = true
            }
            .background(ground)
            // The Home underneath is not pressed through the page.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = ((progress.value - CONTENT_FROM) / (CONTENT_BY - CONTENT_FROM)).coerceIn(0f, 1f)
            },
        ) {
            MaterialTheme(colorScheme = pageScheme) {
                // Text that names no color takes the ink too, not the app's own text color.
                CompositionLocalProvider(LocalContentColor provides ink, content = page)
            }
        }
    }
}

/** One cover in a page's hero: the picture, and a sharper copy to draw over it. */
internal data class HeroCover(val art: String?, val sharp: String? = null)

/**
 * The top of a block's page. The ground is the block's color, but not
 * flat: a large scalloped record sits behind everything, and the first
 * few covers from the page are set on it in four of Material's
 * expressive shapes, each a different size and turned a little. With
 * fewer covers than shapes the rest are drawn as plain tints, so the
 * page has the same composition before anything has loaded.
 *
 * Under them, the block's name at display size, and the two ways to
 * start it side by side: Play all and Shuffle. Either is left out when
 * the page has nothing to play.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BlockPageHero(
    title: String,
    covers: List<HeroCover>,
    topInset: Dp,
    onBack: () -> Unit,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        // The record: the app's own mark, large and half off the page.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 96.dp, y = (-120).dp)
                .size(340.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(scheme.surfaceContainerLow),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 16.dp, y = (-40).dp)
                .size(180.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainer),
        )
        Column(Modifier.fillMaxWidth().padding(top = topInset)) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = scheme.onBackground,
                )
            }
            Box(Modifier.fillMaxWidth().height(176.dp)) {
                HeroShape(
                    covers.getOrNull(2), MaterialShapes.Clover4Leaf.toShape(), scheme.surfaceContainerHighest,
                    Modifier.align(Alignment.TopEnd).offset(x = (-44).dp, y = 0.dp).size(124.dp),
                )
                HeroShape(
                    covers.getOrNull(0), MaterialShapes.Cookie9Sided.toShape(), scheme.surfaceContainerHighest,
                    Modifier.align(Alignment.TopStart).offset(x = 16.dp, y = 12.dp).size(140.dp).rotate(-8f),
                )
                HeroShape(
                    covers.getOrNull(1), MaterialShapes.Sunny.toShape(), scheme.surfaceContainerHigh,
                    Modifier.align(Alignment.TopCenter).offset(x = (-8).dp, y = 60.dp).size(108.dp).rotate(10f),
                )
                HeroShape(
                    covers.getOrNull(3), MaterialShapes.Pill.toShape(), scheme.surfaceContainerHigh,
                    Modifier.align(Alignment.BottomEnd).offset(x = (-14).dp, y = 0.dp).size(78.dp).rotate(18f),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.displayMedium,
                color = scheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
            )
            if (onPlay != null || onShuffle != null) {
                Row(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onPlay != null) {
                        Button(onClick = onPlay, modifier = Modifier.height(52.dp)) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_play_all))
                        }
                    }
                    if (onShuffle != null) {
                        FilledTonalButton(onClick = onShuffle, modifier = Modifier.height(52.dp)) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.cd_shuffle))
                        }
                    }
                }
            }
        }
    }
}

/** One shape of the hero: a cover clipped to it, or the tint alone when there is no cover for it. */
@Composable
private fun HeroShape(cover: HeroCover?, shape: Shape, tint: Color, modifier: Modifier) {
    Box(modifier.clip(shape).background(tint)) {
        if (cover?.art != null) {
            com.ivor.ivormusic.ui.components.VideoThumbnail(
                thumbnailUrl = cover.art,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                highResThumbnailUrl = cover.sharp ?: cover.art,
                contentScale = ContentScale.Crop,
                indicatorSize = 0.dp,
                emptyIcon = Icons.Rounded.PlayArrow,
                placeholderColor = tint,
            )
        }
    }
}
