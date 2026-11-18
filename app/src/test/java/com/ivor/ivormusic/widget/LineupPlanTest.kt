package com.ivor.ivormusic.widget

import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.ui.home.BlockKind
import com.ivor.ivormusic.ui.home.blockKindForWidgetKey
import com.ivor.ivormusic.ui.home.widgetKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineupPlanTest {

    @Test
    fun aShortQueueInATallCellIsABigCover() {
        // The case on the phone: two songs queued in a 4x3 cell left half the card empty.
        val plan = lineupPlan(300.dp, queued = 2)
        assertEquals(2, plan.rows)
        assertEquals(140.dp, plan.header)
    }

    @Test
    fun aLongQueueGetsTheRowsThatFitAndNoMoreThanFour() {
        assertEquals(2, lineupPlan(200.dp, queued = 5).rows)
        assertEquals(4, lineupPlan(400.dp, queued = 5).rows)
    }

    @Test
    fun theHeaderNeverShrinksBelowItsMinimumToMakeRoomForARow() {
        for (height in listOf(110, 150, 200, 260, 300, 400)) {
            val plan = lineupPlan(height.dp, queued = 5)
            assertTrue("$height: ${plan.header}", plan.header >= 52.dp || plan.rows == 0)
        }
    }

    @Test
    fun aRealFourByTwoShowsTwoRowsWithoutTheLabelWhenThatIsWhatFits() {
        // About the height a 4x2 gets on a phone launcher.
        val plan = lineupPlan(176.dp, queued = 5)
        assertEquals(2, plan.rows)
        assertEquals(false, plan.label)
    }

    @Test
    fun anEmptyQueueStillSaysSoWhereThereIsRoom() {
        assertEquals(1, lineupPlan(200.dp, queued = 0).rows)
    }

    @Test
    fun aCellTooShortForARowIsTheHeaderAlone() {
        val plan = lineupPlan(110.dp, queued = 3)
        assertEquals(0, plan.rows)
        assertEquals(86.dp, plan.header)
    }

    @Test
    fun everyBlockHasAKeyThatFindsItAgain() {
        val keys = BlockKind.entries.map { it.widgetKey }
        assertEquals(keys.size, keys.toSet().size)
        for (kind in BlockKind.entries) assertEquals(kind, blockKindForWidgetKey(kind.widgetKey))
    }

    @Test
    fun theKeysPlacedWidgetsCarryAreFixed() {
        assertEquals("dj", BlockKind.Dj.widgetKey)
        assertEquals("for_you", BlockKind.ForYou.widgetKey)
        assertEquals("liked", BlockKind.Liked.widgetKey)
        assertEquals("episodes", BlockKind.Episodes.widgetKey)
    }
}
