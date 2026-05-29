package app.naviamp.domain.settings

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.radio.internetRadioTrack
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionFormState(
    val displayName: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val skipTlsVerification: Boolean = false,
    val customCertificatePath: String = "",
    val clientCertificatePath: String = "",
    val clientCertificatePassword: String = "",
)

@Serializable
data class PlaybackSettings(
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    val gaplessEnabled: Boolean = true,
    val crossfadeDurationSeconds: Int = 0,
    val volumePercent: Int = 100,
    val debugLoggingEnabled: Boolean = false,
    val lrclibLyricsEnabled: Boolean = false,
    val previousButtonBehavior: PreviousButtonBehavior = PreviousButtonBehavior.RestartThenPrevious,
    val upNextSelectionBehavior: UpNextSelectionBehavior = UpNextSelectionBehavior.MoveSelectedToCurrent,
    val wifiStreamingQuality: StreamQualityPreference = StreamQualityPreference(),
    val mobileStreamingQuality: StreamQualityPreference = StreamQualityPreference(
        mode = StreamQualityMode.Transcode,
        codec = StreamingCodec.Opus,
        bitrateKbps = 192,
    ),
    val downloadQuality: StreamQualityPreference = StreamQualityPreference(),
    val allowMobileDownloads: Boolean = false,
)

fun PlaybackSettings.effectiveForEngine(playbackEngine: PlaybackEngine): PlaybackSettings {
    val effectiveGapless = playbackEngine.supportsGapless && gaplessEnabled
    return copy(
        replayGainMode = if (playbackEngine.supportsReplayGain) {
            replayGainMode
        } else {
            ReplayGainMode.Off
        },
        gaplessEnabled = effectiveGapless,
        crossfadeDurationSeconds = if (playbackEngine.supportsCrossfade) {
            if (effectiveGapless) 0 else crossfadeDurationSeconds.coerceIn(0, 12)
        } else {
            0
        },
        volumePercent = if (playbackEngine.supportsSoftwareVolume) {
            volumePercent.coerceIn(0, 100)
        } else {
            100
        },
        wifiStreamingQuality = wifiStreamingQuality.normalized(),
        mobileStreamingQuality = mobileStreamingQuality.normalized(),
        downloadQuality = downloadQuality.normalized(),
    )
}

@Serializable
data class StreamQualityPreference(
    val mode: StreamQualityMode = StreamQualityMode.Original,
    val codec: StreamingCodec = StreamingCodec.Mp3,
    val bitrateKbps: Int = 192,
) {
    fun normalized(): StreamQualityPreference =
        copy(
            bitrateKbps = bitrateKbps.takeIf { it in StreamBitrateKbpsOptions } ?: 192,
        )

    fun toStreamQuality(): StreamQuality =
        when (mode) {
            StreamQualityMode.Original -> StreamQuality.Original
            StreamQualityMode.Transcode -> StreamQuality.Transcoded(
                codec = codec.toAudioCodec(),
                bitrateKbps = normalized().bitrateKbps,
            )
        }
}

@Serializable
enum class StreamQualityMode {
    Original,
    Transcode,
}

@Serializable
enum class StreamingCodec {
    Mp3,
    Aac,
    Opus,
}

val StreamBitrateKbpsOptions: List<Int> = listOf(128, 192, 256, 320)

fun StreamingCodec.toAudioCodec(): AudioCodec =
    when (this) {
        StreamingCodec.Mp3 -> AudioCodec.Mp3
        StreamingCodec.Aac -> AudioCodec.Aac
        StreamingCodec.Opus -> AudioCodec.Opus
    }

fun PlaybackSettings.streamQualityForNetwork(isMobileData: Boolean): StreamQuality =
    (if (isMobileData) mobileStreamingQuality else wifiStreamingQuality)
        .normalized()
        .toStreamQuality()

fun PlaybackSettings.downloadStreamQuality(): StreamQuality =
    downloadQuality.normalized().toStreamQuality()

@Serializable
enum class PreviousButtonBehavior {
    AlwaysPrevious,
    RestartThenPrevious,
}

@Serializable
enum class UpNextSelectionBehavior {
    MoveSelectedToCurrent,
    SkipToSelected,
}

@Serializable
data class CacheSettings(
    val audioCachingEnabled: Boolean = true,
    val audioPrefetchDepth: Int = 10,
    val maxAudioCacheBytes: Long = 2L * 1024L * 1024L * 1024L,
    val maxDownloadBytes: Long = 10L * 1024L * 1024L * 1024L,
) {
    fun normalized(): CacheSettings =
        copy(
            audioPrefetchDepth = audioPrefetchDepth.coerceIn(0, 25),
            maxAudioCacheBytes = maxAudioCacheBytes.coerceIn(256L * 1024L * 1024L, 20L * 1024L * 1024L * 1024L),
            maxDownloadBytes = maxDownloadBytes.coerceIn(512L * 1024L * 1024L, 100L * 1024L * 1024L * 1024L),
        )
}

@Serializable
data class VisualizerSettings(
    val selectedVisualizer: String = DefaultSelectedVisualizer,
)

@Serializable
data class NavigationSettings(
    val route: String = "Home",
    val lastContentRoute: String = "Home",
)

@Serializable
data class SearchSettings(
    val query: String = "",
)

@Serializable
data class RecentRadioStream(
    val id: String,
    val label: String,
    val kind: RecentRadioKind,
    val artist: SavedArtist? = null,
    val album: SavedAlbum? = null,
    val track: SavedTrack? = null,
    val genre: String? = null,
    val fromYear: Int? = null,
    val toYear: Int? = null,
)

@Serializable
enum class RecentRadioKind {
    Library,
    RandomAlbum,
    Genre,
    Decade,
    Artist,
    Album,
    Track,
}

