package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SwipeGestureIntentLockTest {
    @Test
    fun movementInsideDeadZoneRemainsPending() {
        val lock = SwipeGestureIntentLock(deadZonePx = 10f)

        assertEquals(SwipeGestureIntent.Pending, lock.update(horizontalDistance = 9f, verticalDistance = 9f))
        assertEquals(9f, lock.horizontalDistance)
    }

    @Test
    fun clearlyHorizontalMovementLocksForTheRestOfTheGesture() {
        val lock = SwipeGestureIntentLock(deadZonePx = 10f)

        assertEquals(SwipeGestureIntent.Horizontal, lock.update(horizontalDistance = 14f, verticalDistance = 4f))
        assertEquals(SwipeGestureIntent.Horizontal, lock.update(horizontalDistance = -30f, verticalDistance = 80f))
        assertEquals(-30f, lock.horizontalDistance)
    }

    @Test
    fun verticalAndAmbiguousDiagonalMovementYieldTheGesture() {
        val vertical = SwipeGestureIntentLock(deadZonePx = 10f)
        val diagonal = SwipeGestureIntentLock(deadZonePx = 10f)

        assertEquals(SwipeGestureIntent.Vertical, vertical.update(horizontalDistance = 4f, verticalDistance = 14f))
        assertEquals(SwipeGestureIntent.Vertical, diagonal.update(horizontalDistance = 14f, verticalDistance = 12f))
    }

    @Test
    fun aDecidedVerticalGestureCannotReverseIntoAHorizontalSwipe() {
        val lock = SwipeGestureIntentLock(deadZonePx = 10f)

        assertEquals(SwipeGestureIntent.Vertical, lock.update(horizontalDistance = 3f, verticalDistance = 11f))
        assertEquals(SwipeGestureIntent.Vertical, lock.update(horizontalDistance = 100f, verticalDistance = 12f))
    }

    @Test
    fun leftAndRightMovementUseTheSameDominanceRule() {
        val right = SwipeGestureIntentLock(deadZonePx = 10f)
        val left = SwipeGestureIntentLock(deadZonePx = 10f)

        assertEquals(SwipeGestureIntent.Horizontal, right.update(horizontalDistance = 20f, verticalDistance = 5f))
        assertEquals(SwipeGestureIntent.Horizontal, left.update(horizontalDistance = -20f, verticalDistance = 5f))
    }
}
