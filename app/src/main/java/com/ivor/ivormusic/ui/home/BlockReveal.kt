package com.ivor.ivormusic.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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

/** The top of a block's page: the way back, and the block's name at the size it had on the tile. */
@Composable
internal fun BlockPageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