const val DefaultSelectedVisualizer = "AudioSphere"

@Serializable
data class SavedArtist(
    val id: String,
    val name: String,
) {
    fun toArtist(): Artist =
        Artist(id = ArtistId(id), name = name)

    companion object {
        fun fromArtist(artist: Artist): SavedArtist =
            SavedArtist(id = artist.id.value, name = artist.name)
    }
}

@Serializable
data class SavedAlbum(
    val id: String,
    val title: String,
    val artistName: String,
    val coverArtId: String? = null,
    val recentlyAddedAtIso8601: String? = null,
    val releaseYear: Int? = null,
) {
    fun toAlbum(): Album =
        Album(
            id = AlbumId(id),
            title = title,
            artistName = artistName,
            coverArtId = coverArtId,
            recentlyAddedAtIso8601 = recentlyAddedAtIso8601,
            releaseYear = releaseYear,
        )

    companion object {
        fun fromAlbum(album: Album): SavedAlbum =
            SavedAlbum(
                id = album.id.value,
                title = album.title,
                artistName = album.artistName,
                coverArtId = album.coverArtId,
                recentlyAddedAtIso8601 = album.recentlyAddedAtIso8601,
                releaseYear = album.releaseYear,
            )
    }
}

@Serializable
data class SavedInternetRadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val homePageUrl: String? = null,
) {
    fun toStation(): InternetRadioStation =
        InternetRadioStation(
            id = id,
            name = name,
            streamUrl = streamUrl,
            homePageUrl = homePageUrl,
        )

    companion object {
        fun fromStation(station: InternetRadioStation): SavedInternetRadioStation =
            SavedInternetRadioStation(
                id = station.id,
                name = station.name,
                streamUrl = station.streamUrl,
                homePageUrl = station.homePageUrl,
            )
    }
}

@Serializable
data class PlaybackSessionSettings(
    val tracks: List<SavedTrack> = emptyList(),
    val currentIndex: Int = -1,
    val positionSeconds: Double? = null,
    val internetRadioStation: SavedInternetRadioStation? = null,
) {
    fun currentTrack(): Track? =
        tracks.getOrNull(currentIndex)?.toTrack() ?: internetRadioStation?.toTrack()

    fun toTracks(): List<Track> =
        tracks.map { it.toTrack() }

    companion object {
        fun fromTracks(
            tracks: List<Track>,
            currentIndex: Int,
            positionSeconds: Double? = null,
        ): PlaybackSessionSettings? {
            if (tracks.isEmpty() || currentIndex !in tracks.indices) return null
            return PlaybackSessionSettings(
                tracks = tracks.map { SavedTrack.fromTrack(it) },
                currentIndex = currentIndex,
                positionSeconds = positionSeconds?.takeIf { it > 0.0 },
            )
        }

        fun fromInternetRadioStation(station: InternetRadioStation): PlaybackSessionSettings =
            PlaybackSessionSettings(
                internetRadioStation = SavedInternetRadioStation.fromStation(station),
            )
    }
}

private fun SavedInternetRadioStation.toTrack(): Track =
    internetRadioTrack(toStation())

@Serializable
data class SavedTrack(
    val id: String,
    val title: String,
    val artistId: String? = null,
    val artistName: String,
    val albumId: String? = null,
    val albumTitle: String? = null,
    val albumReleaseYear: Int? = null,
    val durationSeconds: Int? = null,
    val coverArtId: String? = null,
    val audioInfo: SavedAudioInfo? = null,
    val favoritedAtIso8601: String? = null,
    val userRating: Int? = null,
) {
    fun toTrack(): Track =
        Track(
            id = TrackId(id),
            title = title,
            artistId = artistId?.let { ArtistId(it) },
            artistName = artistName,
            albumId = albumId?.let { AlbumId(it) },
            albumTitle = albumTitle,
            albumReleaseYear = albumReleaseYear,
            durationSeconds = durationSeconds,
            coverArtId = coverArtId,
            audioInfo = audioInfo?.toAudioInfo(),
            replayGain = null,
            favoritedAtIso8601 = favoritedAtIso8601,
            userRating = userRating,
        )

    companion object {
        fun fromTrack(track: Track): SavedTrack =
            SavedTrack(
                id = track.id.value,
                title = track.title,
                artistId = track.artistId?.value,
                artistName = track.artistName,
                albumId = track.albumId?.value,
                albumTitle = track.albumTitle,
                albumReleaseYear = track.albumReleaseYear,
                durationSeconds = track.durationSeconds,
                coverArtId = track.coverArtId,
                audioInfo = track.audioInfo?.let { SavedAudioInfo.fromAudioInfo(it) },
                favoritedAtIso8601 = track.favoritedAtIso8601,
                userRating = track.userRating,
            )
    }
}

@Serializable
data class SavedAudioInfo(
    val codec: String? = null,
    val bitrateKbps: Int? = null,
    val contentType: String? = null,
    val bitDepth: Int? = null,
    val samplingRateHz: Int? = null,
) {
    fun toAudioInfo(): AudioInfo =
        AudioInfo(
            codec = codec,
            bitrateKbps = bitrateKbps,
            contentType = contentType,
            bitDepth = bitDepth,
            samplingRateHz = samplingRateHz,
        )

    companion object {
        fun fromAudioInfo(audioInfo: AudioInfo): SavedAudioInfo =
            SavedAudioInfo(
                codec = audioInfo.codec,
                bitrateKbps = audioInfo.bitrateKbps,
                contentType = audioInfo.contentType,
                bitDepth = audioInfo.bitDepth,
                samplingRateHz = audioInfo.samplingRateHz,
            )
    }
}
