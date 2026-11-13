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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import com.ivor.ivormusic.ui.components.PLAYER_CONTAINER_SPRING
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.layer.drawLayer
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
import androidx.compose.ui.unit.sp
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
private const val CONTENT_FROM = 0.35f
private const val CONTENT_BY = 0.8f

/**
 * Over the last stretch before it lands on the tile, the window fades,
 * so the tile's own name and icon show through as it arrives rather than
 * a flat patch of its color sitting over them for a frame.
 */
private const val WINDOW_FADE_UNTIL = 0.18f

/**
 * The tile's own face (its name, icon and color, as last drawn) sits over
 * the page as it starts to grow and fades by this far open, so the page
 * seems to come out from under the tile rather than replace it.
 */
private const val FACE_GONE_BY = 0.55f

/**
 * The page's growth on opening, from every tile and every tap. Damped
 * enough not to bounce against the screen's edges. A spring of the same
 * damping settles in a time that goes as one over the square root of its
 * stiffness, so this is four times the 200 it was tried at on the phone,
 * and opens in half the time: that one read as slow. Closing is the
 * player's own collapse ([PLAYER_CONTAINER_SPRING]), so a page shrinks
 * into its tile as quickly as the player shrinks into the mini player.
 */
private val BLOCK_PAGE_SPRING = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.92f, stiffness = 800f)

/**
 * The tile a page grew out of: its color and the ink on it. A page that
 * paints its own ground (the DJ, a playlist with no cover) takes this
 * one, so it opens in exactly the tile's color; a page with no tile
 * behind it reads null and keeps its usual ground. Being inside a tile's
 * page is also why such a page has no back arrow: a pull down from the
 * top closes it, and so does Back.
 */
internal class BlockTile(val ground: Color, val ink: Color)

internal val LocalBlockTile = androidx.compose.runtime.compositionLocalOf<BlockTile?> { null }

/**
 * Where a page tells the window it grows in what its own ground is, so
 * the window grows and shrinks in the page's color: an artist page opened
 * from a fingertip closes as its own color shrinking away, not as a plain
 * box left behind once the page has faded.
 */
internal val LocalRevealGround = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.MutableState<Color?>?> { null }

/** Tells the window this page grows in that [color] is the page's ground. */
@Composable
internal fun ReportRevealGround(color: Color) {
    val sink = LocalRevealGround.current
    androidx.compose.runtime.SideEffect { sink?.value = color }
}

/** How much of the tile's face shows at window progress [p]. */
internal fun faceAlpha(p: Float): Float = (1f - p / FACE_GONE_BY).coerceIn(0f, 1f)

/** A pull down this far, as a share of the page's height, closes the page on release. */
private const val PULL_TO_CLOSE = 0.22f

/** How much of the page's height a pull covers to bring the window all the way back. */
private const val PULL_RANGE = 1.1f

/** The window as a page is pulled down by [pulledPx] of [heightPx]: 1 is fully open, 0 the tile. */
internal fun pulledProgress(pulledPx: Float, heightPx: Float): Float =
    if (heightPx <= 0f) 1f else (1f - pulledPx / (heightPx * PULL_RANGE)).coerceIn(0f, 1f)

