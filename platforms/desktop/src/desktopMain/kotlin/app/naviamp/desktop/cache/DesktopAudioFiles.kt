package app.naviamp.desktop

import app.naviamp.domain.Track
import java.nio.file.Path

data class CachedAudioFile(
    val path: Path,
    val sizeBytes: Long,
    val contentType: String?,
)

data class CachedAudioMetadata(
    val path: Path,
    val exists: Boolean,
    val sizeBytes: Long,
    val contentType: String?,
    val createdAtEpochMillis: Long,
    val lastAccessedEpochMillis: Long,
)

data class DownloadedAudioFile(
    val path: Path,
    val sizeBytes: Long,
    val contentType: String?,
)

data class DownloadedTrack(
    val track: Track,
    val path: Path,
    val sizeBytes: Long,
    val contentType: String?,
    val qualityKey: String,
    val downloadedAtEpochMillis: Long,
)
