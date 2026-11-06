package app.naviamp.domain.audio

import app.naviamp.domain.*
import app.naviamp.domain.provider.*
import app.naviamp.domain.playback.emptyPlaybackAudioAssetRepository
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class AudioFileMetadataTest {
    @Test fun flacHeaderSuppliesFilePropertiesWithoutConfusingBitrate() {
        val info = assertNotNull(audioInfoFromAudioBytes(flacHeader()))
        assertEquals(44100, info.samplingRateHz)
        assertEquals(16, info.bitDepth)
        val provider = AudioInfo("FLAC", 847, "audio/flac")
        val merged = assertNotNull(provider.withMissingFileProperties(info))
        assertEquals(847, merged.bitrateKbps)
        assertEquals(44100, merged.samplingRateHz)
        assertEquals(16, merged.bitDepth)
        assertNull(audioInfoFromAudioBytes(flacHeader().copyOf(30)))
        assertNull(audioInfoFromAudioBytes(ByteArray(42)))
        assertNull(audioInfoFromAudioBytes(flacHeader(rate = 0)))
        val hires = assertNotNull(audioInfoFromAudioBytes(flacHeader(rate = 192000, depth = 24)))
        assertEquals(192000, hires.samplingRateHz)
        assertEquals(24, hires.bitDepth)
        assertEquals(24, provider.copy(bitDepth = 24).withMissingFileProperties(info)?.bitDepth)
    }

    @Test fun streamsBoundedOriginalMetadataWithCacheDisabledAndReusesIt() = runTest {
        val provider = HeaderProvider(flacHeader())
        val service = AudioMetadataSidecarService(emptyPlaybackAudioAssetRepository()) { error("No local file") }
        val result = service.metadataForTrack("account", provider, track, StreamQuality.Original, false)
        assertEquals(44100, result.audioInfo?.samplingRateHz)
        assertEquals(listOf(AudioTag("Title", "Header title")), result.tags)
        assertEquals(StreamQuality.Original, provider.request?.quality)
        assertEquals("bytes=0-${MaximumAudioTagProbeBytes - 1}", provider.range)
        service.metadataForTrack("account", provider, track, StreamQuality.Original, false)
        assertEquals(1, provider.reads)
        service.metadataForTrack("other-account", provider, track, StreamQuality.Original, false)
        assertEquals(2, provider.reads)
    }

    @Test fun stopsServersThatIgnoreRangeAndRejectsNonzeroRanges() = runTest {
        val provider = HeaderProvider(ByteArray(65536), ignoreRange = true)
        val service = AudioMetadataSidecarService(emptyPlaybackAudioAssetRepository()) { emptyList() }
        service.metadataForTrack("account", provider, track, StreamQuality.Original, false)
        assertEquals(MaximumAudioTagProbeBytes, provider.delivered)
        val nonzero = HeaderProvider(flacHeader()).also { it.badRange = true }
        val rejected = service.metadataForTrack("second", nonzero, track, StreamQuality.Original, false)
        assertNull(rejected.audioInfo)
        assertTrue(rejected.tags.isEmpty())
    }

    @Test fun localTagsSurviveProbeFailuresAndCompleteProviderMetadataAvoidsTheProbe() = runTest {
        val provider = HeaderProvider(flacHeader()).also { it.failure = IllegalStateException("offline") }
        val service = AudioMetadataSidecarService(emptyPlaybackAudioAssetRepository()) {
            listOf(AudioTag("Title", "Local title"))
        }
        val local = app.naviamp.domain.playback.PlaybackLocalAudio("track.flac", "file://track.flac")
        val result = service.metadataForTrack("account", provider, track, StreamQuality.Original, false, local)
        assertEquals("Local title", result.tags.single().value)
        assertEquals(1, provider.reads)
        service.metadataForTrack("account", provider,
            track.copy(audioInfo = AudioInfo("FLAC", 847, "audio/flac", 16, 44100)),
            StreamQuality.Original, false, local)
        assertEquals(1, provider.reads)
    }

    @Test fun stalledProbesTimeOutAndRemainRetryable() = runTest {
        val provider = HeaderProvider(flacHeader()).also { it.delayMillis = 20_000 }
        val service = AudioMetadataSidecarService(emptyPlaybackAudioAssetRepository()) { emptyList() }
        val timeout = service.metadataForTrack("account", provider, track, StreamQuality.Original, false)
        assertTrue(timeout.tags.isEmpty())
        provider.delayMillis = 0
        assertTrue(service.metadataForTrack("account", provider, track, StreamQuality.Original, false).tags.isNotEmpty())
        assertEquals(2, provider.reads)
    }

    @Test fun failuresRetryButCancellationPropagatesAndSamplesAreNeverProbed() = runTest {
        val provider = HeaderProvider(flacHeader())
        val service = AudioMetadataSidecarService(emptyPlaybackAudioAssetRepository()) { emptyList() }
        provider.failure = IllegalStateException("offline")
        assertTrue(service.metadataForTrack("account", provider, track, StreamQuality.Original, false).tags.isEmpty())
        provider.failure = null
        assertTrue(service.metadataForTrack("account", provider, track, StreamQuality.Original, false).tags.isNotEmpty())
        provider.failure = CancellationException("track changed")
        assertFailsWith<CancellationException> {
            service.metadataForTrack("cancelled", provider, track, StreamQuality.Original, false)
        }
        val reads = provider.reads
        service.metadataForTrack("account", provider, track.copy(id = TrackId("discovery-preview:123")), StreamQuality.Original, false)
        assertEquals(reads, provider.reads)
    }
}

