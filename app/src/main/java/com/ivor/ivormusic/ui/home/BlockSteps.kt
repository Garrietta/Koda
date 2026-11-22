package com.ivor.ivormusic.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * Home on a phone moves in steps, like the blocks of a rolling desk clock:
 * the header and search stay put, and under them two blocks are always in
 * view, each half the room above the now-playing bar. A swipe moves
 * exactly one step. As it does, the top block falls back where it stands -
 * it shrinks a little and fades - while the block under it rises into its
 * place, and the next one opens into the bottom slot out of a pinched,
 * faded state.
 *
 * The last step is different when something is playing: the bar itself
 * turns into the last block. The Now playing block grows up out of the
 * bar's own rectangle into the bottom slot and the bar's space below it,
 * so it is the largest block, while the bar fades away into it. The
 * stepper reports how far that has gone ([HomeSteps.morph]) so the bar
 * can follow.
 *
 * The steps take their own drags rather than scrolling, so a swipe here
 * neither hides the navigation nor closes the bar into its bubble: on
 * Home the bar stays a bar.
 */

/** The gap between blocks, and between the lower block and the bar. */
internal val STEP_GAP = 10.dp

/** The now-playing bar's height, which the last block grows out of and over. */
internal val STEP_BAR = 68.dp

/** How far a block shrinks as it falls back or before it opens: 12%. */
private const val STEP_SHRINK = 0.12f

/** How much of a slot's travel an opening block gives up, so it opens in the bottom slot. */
private const val STEP_HOLD_OPEN = 0.72f

/** A drag past this share of a step, or a flick, turns it. */
private const val STEP_TURN_AT = 0.2f
private const val STEP_FLICK = 900f

/**
 * Where Home's steps stand, shared with the screen that hosts the bar:
 * which step is up, whether the blocks are what is on screen, and how far
 * the bar has turned into the Now playing block.
 */
@Stable
class HomeSteps {
    /** The step the blocks stand at: 0 is the first two; fractional while one turns. */
    val position = Animatable(0f)

    /** How many blocks there are. */
    var count by mutableIntStateOf(0)

    /** Whether the last block is the Now playing one, grown out of the bar. */
    var endsWithBar by mutableStateOf(false)

    /** Home's blocks are on screen: Home's tab, the stepped layout, no search and no block's page open. */
    var landing by mutableStateOf(false)

    /** Something is open over Home (the DJ, a page grown from a tile); the bar keeps its usual ways there. */
    var overlayOpen by mutableStateOf(false)

    /** The last step: the final two blocks in view. */
    val lastStep: Int get() = (count - 2).coerceAtLeast(0)

    /** How far the bar has turned into the Now playing block, 0 to 1. */
    val morph: Float
        get() = if (!endsWithBar || count < 2) 0f else (position.value - (count - 3)).coerceIn(0f, 1f)

    /** The bar is a bar here, never a bubble, and follows [morph]. */
    val stepping: Boolean get() = landing && !overlayOpen
}

/**
 * Where a block is relative to the step: 0 is the top slot, 1 the bottom,
 * -1 just gone above, 2 still to come. Fractional while a step turns.
 */
internal fun stepPosition(page: Int, currentPage: Int, offsetFraction: Float): Float =
    (page - currentPage) - offsetFraction

/** How a block is drawn at a [position]: its scale, opacity, and the share of a slot it is held back by. */
internal data class StepLook(val scale: Float, val alpha: Float, val hold: Float)

internal fun stepLook(position: Float): StepLook = when {
    // Falling back out of the top slot, where it stands.
    position < 0f -> {
        val t = (-position).coerceIn(0f, 1f)
        StepLook(scale = 1f - STEP_SHRINK * t, alpha = 1f - t, hold = t)
    }
    // Opening into the bottom slot.
    position > 1f -> {
        val t = (position - 1f).coerceIn(0f, 1f)
        StepLook(scale = 1f - STEP_SHRINK * t, alpha = 1f - t, hold = -STEP_HOLD_OPEN * t)
    }
    else -> StepLook(1f, 1f, 0f)
}

/**
 * Which block is drawn over which: the one rising through the slots on
 * top, the one opening at the bottom under it (it is uncovered as the
 * other moves up), and the one falling back under both.
 */
