package com.ivor.ivormusic.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
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
 * Home moves in steps, like the blocks of a rolling desk clock: the header
 * and search stay put, and under them two blocks are always in view, each
 * half the room. The motion follows three patterns that are known to work:
 *
 *  - Masking, as Material 3's carousel does at its edges. A block never
 *    shrinks or stretches; a rounded window over it does. As a step turns,
 *    the top block's window closes up into the top edge, its content kept
 *    centred in what is left, and it fades back; the lower block slides up
 *    whole into the top slot; and the next block's window opens up from the
 *    foot into the room the others leave. The three windows tile the space
 *    with the same gap between them at every moment, so nothing overlaps
 *    and nothing jumps (stepWindow).
 *
 *  - Paging. A swipe turns exactly one step, however long it is, and the
 *    step is finished by a stiff spring with no visible overshoot (the
 *    standard scheme's fast spatial spring), landing with a haptic tick. A
 *    finger still held down after a step lands keeps turning steps, one
 *    after another, until it lifts.
 *
 *  - Resistance. Before a swipe is long enough to turn a step, the blocks
 *    give a little under the finger, as a list does at its end, so the touch
 *    is felt; the step then carries on from there. Let go short and they
 *    spring back. They can give only a few percent, so they never rest part
 *    way between steps.
 *
 * The last step is different when something is playing on a phone: the
 * bar itself turns into the last block. The Now playing block grows up out
 * of the bar's own rectangle into the bottom slot and the bar's space
 * below it, while the bar fades away into it. The stepper reports how far
 * that has gone ([HomeSteps.morph]) so the bar can follow.
 *
 * One coroutine owns the position, so nothing can stop a step part way,
 * and the steps take their own drags rather than scrolling, so a swipe
 * here neither hides the navigation nor closes the bar into its bubble.
 */

/** The gap between blocks, and between the lower block and the bar. */
internal val STEP_GAP = 10.dp

/** The now-playing bar's height, which the last block grows out of and over. */
internal val STEP_BAR = 68.dp

/** The rounding of a window's moving edge, where it cuts across a block. */
private val STEP_MASK_RADIUS = 28.dp

/** How much a block closing into the top fades back by the time it is gone, and how faint one opening at the foot begins. */
private const val STEP_FADE_OUT = 0.6f
private const val STEP_FADE_IN = 0.45f

/** Before a step turns, the blocks follow the finger at this share of its travel, at most this share of a step. */
private const val STEP_NUDGE = 0.4f
private const val STEP_NUDGE_MOST = 0.06f

/** A swipe turns a step once it has moved this share of a step... */
private const val STEP_TURN_AT = 0.12f

/**
 * With the finger still down, the next step turns this long after the
 * swipe's step has landed, so a swipe lifted at its natural end turns one;
 * after that, held steps follow each other this much sooner.
 */
private const val STEP_HOLD_MILLIS = 280L
private const val STEP_HOLD_REPEAT_MILLIS = 120L

/** A swipe too short to turn a step turns one if it ends faster than this, in steps a second. */
private const val STEP_FLICK = 2f

/** The share of the last step over which the Now playing block fades in out of the bar. */
private const val BAR_BLOCK_FADE_IN = 0.15f

/**
 * A step's spring: Material's standard fast spatial spring, stiff and all
 * but critically damped, so a step lands firmly with no bounce to see.
 */
private const val STEP_STIFFNESS = 1400f
private const val STEP_DAMPING = 0.9f

/** Letting go short: the blocks spring back from their nudge. */
private const val STEP_RETURN_STIFFNESS = 700f

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

    /** How far a swipe not yet long enough to turn a step has nudged the blocks, in steps. */
    var nudge by mutableFloatStateOf(0f)

    /** Where the blocks are drawn: the position and any nudge. */
    val shown: Float get() = position.value + nudge

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

    /**
     * The unfolded Home is on screen with its Now playing panel, which takes
     * the bar's place: the bar stays hidden there until a page opens over it.
     */
    var wideLanding by mutableStateOf(false)

    /** The bar is hidden behind the unfolded Home's panel. */
    val panelShowing: Boolean get() = wideLanding && !overlayOpen

    /** The last step: the final two blocks in view. */
    val lastStep: Int get() = (count - 2).coerceAtLeast(0)

    /** How far the bar has turned into the Now playing block, 0 to 1. */
    val morph: Float
        get() = if (!endsWithBar || count < 2) 0f else (shown - (count - 3)).coerceIn(0f, 1f)

    /** The bar is a bar here, never a bubble, and follows [morph]. */
    val stepping: Boolean get() = landing && !overlayOpen
}