private val track = Track(TrackId("song"), "Track", artistName = "Artist", albumTitle = null,
    durationSeconds = null, coverArtId = null, audioInfo = null, replayGain = null)

private fun flacHeader(rate: Int = 44100, depth: Int = 16): ByteArray {
    val info = ByteArray(34)
    val packed = (rate.toLong() shl 44) or (1L shl 41) or ((depth - 1).toLong() shl 36)
    for (i in 0..7) info[10 + i] = (packed ushr ((7 - i) * 8)).toByte()
    val comment = "TITLE=Header title".encodeToByteArray()
    fun little(value: Int) = ByteArray(4) { (value ushr (8 * it)).toByte() }
    val comments = little(0) + little(1) + little(comment.size) + comment
    return "fLaC".encodeToByteArray() + byteArrayOf(0, 0, 0, 34) + info +
        byteArrayOf(0x84.toByte(), 0, 0, comments.size.toByte()) + comments
}

private class HeaderProvider(private val bytes: ByteArray, val ignoreRange: Boolean = false) : MediaProvider {
    override val id = ProviderId("test")
    override val displayName = "Test"
    override val capabilities = ProviderCapabilities(false, false, false, false, false)
    override val supportsPlaybackByteTransport = true
    var delayMillis = 0L
    var reads = 0
    var delivered = 0
    var badRange = false
    var failure: Exception? = null
    var request: StreamRequest? = null
    var range: String? = null
    override suspend fun streamTrackBytes(request: StreamRequest, rangeHeader: String?, headOnly: Boolean,
        onResponse: suspend (ProviderMediaByteResponse) -> Unit,
        writeChunk: suspend (ByteArray, Int) -> Unit): Boolean {
        reads++
        kotlinx.coroutines.delay(delayMillis)
        failure?.let { throw it }
        this.request = request
        range = rangeHeader
        onResponse(ProviderMediaByteResponse(if (ignoreRange) 200 else 206, "audio/flac", bytes.size.toLong(),
            if (badRange) "bytes 1-99/100" else "bytes 0-${bytes.lastIndex}/${bytes.size}"))
        do { delivered += bytes.size; writeChunk(bytes, bytes.size) } while (ignoreRange)
        return true
    }
    override suspend fun validateConnection(): ConnectionValidation = error("unused")
    override suspend fun recentlyAddedAlbums(limit: Int): List<Album> = error("unused")
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("unused")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("unused")
    override suspend fun artists(limit: Int): List<Artist> = error("unused")
    override suspend fun tracks(limit: Int): List<Track> = error("unused")
    override suspend fun search(query: String, limit: Int): MediaSearchResults = error("unused")
    override suspend fun streamUrl(request: StreamRequest): String = error("unused")
    override fun coverArtUrl(coverArtId: String): String = error("unused")
}