internal fun stepLayer(position: Float): Float = when {
    position < 0f -> 0f
    position > 1f -> 0.5f
    else -> 1f
}

/** The step a drag that began at [from] settles on, after moving [moved] steps at [velocity] (steps per second, positive onward). */
internal fun stepTarget(from: Int, moved: Float, velocity: Float, last: Int): Int {
    val onward = moved > STEP_TURN_AT || velocity > STEP_FLICK
    val back = moved < -STEP_TURN_AT || velocity < -STEP_FLICK
    return when {
        onward && !back -> from + 1
        back && !onward -> from - 1
        else -> from
    }.coerceIn(0, last)
}

/**
 * The blocks as steps: [count] blocks, each drawn by [block] at the
 * height it is given. The box fills down to the bottom of the bar
 * ([barReserve] is the bar and the gap above it, zero with nothing
 * playing), so two blocks and the bar fill it exactly.
 */
@Composable
internal fun BlockSteps(
    steps: HomeSteps,
    count: Int,
    endsWithBar: Boolean,
    barReserve: Dp,
    modifier: Modifier = Modifier,
    block: @Composable (index: Int, morph: () -> Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(count, endsWithBar) {
        steps.count = count
        steps.endsWithBar = endsWithBar
        if (steps.position.value > steps.lastStep) steps.position.snapTo(steps.lastStep.toFloat())
    }
    var stepPx by androidx.compose.runtime.remember { mutableStateOf(1f) }
    var dragFrom by androidx.compose.runtime.remember { mutableIntStateOf(0) }
    val drag = rememberDraggableState { delta ->
        scope.launch {
            steps.position.snapTo((steps.position.value - delta / stepPx).coerceIn(0f, steps.lastStep.toFloat()))
        }
    }
    Layout(
        content = { for (i in 0 until count) block(i) { if (endsWithBar && i == count - 1) steps.morph else 0f } },
        modifier = modifier
            .fillMaxSize()
            .draggable(
                drag,
                Orientation.Vertical,
                onDragStarted = { dragFrom = steps.position.value.roundToInt() },
                onDragStopped = { velocity ->
                    val target = stepTarget(dragFrom, steps.position.value - dragFrom, -velocity / stepPx, steps.lastStep)
                    steps.position.animateTo(target.toFloat(), spring(dampingRatio = 1f, stiffness = 520f))
                },
            ),
    ) { measurables, constraints ->
        val gap = STEP_GAP.roundToPx()
        val bar = if (endsWithBar) barReserve.roundToPx() else 0
        val height = constraints.maxHeight
        val width = constraints.maxWidth
        val slot = ((height - bar - gap) / 2).coerceAtLeast(1)
        stepPx = (slot + gap).toFloat()
        val position = steps.position.value
        val barHeight = STEP_BAR.roundToPx()
        layout(width, height) {
            measurables.forEachIndexed { index, measurable ->
                val at = index - position
                if (at < -1f || at > 2f) return@forEachIndexed
                val isBarBlock = endsWithBar && index == count - 1
                if (isBarBlock) {
                    // Grown out of the bar: from its rectangle at the foot of
                    // the box to the bottom slot and the bar's room below it.
                    val m = (2f - at).coerceIn(0f, 1f)
                    val full = slot + bar
                    val h = (barHeight + (full - barHeight) * m).roundToInt().coerceAtLeast(1)
                    val top = ((height - barHeight) + ((slot + gap) - (height - barHeight)) * m).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(width, h))
                    placeable.place(0, top, zIndex = 0.5f)
                } else {
                    val look = stepLook(at)
                    val placeable = measurable.measure(Constraints.fixed(width, slot))
                    val y = (at.coerceIn(0f, 1f) * (slot + gap)).roundToInt() +
                        (if (at > 1f) ((at - 1f) * (slot + gap) * (1f - STEP_HOLD_OPEN)).roundToInt() else 0)
                    placeable.placeWithLayer(0, y, zIndex = stepLayer(at)) {
                        scaleX = look.scale
                        scaleY = look.scale
                        alpha = look.alpha
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                }
            }
        }
    }
}

/** Sends the steps back to the first, as a tap on Home's tab does. */
suspend fun HomeSteps.toFirst() {
    position.animateTo(0f, spring(dampingRatio = 1f, stiffness = 380f))
}