/** The part of the steps' box a block is seen through, top and bottom in pixels from the box's top. */
internal data class StepWindow(val top: Float, val bottom: Float) {
    val height: Float get() = bottom - top
}

/**
 * The window of a block at [at] steps from the top slot (0 the top slot,
 * 1 the bottom, -1 gone above, 2 still to come), for slots [slot] tall
 * with [gap] between them; null when none of it shows.
 *
 * Going up out of the top slot its window closes into the top edge; between
 * the slots it is whole and moves; coming into the bottom slot its window
 * opens up from the foot, its top edge following the block above at one
 * gap's distance. At every position the windows in view tile the box.
 */
internal fun stepWindow(at: Float, slot: Float, gap: Float): StepWindow? {
    val pitch = slot + gap
    val window = when {
        at <= -1f || at >= 2f -> return null
        // Its foot stays one gap above the block rising into the top slot.
        at < 0f -> StepWindow(0f, (1f + at) * pitch - gap)
        at <= 1f -> StepWindow(at * pitch, at * pitch + slot)
        else -> StepWindow((at - 1f) * pitch + pitch, slot + pitch)
    }
    return window.takeIf { it.height > 0.5f }
}

/** How opaque a block at [at] is: fading back as it closes into the top, faint as it opens at the foot. */
internal fun stepFade(at: Float): Float = when {
    at < 0f -> 1f - STEP_FADE_OUT * (-at).coerceIn(0f, 1f)
    at > 1f -> 1f - (1f - STEP_FADE_IN) * (at - 1f).coerceIn(0f, 1f)
    else -> 1f
}

/** How far a swipe that has moved [moved] steps nudges the blocks before it turns one. */
internal fun stepNudge(moved: Float): Float = (moved * STEP_NUDGE).coerceIn(-STEP_NUDGE_MOST, STEP_NUDGE_MOST)

/** A rounded window cut across a block, in the block's own coordinates, rounded only on the edges that cut it. */
private class StepWindowShape(
    private val top: Float,
    private val bottom: Float,
    private val radius: Float,
    private val roundTop: Boolean,
    private val roundBottom: Boolean,
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val t = if (roundTop) androidx.compose.ui.geometry.CornerRadius(radius) else androidx.compose.ui.geometry.CornerRadius.Zero
        val b = if (roundBottom) androidx.compose.ui.geometry.CornerRadius(radius) else androidx.compose.ui.geometry.CornerRadius.Zero
        return androidx.compose.ui.graphics.Outline.Rounded(
            androidx.compose.ui.geometry.RoundRect(0f, top, size.width, bottom, t, t, b, b),
        )
    }
}

/**
 * Which way a swipe that has moved [moved] steps from where the finger
 * went down (positive onward) turns: +1 or -1 once it has gone
 * [STEP_TURN_AT], 0 before. However far it goes, a swipe turns one step.
 */
internal fun swipeTurn(moved: Float): Int = when {
    moved >= STEP_TURN_AT -> 1
    moved <= -STEP_TURN_AT -> -1
    else -> 0
}

/**
 * Whether a finger still held down turns the next step: the swipe has
 * turned its step, the finger is still off to one side ([direction] not 0),
 * and the last step has been landed for [landedMillis], long enough after
 * [heldSteps] steps already turned by holding.
 */
internal fun holdTurns(turned: Boolean, direction: Int, landedMillis: Long, heldSteps: Int): Boolean =
    turned && direction != 0 &&
        landedMillis >= (if (heldSteps == 0) STEP_HOLD_MILLIS else STEP_HOLD_REPEAT_MILLIS)

