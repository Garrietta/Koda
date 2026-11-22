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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
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
 * between steps: once a swipe has travelled a short way, or is a flick, a
 * step turns at once and plays out as a short fixed snap that lands with a
 * haptic tick. A longer swipe turns more steps, one for each further stretch
 * of travel from where the finger went down, and a finger held down past
 * the first step keeps turning them at a steady pace, so the bottom is a
 * swipe away and the steps never race past the one wanted. When several
 * are due they play one after another, each landing before the next, a
 * little quicker than a single one. One coroutine owns the animation, so
 * nothing can stop a step part way.
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

/** ...and one more for each further stretch this long... */
private const val STEP_MORE_AT = 0.6f

/** ...or, with the finger held still past the first step, one more after this wait, then at this pace. */
private const val STEP_HOLD_MILLIS = 550L
private const val STEP_HOLD_REPEAT_MILLIS = 300L

/** A swipe that turned nothing turns one if it ends faster than this, in steps a second... */
private const val STEP_FLICK = 2f

/** ...and a swipe that turned some turns one more if it ends faster than this. */
private const val STEP_FLING_MORE = 8f

/** The share of the last step over which the Now playing block fades in out of the bar. */
private const val BAR_BLOCK_FADE_IN = 0.15f

/** A step: a short snap with no lingering tail. Steps on the way to a later one are a little quicker. */
private const val STEP_MILLIS = 220
private const val STEP_RUN_MILLIS = 150

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
 * How many steps a swipe that has moved [moved] steps from where the
 * finger went down (positive onward) has turned: none until
 * [STEP_TURN_AT], then one, and one more for each further [STEP_MORE_AT].
 * Checked as the finger moves, so each step turns the moment it is due.
 */
internal fun swipeSteps(moved: Float): Int {
    val distance = abs(moved)
    if (distance < STEP_TURN_AT) return 0
    val steps = 1 + floor((distance - STEP_TURN_AT) / STEP_MORE_AT).toInt()
    return if (moved > 0f) steps else -steps
}

/** Whether a swipe that ended without turning a step was a flick at [velocity] steps a second (positive onward). */
internal fun flickTurn(velocity: Float): Int = when {
    velocity >= STEP_FLICK -> 1
    velocity <= -STEP_FLICK -> -1
    else -> 0
}

/**
 * The steps a swipe's release adds after it turned [turned]: a flick turns
 * one when the swipe turned none, and a fast release in the swipe's own
 * direction one more. Never more than one, so a hard swipe does not race
 * past the step wanted.
 */
internal fun releaseSteps(turned: Int, velocity: Float): Int = when {
    turned == 0 -> flickTurn(velocity)
    turned > 0 && velocity >= STEP_FLING_MORE -> 1
    turned < 0 && velocity <= -STEP_FLING_MORE -> -1
    else -> 0
}

/** The step a turn of [direction] from [from] lands on, one step at most and within the steps. */
internal fun stepTarget(from: Int, direction: Int, last: Int): Int =
    (from + direction.sign).coerceIn(0, last)

/** The next whole step from [position] on the way to [target]. */
internal fun nextStepToward(position: Float, target: Int): Float = when {
    target > position -> minOf(floor(position) + 1f, target.toFloat())
    target < position -> maxOf(ceil(position) - 1f, target.toFloat())
    else -> position
}

/**
 * The swipe in progress: the step it began on, how far the finger has
 * moved in steps, the steps added by holding, and when a step last turned.
 */
private class StepSwipe {
    var from = 0
    var moved = 0f
    var held = 0
    var lastTurn = 0L
    var hold: Job? = null
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
    val scope = rememberCoroutineScope()
    LaunchedEffect(count, endsWithBar) {
        steps.count = count
        steps.endsWithBar = endsWithBar
        if (steps.target > steps.lastStep) steps.stepTo(steps.lastStep)
    }
    // The one owner of the position. A step cut short when Home last left
    // the screen lands first. After that the blocks go to each new target
    // one whole step at a time, every step a snap that lands with a tick; a
    // target that changes on the way takes over from where the blocks are.
    LaunchedEffect(steps) {
        if (steps.position.value != steps.target.toFloat()) steps.position.snapTo(steps.target.toFloat())
        snapshotFlow { steps.target }.drop(1).collectLatest {
            while (true) {
                val target = steps.target
                val position = steps.position.value
                if (position == target.toFloat()) break
                val next = nextStepToward(position, target)
                val millis = if (abs(target - position) > 1.01f) STEP_RUN_MILLIS else STEP_MILLIS
                steps.position.animateTo(next, tween(millis, easing = FastOutSlowInEasing))
                haptics.tick()
            }
        }
    }
    var stepPx by remember { mutableStateOf(1f) }
    val swipe = remember { StepSwipe() }
    // The finger never moves the blocks: it only decides which step they
    // go to, from how far it has moved and how long it has been held.
    fun follow() {
        val last = steps.lastStep
        val wanted = swipeSteps(swipe.moved) + swipe.held
        // Past either end, the start moves with the finger, so turning back
        // turns a step back at once.
        if (swipe.from + wanted > last) swipe.from = last - wanted
        if (swipe.from + wanted < 0) swipe.from = -wanted
        val target = (swipe.from + wanted).coerceIn(0, last)
        if (target != steps.target) {
            steps.stepTo(target)
            swipe.lastTurn = System.currentTimeMillis()
        }
    }
    val drag = rememberDraggableState { delta ->
        swipe.moved -= delta / stepPx
        val direction = swipeSteps(swipe.moved).sign
        if (swipe.held != 0 && swipe.held.sign != direction) swipe.held = 0
        follow()
    }
    Layout(
        content = { for (i in 0 until count) block(i) { if (endsWithBar && i == count - 1) steps.morph else 0f } },
        modifier = modifier
            .fillMaxSize()
            .draggable(
                drag,
                Orientation.Vertical,
                onDragStarted = {
                    swipe.from = steps.target
                    swipe.moved = 0f
                    swipe.held = 0
                    swipe.lastTurn = System.currentTimeMillis()
                    // A finger held still past the first step keeps turning them.
                    swipe.hold?.cancel()
                    swipe.hold = scope.launch {
                        while (isActive) {
                            delay(40)
                            val direction = swipeSteps(swipe.moved).sign
                            if (direction == 0) continue
                            val wait = if (swipe.held == 0) STEP_HOLD_MILLIS else STEP_HOLD_REPEAT_MILLIS
                            val next = steps.target + direction
                            if (System.currentTimeMillis() - swipe.lastTurn >= wait && next in 0..steps.lastStep) {
                                swipe.held += direction
                                follow()
                            }
                        }
                    }
                },
                onDragStopped = { velocity ->
                    swipe.hold?.cancel()
                    val extra = releaseSteps(swipeSteps(swipe.moved) + swipe.held, -velocity / stepPx)
                    if (extra != 0) steps.stepTo(steps.target + extra)
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
                    // Until the last step begins it is not drawn at all, and
                    // it fades in over the first moments of the turn, so its
                    // corners never show around the bar's round ends.
                    val m = (2f - at).coerceIn(0f, 1f)
                    if (m <= 0f) return@forEachIndexed
                    val full = slot + bar
                    val h = (barHeight + (full - barHeight) * m).roundToInt().coerceAtLeast(1)
                    val top = ((height - barHeight) + ((slot + gap) - (height - barHeight)) * m).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(width, h))
                    placeable.placeWithLayer(0, top, zIndex = 0.5f) {
                        alpha = (m / BAR_BLOCK_FADE_IN).coerceIn(0f, 1f)
                    }
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
