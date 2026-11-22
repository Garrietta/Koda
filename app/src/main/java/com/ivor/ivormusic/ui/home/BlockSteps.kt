package com.ivor.ivormusic.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt
import kotlin.math.sign

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
 * A step is rigid. The blocks never follow the finger and never rest
 * between steps: once a swipe has travelled a short way, or is a flick, one
 * step is turned at once and plays out as a short fixed snap with a haptic
 * tick, whatever the finger does next. One coroutine owns the animation,
 * so nothing can stop a step part way.
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

/** A swipe turns a step once it has moved this share of a step... */
private const val STEP_TURN_AT = 0.12f

/** ...or when it ends faster than this, in steps a second. */
private const val STEP_FLICK = 2f

/** A step: a short snap with no lingering tail. */
private const val STEP_MILLIS = 220

/**
 * Where Home's steps stand, shared with the screen that hosts the bar:
 * which step is up, whether the blocks are what is on screen, and how far
 * the bar has turned into the Now playing block.
 */
@Stable
class HomeSteps {
    /** The step the blocks stand at: 0 is the first two; fractional only while one turns. */
    val position = Animatable(0f)

    /** The step the blocks are at or snapping to. */
    var target by mutableIntStateOf(0)
        private set

    /** Snaps the blocks to [step]; the stepper plays the turn. */
    fun stepTo(step: Int) {
        target = step.coerceIn(0, lastStep)
    }

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

/**
 * Whether a swipe that has moved [moved] steps (positive onward) turns a
 * step yet: +1 onward, -1 back, 0 not yet. Checked as the finger moves, so
 * the step turns the moment the swipe is long enough.
 */
internal fun swipeTurn(moved: Float): Int = when {
    moved >= STEP_TURN_AT -> 1
    moved <= -STEP_TURN_AT -> -1
    else -> 0
}

/** Whether a swipe that ended without turning a step was a flick at [velocity] steps a second (positive onward). */
internal fun flickTurn(velocity: Float): Int = when {
    velocity >= STEP_FLICK -> 1
    velocity <= -STEP_FLICK -> -1
    else -> 0
}

/** The step a turn of [direction] from [from] lands on, one step at most and within the steps. */
internal fun stepTarget(from: Int, direction: Int, last: Int): Int =
    (from + direction.sign).coerceIn(0, last)

/** The swipe in progress: how far it has moved, in steps, and whether it has turned its step. */
private class StepSwipe {
    var moved = 0f
    var turned = false
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
    val haptics = rememberKodaHaptics()
    LaunchedEffect(count, endsWithBar) {
        steps.count = count
        steps.endsWithBar = endsWithBar
        if (steps.target > steps.lastStep) steps.stepTo(steps.lastStep)
    }
    // The one owner of the position. A step cut short when Home last left
    // the screen lands first; after that each new target plays as one
    // snap, and a target that changes mid-snap takes over from where the
    // blocks are.
    LaunchedEffect(steps) {
        if (steps.position.value != steps.target.toFloat()) steps.position.snapTo(steps.target.toFloat())
        snapshotFlow { steps.target }.drop(1).collectLatest { target ->
            steps.position.animateTo(target.toFloat(), tween(STEP_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    var stepPx by remember { mutableStateOf(1f) }
    val swipe = remember { StepSwipe() }
    fun turn(direction: Int) {
        val next = stepTarget(steps.target, direction, steps.lastStep)
        if (next != steps.target) {
            steps.stepTo(next)
            haptics.tick()
        }
    }
    // The finger never moves the blocks: it only decides when a step turns.
    val drag = rememberDraggableState { delta ->
        if (!swipe.turned) {
            swipe.moved -= delta / stepPx
            val direction = swipeTurn(swipe.moved)
            if (direction != 0) {
                swipe.turned = true
                turn(direction)
            }
        }
    }
    Layout(
        content = { for (i in 0 until count) block(i) { if (endsWithBar && i == count - 1) steps.morph else 0f } },
        modifier = modifier
            .fillMaxSize()
            .draggable(
                drag,
                Orientation.Vertical,
                onDragStarted = {
                    swipe.moved = 0f
                    swipe.turned = false
                },
                onDragStopped = { velocity ->
                    if (!swipe.turned) turn(flickTurn(-velocity / stepPx))
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
fun HomeSteps.toFirst() {
    stepTo(0)
}
