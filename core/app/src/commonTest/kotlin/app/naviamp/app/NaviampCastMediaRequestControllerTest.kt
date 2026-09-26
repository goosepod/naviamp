package app.naviamp.app

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NaviampCastMediaRequestControllerTest {
    @Test
    fun onlyLiveOpaqueLeasePathCanReachMedia() = runTest {
        val leases = NaviampCastMediaLeaseController(
            tokens = NaviampCastSecureTokenSource { "abcdefghijklmnopqrstuvwxyz012345" },
            nowEpochMillis = { 0L },
        )
        val resource = NaviampCastMediaResource(NaviampCastMediaKind.Track, "source", "provider-secret-id")
        val lease = leases.issue(resource)
        val requests = NaviampCastMediaRequestController(leases)

        assertEquals(resource,
            assertIs<NaviampCastMediaRequestDecision.Allowed>(
                requests.authorize("GET", lease.receiverPath, null),
            ).resource)
        assertEquals(NaviampCastMediaRequestDecision.NotFound,
            requests.authorize("GET", "/cast/media/provider-secret-id", null))
        assertEquals(NaviampCastMediaRequestDecision.NotFound,
            requests.authorize("GET", "${lease.receiverPath}/../other", null))
        assertEquals(NaviampCastMediaRequestDecision.MethodNotAllowed,
            requests.authorize("POST", lease.receiverPath, null))
        leases.revokeAll()
        assertEquals(NaviampCastMediaRequestDecision.NotFound,
            requests.authorize("GET", lease.receiverPath, null))
    }

    @Test
    fun acceptsSingleByteRangeFormsAndRejectsMalformedRanges() = runTest {
        val leases = NaviampCastMediaLeaseController(
            tokens = NaviampCastSecureTokenSource { "abcdefghijklmnopqrstuvwxyz012345" },
            nowEpochMillis = { 0L },
        )
        val path = leases.issue(NaviampCastMediaResource(NaviampCastMediaKind.Artwork, "source", "cover")).receiverPath
        val requests = NaviampCastMediaRequestController(leases)

        assertEquals(NaviampCastRequestedRange.From(10, 99),
            assertIs<NaviampCastMediaRequestDecision.Allowed>(
                requests.authorize("GET", path, "bytes=10-99"),
            ).range)
        assertEquals(NaviampCastRequestedRange.From(10),
            assertIs<NaviampCastMediaRequestDecision.Allowed>(
                requests.authorize("HEAD", path, "bytes=10-"),
            ).range)
        assertEquals(NaviampCastRequestedRange.Suffix(100),
            assertIs<NaviampCastMediaRequestDecision.Allowed>(
                requests.authorize("GET", path, "bytes=-100"),
            ).range)
        listOf("bytes=99-10", "bytes=0-1,3-4", "bytes=-0", "items=0-1", "bytes=broken-")
            .forEach { header ->
                assertEquals(NaviampCastMediaRequestDecision.InvalidRange,
                    requests.authorize("GET", path, header))
            }
    }

    @Test
    fun resolvesRangesAgainstKnownArtworkLength() {
        assertEquals(NaviampCastResolvedRange(10, 19, 100),
            NaviampCastRequestedRange.From(10, 19).resolve(100))
        assertEquals(NaviampCastResolvedRange(90, 99, 100),
            NaviampCastRequestedRange.Suffix(10).resolve(100))
        assertEquals(NaviampCastResolvedRange(0, 99, 100),
            NaviampCastRequestedRange.Suffix(200).resolve(100))
        assertEquals(null, NaviampCastRequestedRange.From(100).resolve(100))
        assertEquals("bytes=10-", NaviampCastRequestedRange.From(10).toHttpHeader())
    }
}
