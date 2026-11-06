package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampSurfacePresentationTest {
    @Test fun showingBeforeContentStillRequestsTheFirstFrameAfterCommit() {
        val presentation = NaviampSurfacePresentation()
        assertFalse(presentation.visibilityChanged(true))
        assertTrue(presentation.contentAttached(true))
        repeat(100) { assertFalse(presentation.contentAttached(true)) }
    }

    @Test fun contentCommittedBeforeShowingRequestsAFrameWhenShown() {
        val presentation = NaviampSurfacePresentation()
        assertFalse(presentation.contentAttached(false))
        assertTrue(presentation.visibilityChanged(true))
        repeat(100) { assertFalse(presentation.visibilityChanged(true)) }
    }

    @Test fun restoringRequestsOneFrameWithoutContinuousInvalidation() {
        val presentation = NaviampSurfacePresentation()
        assertTrue(presentation.contentAttached(true))
        assertFalse(presentation.visibilityChanged(false))
        assertTrue(presentation.visibilityChanged(true))
        assertFalse(presentation.contentAttached(true))
    }
}
