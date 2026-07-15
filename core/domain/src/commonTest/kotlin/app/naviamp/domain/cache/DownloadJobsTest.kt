package app.naviamp.domain.cache

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.StreamQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadJobsTest {
    @Test
    fun transcodedDownloadsUseTheActualOutputFormatAndQualityLabel() {
        val quality = StreamQuality.Transcoded(AudioCodec.Opus, 128)

        assertEquals("audio/ogg", quality.downloadContentType("audio/flac"))
        assertEquals("OPUS · 128 kbps", downloadedAudioQualityLabel("transcoded:opus:128", null, "audio/ogg"))
    }

    @Test
    fun originalDownloadsShowTheirSourceQuality() {
        val audioInfo = AudioInfo(
            codec = "flac",
            bitrateKbps = 900,
            contentType = "audio/flac",
            bitDepth = 24,
            samplingRateHz = 96_000,
        )

        assertEquals("audio/flac", StreamQuality.Original.downloadContentType("audio/flac"))
        assertEquals("FLAC · 24-bit / 96 kHz", downloadedAudioQualityLabel("original", audioInfo, "audio/flac"))
    }

    @Test
    fun createDownloadJobStartsQueuedAndDeduplicatesTracks() {
        val first = track("one")
        val job = createDownloadJob(
            id = "job-1",
            label = "Album",
            tracks = listOf(first, track("two"), first),
        )

        assertEquals(DownloadJobStatus.Queued, job.status)
        assertEquals(listOf("one", "two"), job.items.map { it.track.id.value })
        assertEquals(0, job.completedCount)
        assertEquals(2, job.totalCount)
        assertEquals(0f, job.progress)
        assertTrue(job.canCancel)
        assertFalse(job.canRetry)
    }

    @Test
    fun updatesTrackAndAggregateProgress() {
        val job = createDownloadJob("job-1", "Album", listOf(track("one"), track("two")))
            .updated(DownloadJobUpdate.Started)
            .updated(DownloadJobUpdate.TrackStarted("one"))
            .updated(DownloadJobUpdate.TrackCompleted("one"))

        assertEquals(DownloadJobStatus.Running, job.status)
        assertEquals(DownloadJobItemStatus.Completed, job.items[0].status)
        assertEquals(DownloadJobItemStatus.Pending, job.items[1].status)
        assertEquals(1, job.completedCount)
        assertEquals(0.5f, job.progress)
    }

    @Test
    fun failedJobRetainsCompletedTracksAndExposesRetryableRemainder() {
        val job = createDownloadJob("job-1", "Album", listOf(track("one"), track("two"), track("three")))
            .updated(DownloadJobUpdate.TrackCompleted("one"))
            .updated(DownloadJobUpdate.TrackStarted("two"))
            .updated(DownloadJobUpdate.Failed("two", "Network unavailable"))

        assertEquals(DownloadJobStatus.Failed, job.status)
        assertEquals(DownloadJobItemStatus.Failed, job.items[1].status)
        assertEquals("Network unavailable", job.items[1].failureMessage)
        assertEquals(listOf("two", "three"), job.retryTracks.map { it.id.value })
        assertFalse(job.canCancel)
        assertTrue(job.canRetry)
    }

    @Test
    fun cancellationKeepsCompletedTracksAndCancelsTheRemainder() {
        val job = createDownloadJob("job-1", "Album", listOf(track("one"), track("two")))
            .updated(DownloadJobUpdate.TrackCompleted("one"))
            .updated(DownloadJobUpdate.TrackStarted("two"))
            .updated(DownloadJobUpdate.Cancelled)

        assertEquals(DownloadJobStatus.Cancelled, job.status)
        assertEquals(DownloadJobItemStatus.Completed, job.items[0].status)
        assertEquals(DownloadJobItemStatus.Cancelled, job.items[1].status)
        assertEquals(listOf("two"), job.retryTracks.map { it.id.value })
    }

    @Test
    fun completedJobMarksEveryItemComplete() {
        val job = createDownloadJob("job-1", "Album", listOf(track("one"), track("two")))
            .updated(DownloadJobUpdate.Completed)

        assertEquals(DownloadJobStatus.Completed, job.status)
        assertEquals(2, job.completedCount)
        assertEquals(1f, job.progress)
        assertFalse(job.canCancel)
        assertFalse(job.canRetry)
    }

    @Test
    fun retryJobContainsOnlyTheUnfinishedRemainder() {
        val failed = createDownloadJob("job-1", "Album", listOf(track("one"), track("two")))
            .updated(DownloadJobUpdate.TrackCompleted("one"))
            .updated(DownloadJobUpdate.Failed("two", "offline"))

        val retry = failed.retryJob("job-2")

        assertEquals("job-2", retry.id)
        assertEquals(listOf("two"), retry.items.map { it.track.id.value })
        assertEquals(DownloadJobStatus.Queued, retry.status)
    }

    @Test
    fun recentJobsAreBoundedAndKeepActiveJobsFirst() {
        val cancelled = (1..MaximumRecentDownloadJobs).map { index ->
            createDownloadJob("job-$index", "Job $index", listOf(track("track-$index")))
                .updated(DownloadJobUpdate.Cancelled)
        }
        val active = createDownloadJob("job-active", "Active", listOf(track("active")))
            .updated(DownloadJobUpdate.Started)

        val jobs = cancelled.fold(emptyList<DownloadJob>()) { jobs, job -> jobs.withDownloadJob(job) }
            .withDownloadJob(active)

        assertEquals(MaximumRecentDownloadJobs + 1, jobs.size)
        assertEquals("job-active", jobs.first().id)
    }

    @Test
    fun completedJobsAreRemovedFromDownloadActivity() {
        val running = createDownloadJob("job-1", "Album", listOf(track("one")))
            .updated(DownloadJobUpdate.Started)

        val jobs = listOf(running).withDownloadJob(running.updated(DownloadJobUpdate.Completed))

        assertTrue(jobs.isEmpty())
    }

    private fun track(id: String): Track =
        Track(
            id = TrackId(id),
            title = id,
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