/** Whether a pull of [pulledPx] released at [velocityY] closes the page. */
internal fun pullCloses(pulledPx: Float, heightPx: Float, velocityY: Float): Boolean =
    pulledPx > heightPx * PULL_TO_CLOSE || (pulledPx > 0f && velocityY > 1800f)

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
    /**
     * Draw the page in the app's own colors rather than a scheme made from
     * the tile's: for a page that is a whole screen of the app (the
     * Library, a playlist) and was designed for the app's roles. It still
     * grows out of the tile in the tile's color, which gives way to the
     * app's background as the page fades in.
     */
    appColors: Boolean = false,
    /** The tile's face, recorded by the tile itself (see [BlockFaces]); null draws none. */
    face: androidx.compose.ui.graphics.layer.GraphicsLayer? = null,
    page: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(false) }
    // The tile and its colors are held from the moment it opened, so the
    // page shrinks back to where it came from in the color it had.
    var from by remember { mutableStateOf<Rect?>(null) }
    var ground by remember { mutableStateOf(container) }
    var ink by remember { mutableStateOf(content) }
    var heldFace by remember { mutableStateOf(face) }
    val reportedGround = remember { mutableStateOf<Color?>(null) }
    val openSpec = BLOCK_PAGE_SPRING
    val closeSpec = PLAYER_CONTAINER_SPRING
    LaunchedEffect(open) {
        if (open) {
            from = origin
            ground = container
            ink = content
            heldFace = face
            shown = true
            progress.animateTo(1f, openSpec)
        } else if (shown) {
            progress.animateTo(0f, closeSpec)
            shown = false
            onClosed()
        }
    }
    if (!shown) return

    // Pulling down from the top of the page closes it, the way the player
    // collapses: once the page's list is at its top, a further pull is the
    // window shrinking back towards the tile under the finger. Let go past
    // a fifth of the screen (or with a flick) and it closes; short of that
    // it springs open again.
    val scope = rememberCoroutineScope()
    var pagePx by remember { mutableFloatStateOf(0f) }
    var pulled by remember { mutableFloatStateOf(0f) }
    val latestDismiss by rememberUpdatedState(onDismiss)
    val pullToClose = remember(progress) {
        object : NestedScrollConnection {
            private fun follow() {
                scope.launch { progress.snapTo(pulledProgress(pulled, pagePx)) }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Pushing back up while pulled undoes the pull before the list scrolls.
                if (available.y < 0f && pulled > 0f && source == NestedScrollSource.UserInput) {
                    val used = maxOf(available.y, -pulled)
                    pulled += used
                    follow()
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    pulled += available.y
                    follow()
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled <= 0f) return Velocity.Zero
                val close = pullCloses(pulled, pagePx, available.y)
                pulled = 0f
                if (close) latestDismiss() else progress.animateTo(1f, openSpec)
                return available
            }
        }
    }

    // The page's color is what sits behind the status bar while it is up,
    // so it decides whether the icons are dark or light: white icons on
    // the cream Library page would be unreadable.
    val scheme = MaterialTheme.colorScheme
    val pageGround = if (appColors) scheme.background else ground
    val pageInk = if (appColors) scheme.onBackground else ink
    com.ivor.ivormusic.ui.theme.StatusBarAppearance(pageGround)
    BackHandler(enabled = open, onBack = onDismiss)
    var hostInRoot by remember { mutableStateOf(Offset.Zero) }
    val pageScheme = remember(scheme, ground, ink, appColors) { if (appColors) scheme else scheme.onBlock(ground, ink) }
    val contentShown = { ((progress.value - CONTENT_FROM) / (CONTENT_BY - CONTENT_FROM)).coerceIn(0f, 1f) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                hostInRoot = it.positionInRoot()
                pagePx = it.size.height.toFloat()
            }
            .nestedScroll(pullToClose)
            .graphicsLayer {
                // A spring may pass its target; the window may not.
                val p = progress.value.coerceIn(0f, 1f)
                alpha = (p / WINDOW_FADE_UNTIL).coerceIn(0f, 1f)
                val whole = Rect(0f, 0f, size.width, size.height)
                val start = from?.translate(-hostInRoot.x, -hostInRoot.y)
                    ?: Rect(center = whole.center, radius = TILE_CORNER.toPx())
                shape = RevealWindow(lerp(start, whole, p), TILE_CORNER.toPx() * (1f - p))
                clip = true
            }
            .drawBehind {
                // The tile's color, giving way to the page's own ground as
                // the page appears (the same color when the page is in the tile's).
                drawRect(reportedGround.value ?: if (appColors) lerp(ground, pageGround, contentShown()) else ground)
            }
            // The Home underneath is not pressed through the page.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = contentShown() },
        ) {
            MaterialTheme(colorScheme = pageScheme) {
                // Text that names no color takes the ink too, not the app's own text color.
                CompositionLocalProvider(
                    LocalContentColor provides pageInk,
                    LocalBlockTile provides BlockTile(ground, ink),
                    LocalPopupPage provides true,
                    LocalRevealGround provides reportedGround,
                    content = page,
                )
            }
        }
        // The tile's face, where the tile is, over the page as it grows out from under it.
        val tileFace = heldFace
        val tileAt = from
        if (tileFace != null && tileAt != null) {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val a = faceAlpha(progress.value.coerceIn(0f, 1f))
                    if (a <= 0f) return@drawBehind
                    val at = tileAt.translate(-hostInRoot.x, -hostInRoot.y)
                    drawContext.canvas.saveLayer(
                        androidx.compose.ui.geometry.Rect(at.topLeft, at.size),
                        androidx.compose.ui.graphics.Paint().apply { alpha = a },
                    )
                    drawContext.canvas.translate(at.left, at.top)
                    drawLayer(tileFace)
                    drawContext.canvas.translate(-at.left, -at.top)
                    drawContext.canvas.restore()
                },
            )
        }
    }
}

