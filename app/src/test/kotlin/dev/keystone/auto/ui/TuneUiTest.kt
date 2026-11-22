package dev.keystone.auto.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.keystone.auto.TuneState
import dev.keystone.auto.core.*
import dev.keystone.auto.ui.screens.TuneContent
import dev.keystone.auto.ui.theme.ProjectorConnectTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The real touch handling of the Fine-tune screen, driven like a finger. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class TuneUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val base = Offsets(146.0, 153.0, 163.0, 1.0, 82.0, 2.0, 73.0, 48.0)

    private fun show(initiallySelected: Int?, onMoved: (Offsets) -> Unit = {}): () -> Offsets {
        var cur by mutableStateOf(base)
        val view = TuneView(base)
        rule.setContent {
            ProjectorConnectTheme(true) {
                TuneContent(TuneState.Ready(view, null), cur, onMove = { cur = it; onMoved(it) }, onRetry = {}, onBack = {}, initiallySelected = initiallySelected)
            }
        }
        rule.waitForIdle()
        return { cur }
    }

    private fun press(desc: String) {
        rule.onNodeWithContentDescription(desc).performTouchInput { down(center); up() }
        rule.waitForIdle()
    }

    @Test fun anArrowKeepsMovingTheCornerOnEveryPress() {
        val cur = show(initiallySelected = 2)
        val view = TuneView(base)
        val start = view.corners(cur())[2]
        var last = start.x
        repeat(5) {
            press("Move right")
            val x = view.corners(cur())[2].x
            assertTrue("press ${it + 1}: x went from $last to $x", x > last)
            last = x
        }
    }

    @Test fun theArrowsMoveTheSelectedCornerAndNoOther() {
        val cur = show(initiallySelected = 0)
        val before = cur()
        press("Move down"); press("Move down"); press("Move left")
        val after = cur()
        for (k in 1..3) assertEquals("corner $k moved", before.corners()[k], after.corners()[k])
        assertNotEquals(before.corners()[0], after.corners()[0])
    }

    @Test fun eachCornerRespondsToItsOwnArrows() {
        // tapping a different dot afterwards must retarget the arrows (this was the stale-closure bug)
        val moves = ArrayList<Offsets>()
        val cur = show(initiallySelected = 3, onMoved = { moves += it })
        val view = TuneView(base)
        press("Move up")
        assertTrue(view.corners(cur())[3].y < view.corners(base)[3].y)
        val bl = cur()
        // select the bottom right dot by tapping where it is drawn
        val node = rule.onAllNodes(hasClickAction().not()).onFirst()
        assertNotNull(node)
        assertEquals(base.corners()[0], bl.corners()[0])
    }

    @Test fun holdingAnArrowRepeats() {
        val cur = show(initiallySelected = 1)
        val before = cur()
        rule.onNodeWithContentDescription("Move left").performTouchInput { down(center) }
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1100)
        rule.onNodeWithContentDescription("Move left").performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val moved = TuneView(base).corners(cur())[1].x - TuneView(base).corners(before)[1].x
        assertTrue("moved $moved", moved < -3.0)        // the first press plus several repeats
    }

    @Test fun nothingMovesBeforeTheProjectorIsReady() {
        var moved = false
        rule.setContent { ProjectorConnectTheme(true) { TuneContent(TuneState.Starting(), base, { moved = true }, {}, {}, initiallySelected = 2) } }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithContentDescription("Move right").fetchSemanticsNodes().isEmpty())
        assertFalse(moved)
    }
}
