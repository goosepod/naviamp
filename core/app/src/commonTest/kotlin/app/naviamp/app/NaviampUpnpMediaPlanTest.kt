package app.naviamp.app

import app.naviamp.domain.StreamQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class NaviampUpnpMediaPlanTest {
    private val flac = NaviampUpnpSourceFormat(NaviampUpnpAudioFormat.Flac, "audio/flac", 24, 96_000, 2)

    @Test fun losslessOriginalSurvivesMp3FirstAdvertisementAndMimeAliases() {
        val sinks = parseNaviampUpnpSinkProtocols("http-get:*:audio/mpeg:*, http-get:*:audio/x-flac:*")
        val plan = assertIs<NaviampUpnpMediaPlan.Original>(planNaviampUpnpMedia(flac, sinks))
        assertEquals(StreamQuality.Original, plan.quality)
        assertEquals("audio/x-flac", plan.contentType)
        assertEquals(flac, plan.source)
    }

    @Test fun unsupportedLosslessNeverFallsBackToLossyOrWildcard() {
        for (advertisement in listOf("", "http-get:*:audio/mpeg:*", "http-get:*:*:*", "rtsp-rtp-udp:*:audio/flac:*")) {
            assertEquals(NaviampUpnpMediaPlan.Rejected(NaviampUpnpMediaRejection.NoCompatibleOriginal),
                planNaviampUpnpMedia(flac, parseNaviampUpnpSinkProtocols(advertisement)))
        }
    }

    @Test fun profileConstraintsAndInvalidMetadataAreNotGuessed() {
        val constrained = parseNaviampUpnpSinkProtocols("http-get:*:audio/flac:DLNA.ORG_PN=VENDOR_FLAC")
        assertEquals(NaviampUpnpMediaPlan.Rejected(NaviampUpnpMediaRejection.RequiresProfileVerification),
            planNaviampUpnpMedia(flac, constrained))
        val sinks = parseNaviampUpnpSinkProtocols("http-get:*:audio/flac:*")
        assertEquals(NaviampUpnpMediaPlan.Rejected(NaviampUpnpMediaRejection.InvalidSourceMetadata),
            planNaviampUpnpMedia(flac.copy(sampleRateHz = 0), sinks))
        assertEquals(NaviampUpnpMediaPlan.Rejected(NaviampUpnpMediaRejection.SourceFormatMismatch),
            planNaviampUpnpMedia(flac.copy(contentType = "audio/mpeg"), sinks))
    }

    @Test fun unknownSampleParametersRemainUnknownAndLossySourcesRemainLossy() {
        val unknown = flac.copy(bitDepth = null, sampleRateHz = null, channelCount = null)
        val plan = assertIs<NaviampUpnpMediaPlan.Original>(planNaviampUpnpMedia(unknown,
            parseNaviampUpnpSinkProtocols("HTTP-GET:*:Audio/FLAC:*")))
        assertNull(plan.source.bitDepth)
        assertNull(plan.source.sampleRateHz)
        assertNull(plan.source.channelCount)
        val mp3 = NaviampUpnpSourceFormat(NaviampUpnpAudioFormat.Mp3, "audio/mpeg")
        val lossy = assertIs<NaviampUpnpMediaPlan.Original>(planNaviampUpnpMedia(mp3,
            parseNaviampUpnpSinkProtocols("http-get:*:audio/mpeg:*")))
        assertEquals(false, lossy.source.format.lossless)
    }

    @Test fun malformedOrOversizedProtocolInfoCannotAuthorizePlayback() {
        for (value in listOf("http-get:*:audio/flac", "http-get::audio/flac:*", "http-get:*:audio/flac:*\r\nX:bad",
            "x".repeat(65_537), List(1_025) { "http-get:*:audio/flac:*" }.joinToString(","))) {
            assertEquals(emptyList(), parseNaviampUpnpSinkProtocols(value))
        }
        assertEquals(1, parseNaviampUpnpSinkProtocols("invalid,http-get:*:audio/flac:*").size)
    }
}