/**
 * A page's row of filled pills: Play all, Shuffle in the tonal color,
 * then [extras] in the same tonal pills, wrapping when they do not fit.
 * Every page that starts music has this one row under its name, whether
 * its top is a hero of shapes or its own cover.
 */
@Composable
internal fun PagePills(
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    modifier: Modifier = Modifier,
    pillsPerRow: Int = Int.MAX_VALUE,
    extras: @Composable () -> Unit = {},
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = pillsPerRow,
    ) {
        if (onPlay != null) {
            Button(onClick = onPlay, modifier = Modifier.height(HERO_BUTTON_HEIGHT), contentPadding = HERO_BUTTON_PADDING) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(HERO_ICON_SIZE))
                Spacer(Modifier.width(HERO_ICON_GAP))
                Text(stringResource(R.string.action_play_all))
            }
        }
        if (onShuffle != null) {
            FilledTonalButton(onClick = onShuffle, modifier = Modifier.height(HERO_BUTTON_HEIGHT), contentPadding = HERO_BUTTON_PADDING) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(HERO_ICON_SIZE))
                Spacer(Modifier.width(HERO_ICON_GAP))
                Text(stringResource(R.string.cd_shuffle))
            }
        }
        extras()
    }
}

/** How tall a single record's cover stands in its page's header. */
private val SINGLE_COVER_HEIGHT = 200.dp

/** The height of every pill in a page's header row. */
internal val HERO_BUTTON_HEIGHT = 52.dp

/**
 * The pills' side padding: narrower than a 52dp button's own, so Play
 * all, Shuffle and a third pill (Download) fit one row on a phone.
 */
internal val HERO_BUTTON_PADDING = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)

