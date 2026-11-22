package com.ivor.ivormusic.ui.home

import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Home on a phone moves in steps, like the blocks of a rolling desk clock:
 * the header and search stay put, and under them two blocks are always in
 * view, each half the room. A swipe moves exactly one step. As it does,
 * the top block falls back - it shrinks a little and fades, staying almost
 * where it was rather than sliding away - while the block under it rises
 * into its place, and the next one opens into the bottom slot out of a
 * pinched, faded state. Step after step it reads as a mechanism turning
 * over, not a list scrolling past.
 */

/** The gap between the two blocks in view. */
internal val STEP_GAP = 10.dp

/** How far a block shrinks as it falls back or before it opens: 12%. */
private const val STEP_SHRINK = 0.12f

/**
 * How much of a slot's travel a block gives up as it falls back (all of
 * it: it recedes where it stands while the next rises over it) and as it
 * opens (most of it: it opens in the bottom slot rather than sliding in).
 */
private const val STEP_HOLD_BACK = 1f
private const val STEP_HOLD_OPEN = 0.72f

/**
 * Where a block is relative to the step the pager is on: 0 is the top
 * slot, 1 the bottom, -1 just gone above, 2 still to come. Fractional
 * while a step is turning.
 */
internal fun stepPosition(page: Int, currentPage: Int, offsetFraction: Float): Float =
    (page - currentPage) - offsetFraction

/** How a block is drawn at a [position]: its scale, opacity, and the share of a slot it is held back by. */
internal data class StepLook(val scale: Float, val alpha: Float, val hold: Float)

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

internal fun stepLook(position: Float): StepLook = when {
    // Falling back out of the top slot.
    position < 0f -> {
        val t = (-position).coerceIn(0f, 1f)
        StepLook(scale = 1f - STEP_SHRINK * t, alpha = 1f - t, hold = STEP_HOLD_BACK * t)
    }
    // Opening into the bottom slot.
    position > 1f -> {
        val t = (position - 1f).coerceIn(0f, 1f)
        StepLook(scale = 1f - STEP_SHRINK * t, alpha = 1f - t, hold = -STEP_HOLD_OPEN * t)
    }
    else -> StepLook(1f, 1f, 0f)
}

/**
 * The blocks as steps: [blocks] in order, each drawn by [block] into a
 * slot half of [height] (less the gap). The pager's last step leaves the
 * final two in view, so nothing ever sits over empty space.
 */
@Composable
internal fun BlockSteps(
    state: PagerState,
    height: Dp,
    modifier: Modifier = Modifier,
    block: @Composable (index: Int, slot: Dp) -> Unit,
) {
    val slot = (height - STEP_GAP) / 2
    VerticalPager(
        state = state,
        modifier = modifier.fillMaxWidth(),
        pageSize = PageSize.Fixed(slot),
        pageSpacing = STEP_GAP,
        beyondViewportPageCount = 1,
        snapPosition = SnapPosition.Start,
        // One block per swipe however hard the fling: the steps are the point.
        flingBehavior = PagerDefaults.flingBehavior(
            state = state,
            pagerSnapDistance = PagerSnapDistance.atMost(1),
            snapAnimationSpec = spring(dampingRatio = 1f, stiffness = 520f),
        ),
    ) { index ->
        val position = stepPosition(index, state.currentPage, state.currentPageOffsetFraction)
        Box(
            Modifier
                .zIndex(stepLayer(position))
                .fillMaxSize()
                .graphicsLayer {
                    val look = stepLook(stepPosition(index, state.currentPage, state.currentPageOffsetFraction))
                    scaleX = look.scale
                    scaleY = look.scale
                    alpha = look.alpha
                    translationY = look.hold * (slot + STEP_GAP).toPx()
                    // A block falls back toward its top edge and opens from its top
                    // edge, so the slot it leaves or takes keeps its line.
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
        ) {
            block(index, slot)
        }
    }
}