/** Whether a swipe that ended without turning a step was a flick at [velocity] steps a second (positive onward). */
internal fun flickTurn(velocity: Float): Int = when {
    velocity >= STEP_FLICK -> 1
    velocity <= -STEP_FLICK -> -1
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
 * The swipe in progress: how far the finger has moved in steps, whether it
 * has turned its step, the steps turned since by holding, and when the
 * blocks last came to rest (0 while a step is playing).
 */
private class StepSwipe {
    var moved = 0f
    var turned = false
    var held = 0
    var landedAt = 0L
    var hold: Job? = null
    var settle: Job? = null
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
            // The step carries on from where the finger nudged the blocks.
            if (steps.nudge != 0f) {
                steps.position.snapTo(steps.position.value + steps.nudge)
                steps.nudge = 0f
            }
            while (true) {
                val target = steps.target
                val position = steps.position.value
                if (position == target.toFloat()) break
                val next = nextStepToward(position, target)
                steps.position.animateTo(next, spring(dampingRatio = STEP_DAMPING, stiffness = STEP_STIFFNESS))
                haptics.tick()
            }
        }
    }
    var stepPx by remember { mutableStateOf(1f) }
    val swipe = remember { StepSwipe() }
    // The finger never moves the blocks: a swipe turns one step, and a
    // finger held down after it has landed turns the next ones.
    fun turn(direction: Int): Boolean {
        val next = stepTarget(steps.target, direction, steps.lastStep)
        if (next == steps.target) return false
        steps.stepTo(next)
        return true
    }
    val drag = rememberDraggableState { delta ->
        swipe.moved -= delta / stepPx
        if (!swipe.turned) {
            val direction = swipeTurn(swipe.moved)
            // Long enough: the step turns. Not yet, or at either end: the
            // blocks give a little under the finger.
            if (direction != 0 && turn(direction)) swipe.turned = true else steps.nudge = stepNudge(swipe.moved)
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
                    swipe.settle?.cancel()
                    swipe.moved = 0f
                    swipe.turned = false
                    swipe.held = 0
                    swipe.landedAt = 0L
                    // While the finger stays down, each landing is followed
                    // by the next step after a moment.
                    swipe.hold?.cancel()
                    swipe.hold = scope.launch {
                        while (isActive) {
                            delay(30)
                            val now = System.currentTimeMillis()
                            val resting = !steps.position.isRunning && steps.position.value == steps.target.toFloat()
                            if (!resting) {
                                swipe.landedAt = 0L
                                continue
                            }
                            if (swipe.landedAt == 0L) swipe.landedAt = now
                            val direction = swipeTurn(swipe.moved)
                            val next = steps.target + direction
                            if (holdTurns(swipe.turned, direction, now - swipe.landedAt, swipe.held) && next in 0..steps.lastStep) {
                                swipe.held++
                                swipe.landedAt = 0L
                                turn(direction)
                            }
                        }
                    }
                },
                onDragStopped = { velocity ->
                    swipe.hold?.cancel()
                    if (!swipe.turned && turn(flickTurn(-velocity / stepPx))) swipe.turned = true
                    // Let go short: the blocks spring back from their nudge.
                    if (!swipe.turned && steps.nudge != 0f) {
                        swipe.settle = scope.launch {
                            animate(steps.nudge, 0f, animationSpec = spring(dampingRatio = 1f, stiffness = STEP_RETURN_STIFFNESS)) { value, _ ->
                                steps.nudge = value
                            }
                        }
                    }
                },
            ),
    ) { measurables, constraints ->
        val gap = STEP_GAP.roundToPx()
        val bar = if (endsWithBar) barReserve.roundToPx() else 0
        val height = constraints.maxHeight
        val width = constraints.maxWidth
        val slot = ((height - bar - gap) / 2).coerceAtLeast(1)
        stepPx = (slot + gap).toFloat()
        val position = steps.shown
        val barHeight = STEP_BAR.roundToPx()
        val maskRadius = STEP_MASK_RADIUS.toPx()
        layout(width, height) {
            measurables.forEachIndexed { index, measurable ->
                val at = index - position
                val isBarBlock = endsWithBar && index == count - 1
                if (isBarBlock) {
                    if (at < -1f || at > 2f) return@forEachIndexed
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
                    val window = stepWindow(at, slot.toFloat(), gap.toFloat()) ?: return@forEachIndexed
                    // Laid out whole, its content centred in its window, and
                    // cut to the window when the window is less than whole.
                    val placeable = measurable.measure(Constraints.fixed(width, slot))
                    val y = (window.top + window.bottom) / 2f - slot / 2f
                    val whole = window.height >= slot - 0.5f
                    val fade = stepFade(at)
                    placeable.placeWithLayer(0, y.roundToInt()) {
                        alpha = fade
                        if (!whole) {
                            val top = window.top - y.roundToInt()
                            shape = StepWindowShape(
                                top = top,
                                bottom = top + window.height,
                                radius = minOf(maskRadius, window.height / 2f),
                                roundTop = at > 1f,
                                roundBottom = at < 0f,
                            )
                            clip = true
                        }
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