/** A pill's icon, and the space between it and the label. */
internal val HERO_ICON_SIZE = 20.dp
internal val HERO_ICON_GAP = 6.dp

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
 * Under them, the block's name at display size, and one row of filled
 * pills: Play all, Shuffle beside it in the tonal color, then the page's
 * own [extras] (New mix, Download, Save) in the same tonal pills, wrapping
 * to a second line when they do not fit. Every page's header is this
 * row and nothing more; what a page has to say about itself goes in its
 * list. Play all and Shuffle are left out when there is nothing to play.
 *
 * There is no back arrow: a pull down from the top closes the page, and
 * so does Back. The row the arrow sat in is kept, empty, so the covers
 * sit at the same height on every page, under a top bar's actions where
 * a page has them.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BlockPageHero(
    title: String,
    covers: List<HeroCover>,
    topInset: Dp,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    /** A line under the name: an album's artist, year and length. */
    subtitle: String? = null,
    /**
     * One record's own page (an album, a playlist with a cover): its cover
     * large in an expressive shape, with a few plain shapes beside it,
     * rather than a collage of many covers.
     */
    singleCover: Boolean = false,
    /** More pills after Shuffle, each a [FilledTonalButton] [HERO_BUTTON_HEIGHT] high with [HERO_BUTTON_PADDING]. */
    extras: @Composable () -> Unit = {},
    /** At most this many pills to a line; the DJ's four sit two and two. */
    pillsPerRow: Int = Int.MAX_VALUE,
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
            Spacer(Modifier.height(48.dp))
            if (singleCover) Box(Modifier.fillMaxWidth().height(SINGLE_COVER_HEIGHT)) {
                HeroShape(
                    covers.firstOrNull(),
                    MaterialShapes.Cookie9Sided.toShape(),
                    scheme.surfaceContainerHighest,
                    Modifier.align(Alignment.TopStart).offset(x = 16.dp).size(SINGLE_COVER_HEIGHT),
                )
                HeroShape(null, MaterialShapes.Sunny.toShape(), scheme.surfaceContainerHigh,
                    Modifier.align(Alignment.TopEnd).offset(x = (-36).dp, y = 20.dp).size(84.dp).rotate(12f))
                HeroShape(null, MaterialShapes.Pill.toShape(), scheme.surfaceContainerHighest,
                    Modifier.align(Alignment.BottomEnd).offset(x = (-104).dp, y = (-8).dp).size(56.dp).rotate(-18f))
                HeroShape(null, MaterialShapes.Flower.toShape(), scheme.surfaceContainerHigh,
                    Modifier.align(Alignment.BottomEnd).offset(x = (-20).dp, y = (-4).dp).size(44.dp))
            } else Box(Modifier.fillMaxWidth().height(176.dp)) {
                // The first four shapes are always there, as tints until
                // their covers arrive; the smaller ones in the gaps join only
                // when there are covers for them, so a page with many new
                // records is crowded with them and a quiet one is not.
                HERO_SLOTS.forEachIndexed { index, slot ->
                    val cover = covers.getOrNull(index)
                    if (index >= ALWAYS_SHOWN_SLOTS && cover == null) return@forEachIndexed
                    HeroShape(
                        cover,
                        slot.shape.toShape(),
                        if (slot.deep) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
                        Modifier.align(slot.align).offset(x = slot.x, y = slot.y).size(slot.size).rotate(slot.turn),
                    )
                }
            }
            // Large for "For you", smaller rather than cut for a DJ mix's long name.
            Text(
                title,
                style = MaterialTheme.typography.displayMedium,
                color = scheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                    minFontSize = 26.sp,
                    maxFontSize = MaterialTheme.typography.displayMedium.fontSize,
                    stepSize = 2.sp,
                ),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onBackground.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
                )
            }
            PagePills(
                onPlay = onPlay,
                onShuffle = onShuffle,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                pillsPerRow = pillsPerRow,
                extras = extras,
            )
        }
    }
}

/** Where one cover sits in the hero: its shape, size, place and turn. */
private class HeroSlot(
    val shape: androidx.graphics.shapes.RoundedPolygon,
    val size: Dp,
    val align: Alignment,
    val x: Dp,
    val y: Dp,
    val turn: Float,
    val deep: Boolean,
)

/** The four shapes every hero has; the rest appear only with a cover. */
private const val ALWAYS_SHOWN_SLOTS = 4

/** The most covers a hero shows. */
internal const val HERO_COVER_COUNT = 10

/** The hero's shapes, largest first, set so the small ones fill the gaps the large ones leave. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val HERO_SLOTS: List<HeroSlot> = listOf(
    HeroSlot(MaterialShapes.Cookie9Sided, 140.dp, Alignment.TopStart, 16.dp, 12.dp, -8f, deep = true),
    HeroSlot(MaterialShapes.Sunny, 108.dp, Alignment.TopCenter, (-8).dp, 60.dp, 10f, deep = false),
    HeroSlot(MaterialShapes.Clover4Leaf, 124.dp, Alignment.TopEnd, (-44).dp, 0.dp, 0f, deep = true),
    HeroSlot(MaterialShapes.Pill, 78.dp, Alignment.BottomEnd, (-14).dp, 0.dp, 18f, deep = false),
    HeroSlot(MaterialShapes.Flower, 64.dp, Alignment.TopCenter, 44.dp, 0.dp, -12f, deep = false),
    HeroSlot(MaterialShapes.Gem, 56.dp, Alignment.BottomStart, 132.dp, 0.dp, 8f, deep = true),
    HeroSlot(MaterialShapes.Cookie6Sided, 52.dp, Alignment.TopEnd, (-4).dp, 108.dp, 20f, deep = true),
    HeroSlot(MaterialShapes.Puffy, 48.dp, Alignment.BottomStart, 8.dp, 4.dp, -6f, deep = false),
    HeroSlot(MaterialShapes.Arch, 50.dp, Alignment.BottomEnd, (-104).dp, 2.dp, 0f, deep = false),
    HeroSlot(MaterialShapes.SoftBurst, 44.dp, Alignment.TopEnd, (-2).dp, 4.dp, 14f, deep = false),
)

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
