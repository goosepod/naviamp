package app.naviamp.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampRemotePreparedHandoffTest {
    private fun media(id: Long, queue: Long = 1) =
        NaviampRemoteQueuedMedia(NaviampRemoteQueueOccurrence(queue, id), "http://192.0.2.1/media/occurrence-$id")

    @Test fun promotionIsObservedExactlyOnceAndDoesNotRequireLoadingAgain() {
        val ledger = NaviampRemotePreparedHandoff()
        ledger.reset(1, media(1))
        val ticket = assertNotNull(ledger.prepareNext(1))
        assertTrue(ledger.acceptPreparedNext(ticket, media(2)))
        assertEquals(media(1), ledger.current) // SetNext acceptance did not advance the queue.
        assertEquals(NaviampRemoteHandoffObservation.Ignored, ledger.observe(1, 1, media(2).mediaUrl, false))
        assertEquals(NaviampRemoteHandoffObservation.Promoted(media(2)), ledger.observe(1, 2, media(2).mediaUrl, true))
        assertEquals(NaviampRemoteHandoffObservation.Ignored, ledger.observe(1, 2, media(2).mediaUrl, true))
        assertEquals(NaviampRemoteHandoffObservation.Current, ledger.observe(1, 3, media(2).mediaUrl, true))
        assertEquals(NaviampRemoteHandoffObservation.Ignored, ledger.observe(1, 4, media(1).mediaUrl, true))
        assertNull(ledger.next)
        assertFalse(ledger.acceptPreparedNext(ticket, media(3)))
    }

    @Test fun queueEditsLatePreparationsAndOldSelectionsCannotPromote() {
        val ledger = NaviampRemotePreparedHandoff()
        ledger.reset(1, media(1))
        val old = assertNotNull(ledger.prepareNext(1))
        val fresh = assertNotNull(ledger.prepareNext(1))
        assertFalse(ledger.acceptPreparedNext(old, media(2)))
        assertTrue(ledger.acceptPreparedNext(fresh, media(3)))
        assertFalse(ledger.acceptPreparedNext(fresh, media(2)))
        ledger.reset(1, media(1, queue = 2))
        assertFalse(ledger.acceptPreparedNext(fresh, media(3)))
        val changed = assertNotNull(ledger.prepareNext(1))
        assertFalse(ledger.acceptPreparedNext(changed, media(2, queue = 1)))
        assertTrue(ledger.acceptPreparedNext(changed, media(2, queue = 2)))
        ledger.reset(2, media(4))
        assertEquals(NaviampRemoteHandoffObservation.Ignored, ledger.observe(1, 100, media(2).mediaUrl, true))
        assertEquals(media(4), ledger.current)
    }

    @Test fun repeatedSongNeedsANewOccurrenceAndScopedUri() {
        val ledger = NaviampRemotePreparedHandoff()
        ledger.reset(1, media(1))
        val ticket = assertNotNull(ledger.prepareNext(1))
        assertFalse(ledger.acceptPreparedNext(ticket, media(1)))
        assertFalse(ledger.acceptPreparedNext(ticket, media(2).copy(mediaUrl = media(1).mediaUrl)))
        assertTrue(ledger.acceptPreparedNext(ticket, media(2)))
        assertEquals(NaviampRemoteHandoffObservation.UnexpectedResource, ledger.observe(1, 2, "http://other-controller/track", true))
        assertEquals(media(1), ledger.current)
        assertEquals(NaviampRemoteHandoffObservation.Ignored, ledger.observe(1, 1, media(2).mediaUrl, true))
        ledger.clear(0) // A displaced controller cannot clear this session.
        assertEquals(media(2), ledger.next)
        ledger.clear(1)
        assertNull(ledger.current)
        assertNull(ledger.next)
        assertNull(ledger.prepareNext(1))
        assertEquals(NaviampRemoteHandoffObservation.Ignored, ledger.observe(1, 3, media(2).mediaUrl, true))
    }

    @Test fun ticketFromAnotherLedgerCannotStageMedia() {
        val first = NaviampRemotePreparedHandoff()
        val second = NaviampRemotePreparedHandoff()
        first.reset(1, media(1))
        second.reset(1, media(1))
        val foreign = assertNotNull(first.prepareNext(1))
        val own = assertNotNull(second.prepareNext(1))
        assertFalse(second.acceptPreparedNext(foreign, media(2)))
        assertTrue(second.acceptPreparedNext(own, media(2)))
    }
}
