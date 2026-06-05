package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.queue.PlaybackQueue
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackProgressTest {
    @Test
    fun mergeMissingWithKeepsPreviousValuesOnlyForUnknownFields() {
        val merged = PlaybackProgress(
            positionSeconds = null,
            durationSeconds = 240.0,
        ).mergeMissingWith(
            PlaybackProgress(
                positionSeconds = 42.0,
                durationSeconds = 180.0,
            ),
        )

        assertEquals(42.0, merged.positionSeconds)
        assertEquals(240.0, merged.durationSeconds)
    }

    @Test
    fun progressUiUpdateRequiresMeaningfulMovementDurationChangeOrInterval() {
        assertEquals(
            false,
            shouldUpdatePlaybackProgressUi(
                pendingSeekPositionSeconds = null,
                currentProgress = PlaybackProgress(positionSeconds = 10.0, durationSeconds = 200.0),
                mergedProgress = PlaybackProgress(positionSeconds = 10.2, durationSeconds = 200.0),
                nowMillis = 1_200,
                lastUiUpdateMillis = 1_000,
                positionThresholdSeconds = 1.0,
                updateIntervalMillis = 1_000,
            ),
        )
        assertEquals(
            true,
            shouldUpdatePlaybackProgressUi(
                pendingSeekPositionSeconds = null,
                currentProgress = PlaybackProgress(positionSeconds = 10.0, durationSeconds = 200.0),
                mergedProgress = PlaybackProgress(positionSeconds = 12.0, durationSeconds = 200.0),
                nowMillis = 1_200,
                lastUiUpdateMillis = 1_000,
                positionThresholdSeconds = 1.0,
                updateIntervalMillis = 1_000,
            ),
        )
        assertEquals(
            true,
            shouldUpdatePlaybackProgressUi(
                pendingSeekPositionSeconds = null,
                currentProgress = PlaybackProgress(positionSeconds = 10.0, durationSeconds = 200.0),
                mergedProgress = PlaybackProgress(positionSeconds = 10.2, durationSeconds = 201.0),
                nowMillis = 1_200,
                lastUiUpdateMillis = 1_000,
                positionThresholdSeconds = 1.0,
                updateIntervalMillis = 1_000,
            ),
        )
        assertEquals(
            true,
            shouldUpdatePlaybackProgressUi(
                pendingSeekPositionSeconds = 60.0,
                currentProgress = PlaybackProgress(positionSeconds = 10.0, durationSeconds = 200.0),
                mergedProgress = PlaybackProgress(positionSeconds = 10.2, durationSeconds = 200.0),
                nowMillis = 1_200,
                lastUiUpdateMillis = 1_000,
                positionThresholdSeconds = 1.0,
                updateIntervalMillis = 1_000,
            ),
        )
    }

    @Test
    fun playbackPositionSaveRequiresCurrentTrackAndThresholdMovement() {
        val queue = PlaybackQueue(tracks = listOf(track("one")), currentIndex = 0)

        assertEquals(
            false,
            shouldSavePlaybackPosition(
                queue = queue,
                positionSeconds = null,
                lastSavedPositionSeconds = null,
                saveThresholdSeconds = 5.0,
            ),
        )
        assertEquals(
            false,
            shouldSavePlaybackPosition(
                queue = PlaybackQueue(),
                positionSeconds = 10.0,
                lastSavedPositionSeconds = null,
                saveThresholdSeconds = 5.0,
            ),
        )
        assertEquals(
            true,
            shouldSavePlaybackPosition(
                queue = queue,
                positionSeconds = 10.0,
                lastSavedPositionSeconds = null,
                saveThresholdSeconds = 5.0,
            ),
        )
        assertEquals(
            false,
            shouldSavePlaybackPosition(
                queue = queue,
                positionSeconds = 12.0,
                lastSavedPositionSeconds = 10.0,
                saveThresholdSeconds = 5.0,
            ),
        )
        assertEquals(
            true,
            shouldSavePlaybackPosition(
                queue = queue,
                positionSeconds = 15.0,
                lastSavedPositionSeconds = 10.0,
                saveThresholdSeconds = 5.0,
            ),
        )
    }

    @Test
    fun ignoresStaleEngineProgressSoonAfterSeek() {
        assertEquals(
            true,
            shouldIgnoreProgressForPendingSeek(
                pendingSeekPositionSeconds = 60.0,
                pendingSeekIssuedAtMillis = 1_000,
                incomingPositionSeconds = 12.0,
                nowMillis = 1_500,
                toleranceSeconds = 1.0,
                staleWindowMillis = 2_000,
            ),
        )
    }

    @Test
    fun acceptsEngineProgressAfterSeekWindowExpires() {
        assertEquals(
            false,
            shouldIgnoreProgressForPendingSeek(
                pendingSeekPositionSeconds = 60.0,
                pendingSeekIssuedAtMillis = 1_000,
                incomingPositionSeconds = 12.0,
                nowMillis = 3_500,
                toleranceSeconds = 1.0,
                staleWindowMillis = 2_000,
            ),
        )
    }

    @Test
    fun clearsPendingSeekWhenIncomingProgressReachesTarget() {
        assertEquals(
            true,
            shouldClearPendingSeek(
                pendingSeekPositionSeconds = 60.0,
                pendingSeekIssuedAtMillis = 1_000,
                incomingPositionSeconds = 60.5,
                nowMillis = 1_500,
                toleranceSeconds = 1.0,
                staleWindowMillis = 2_000,
            ),
        )
    }

    @Test
    fun clearsPendingSeekWhenProgressIsUnknownOrWindowExpires() {
        assertEquals(
            true,
            shouldClearPendingSeek(
                pendingSeekPositionSeconds = 60.0,
                pendingSeekIssuedAtMillis = 1_000,
                incomingPositionSeconds = null,
                nowMillis = 1_500,
                toleranceSeconds = 1.0,
                staleWindowMillis = 2_000,
            ),
        )
        assertEquals(
            true,
            shouldClearPendingSeek(
                pendingSeekPositionSeconds = 60.0,
                pendingSeekIssuedAtMillis = 1_000,
                incomingPositionSeconds = 12.0,
                nowMillis = 3_000,
                toleranceSeconds = 1.0,
                staleWindowMillis = 2_000,
            ),
        )
    }

    @Test
    fun detectsWhenPendingSeekReachedTarget() {
        assertEquals(
            true,
            hasPendingSeekReachedTarget(
                pendingSeekPositionSeconds = 60.0,
                incomingPositionSeconds = 60.5,
                toleranceSeconds = 1.0,
            ),
        )
        assertEquals(
            false,
            hasPendingSeekReachedTarget(
                pendingSeekPositionSeconds = 60.0,
                incomingPositionSeconds = 58.0,
                toleranceSeconds = 1.0,
            ),
        )
    }

    @Test
    fun progressUpdatePlanIgnoresInactiveSessions() {
        val plan = planPlaybackProgressUpdate(
            sessionToken = 1,
            activeSessionToken = 2,
            incomingProgress = PlaybackProgress(positionSeconds = 12.0, durationSeconds = 180.0),
            currentProgress = PlaybackProgress.Unknown,
            pendingSeekPositionSeconds = null,
            pendingSeekIssuedAtMillis = null,
            pendingRestoreStartPositionSeconds = null,
            nowMillis = 1_000,
            lastExternalProgressPublishAtMillis = 0,
            externalProgressPublishIntervalMillis = 1_000,
        )

        assertEquals(true, plan.ignore)
    }

    @Test
    fun progressUpdatePlanResetsUnknownProgressWhenRestoreIsNotPending() {
        val plan = planPlaybackProgressUpdate(
            sessionToken = 1,
            activeSessionToken = 1,
            incomingProgress = PlaybackProgress.Unknown,
            currentProgress = PlaybackProgress(positionSeconds = 12.0, durationSeconds = 180.0),
            pendingSeekPositionSeconds = 60.0,
            pendingSeekIssuedAtMillis = 900,
            pendingRestoreStartPositionSeconds = null,
            nowMillis = 1_000,
            lastExternalProgressPublishAtMillis = 0,
            externalProgressPublishIntervalMillis = 1_000,
        )

        assertEquals(false, plan.ignore)
        assertEquals(true, plan.resetToUnknown)
        assertEquals(true, plan.clearPendingSeek)
        assertEquals(true, plan.shouldPublishExternalProgress)
    }

    @Test
    fun progressUpdatePlanMergesProgressAndSchedulesSideEffects() {
        val plan = planPlaybackProgressUpdate(
            sessionToken = 1,
            activeSessionToken = 1,
            incomingProgress = PlaybackProgress(positionSeconds = 14.0, durationSeconds = null),
            currentProgress = PlaybackProgress(positionSeconds = 12.0, durationSeconds = 180.0),
            pendingSeekPositionSeconds = null,
            pendingSeekIssuedAtMillis = null,
            pendingRestoreStartPositionSeconds = null,
            nowMillis = 2_000,
            lastExternalProgressPublishAtMillis = 500,
            externalProgressPublishIntervalMillis = 1_000,
        )

        assertEquals(14.0, plan.progress?.positionSeconds)
        assertEquals(180.0, plan.progress?.durationSeconds)
        assertEquals(true, plan.shouldReportPlayed)
        assertEquals(true, plan.shouldPublishExternalProgress)
        assertEquals(true, plan.shouldPrepareNext)
    }

    @Test
    fun progressUpdatePlanClearsPendingRestoreWhenIncomingProgressReachesStart() {
        val plan = planPlaybackProgressUpdate(
            sessionToken = 1,
            activeSessionToken = 1,
            incomingProgress = PlaybackProgress(positionSeconds = 59.0, durationSeconds = 180.0),
            currentProgress = PlaybackProgress.Unknown,
            pendingSeekPositionSeconds = 60.0,
            pendingSeekIssuedAtMillis = 1_000,
            pendingRestoreStartPositionSeconds = 60.0,
            nowMillis = 1_200,
            lastExternalProgressPublishAtMillis = 1_000,
            externalProgressPublishIntervalMillis = 1_000,
            toleranceSeconds = 2.0,
            staleWindowMillis = 1_500,
        )

        assertEquals(false, plan.ignore)
        assertEquals(true, plan.clearPendingSeek)
        assertEquals(true, plan.clearPendingRestoreStart)
    }

    @Test
    fun mergeWithKeepsPreviousValuesWhenCurrentReadIsUnknown() {
        val previous = PlaybackProgress(
            positionSeconds = 42.0,
            durationSeconds = 180.0,
        )

        val merged = PlaybackProgress.Unknown.mergeWith(previous)

        assertEquals(42.0, merged.positionSeconds)
        assertEquals(180.0, merged.durationSeconds)
    }

    @Test
    fun mergeWithIgnoresLargeBackwardPositionJumps() {
        val previous = PlaybackProgress(
            positionSeconds = 42.0,
            durationSeconds = 180.0,
        )

        val merged = PlaybackProgress(
            positionSeconds = 0.0,
            durationSeconds = 180.0,
        ).mergeWith(previous)

        assertEquals(42.0, merged.positionSeconds)
        assertEquals(180.0, merged.durationSeconds)
    }

    @Test
    fun mergeWithAllowsSmallBackwardPositionCorrection() {
        val previous = PlaybackProgress(
            positionSeconds = 42.0,
            durationSeconds = 180.0,
        )

        val merged = PlaybackProgress(
            positionSeconds = 41.4,
            durationSeconds = 180.0,
        ).mergeWith(previous)

        assertEquals(41.4, merged.positionSeconds)
    }

    private fun track(id: String): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
