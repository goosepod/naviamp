package app.naviamp.app

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NaviampCastMediaLeaseControllerTest {
    @Test
    fun receiverPathContainsOnlyOpaqueTokenAndExpires() = runTest {
        var now = 1_000L
        val leases = NaviampCastMediaLeaseController(
            tokens = NaviampCastSecureTokenSource { "abcdefghijklmnopqrstuvwxyz012345" },
            nowEpochMillis = { now },
            lifetimeMillis = 500,
        )
        val resource = NaviampCastMediaResource(
            NaviampCastMediaKind.Track,
            "source",
            "provider-track?api_key=private",
        )

        val lease = leases.issue(resource)

        assertEquals("/cast/media/abcdefghijklmnopqrstuvwxyz012345", lease.receiverPath)
        assertEquals(resource, leases.resolve(lease.token))
        now = 1_500L
        assertNull(leases.resolve(lease.token))
    }

    @Test
    fun revokeAndSessionEndRemoveAccess() = runTest {
        var next = 0
        val leases = NaviampCastMediaLeaseController(
            tokens = NaviampCastSecureTokenSource { (++next).toString().padStart(32, 'a') },
            nowEpochMillis = { 1_000L },
        )
        val track = leases.issue(NaviampCastMediaResource(NaviampCastMediaKind.Track, "source", "song"))
        val artwork = leases.issue(NaviampCastMediaResource(NaviampCastMediaKind.Artwork, "source", "cover"))

        leases.revoke(track.token)
        assertNull(leases.resolve(track.token))
        assertEquals(NaviampCastMediaKind.Artwork, leases.resolve(artwork.token)?.kind)
        leases.revokeAll()
        assertNull(leases.resolve(artwork.token))
    }

    @Test
    fun weakMalformedOrReusedTokensCannotBeIssued() = runTest {
        val resource = NaviampCastMediaResource(NaviampCastMediaKind.Track, "source", "song")
        val weak = NaviampCastMediaLeaseController(
            tokens = NaviampCastSecureTokenSource { "short" },
            nowEpochMillis = { 0L },
        )
        assertFailsWith<IllegalArgumentException> { weak.issue(resource) }

        val repeated = NaviampCastMediaLeaseController(
            tokens = NaviampCastSecureTokenSource { "abcdefghijklmnopqrstuvwxyz012345" },
            nowEpochMillis = { 0L },
        )
        repeated.issue(resource)
        assertFailsWith<IllegalStateException> { repeated.issue(resource) }
    }
}
