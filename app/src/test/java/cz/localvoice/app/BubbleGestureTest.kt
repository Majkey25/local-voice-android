package cz.localvoice.app

import kotlin.test.Test
import kotlin.test.assertEquals

class BubbleGestureTest {
    @Test
    fun tapStartsNormalToggleAction() {
        val gesture = BubbleGesture()

        gesture.down()

        assertEquals(BubbleGesture.Action.TAP, gesture.up())
    }

    @Test
    fun holdStartsAndReleaseStopsPushToTalk() {
        val gesture = BubbleGesture()

        gesture.down()
        assertEquals(BubbleGesture.Action.HOLD_START, gesture.hold())
        assertEquals(BubbleGesture.Action.HOLD_END, gesture.up())
    }

    @Test
    fun dragNeverBecomesTapOrHold() {
        val gesture = BubbleGesture(dragSlop = 6)

        gesture.down()
        assertEquals(BubbleGesture.Action.DRAG, gesture.move(deltaX = 7, deltaY = 0))
        assertEquals(BubbleGesture.Action.NONE, gesture.hold())
        assertEquals(BubbleGesture.Action.DRAG_END, gesture.up())
    }
}
