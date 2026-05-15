package app.naviamp.ui

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.waveform.AudioWaveform

fun Artist.toSharedMediaItemUi(): SharedMediaItemUi =
    SharedMediaItemUi(id = id.value, title = name, subtitle = "Artist")

fun Album.toSharedMediaItemUi(coverArtUrl: (String?) -> String?): SharedMediaItemUi =
    SharedMediaItemUi(
        id = id.value,
        title = title,
        subtitle = artistName,
        meta = releaseYear?.toString().orEmpty(),
        coverArtUrl = coverArtUrl(coverArtId),
    )

fun Playlist.toSharedMediaItemUi(
    coverArtUrl: (String?) -> String?,
    tracks: List<Track> = emptyList(),
): SharedMediaItemUi =
    SharedMediaItemUi(
        id = id,
        title = name,
        subtitle = "$trackCount tracks",
        meta = durationSeconds?.durationLabel().orEmpty(),
        coverArtUrl = coverArtUrl(coverArtId),
        coverArtUrls = tracks.mapNotNull { coverArtUrl(it.coverArtId) }.distinct().take(4),
    )

fun InternetRadioStation.toSharedMediaItemUi(): SharedMediaItemUi =
    SharedMediaItemUi(id = id, title = name, subtitle = homePageUrl ?: "Internet radio")

fun Track.toAndroidTrackRowUi(coverArtUrl: (String?) -> String?): AndroidTrackRowUi =
    AndroidTrackRowUi(
        id = id.value,
        title = title,
        subtitle = listOfNotNull(artistName, albumTitle).joinToString(" - "),
        coverArtUrl = coverArtUrl(coverArtId),
        meta = durationSeconds?.durationLabel().orEmpty(),
    )

fun Track.toNowPlayingItemUi(coverArtUrl: (String?) -> String?): NaviampNowPlayingItemUi =
    NaviampNowPlayingItemUi(
        id = id.value,
        title = title,
        subtitle = listOfNotNull(artistName, albumTitle).joinToString(" - "),
        meta = durationSeconds?.durationLabel().orEmpty(),
        coverArtUrl = coverArtUrl(coverArtId),
    )

fun Track.toNowPlayingItemUi(
    id: String,
    coverArtUrl: String?,
    meta: String = durationSeconds?.durationLabel().orEmpty(),
): NaviampNowPlayingItemUi =
    NaviampNowPlayingItemUi(
        id = id,
        title = title,
        subtitle = artistName,
        meta = meta,
        coverArtUrl = coverArtUrl,
    )

fun Track.compactFavoriteRatingLabel(): String? {
    val parts = listOfNotNull(
        favoritedAtIso8601?.let { "♥" },
        userRating?.takeIf { it in 1..5 }?.let { "$it★" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

fun Track.nowPlayingAlbumLine(): String =
    albumTitle?.let { title ->
        albumReleaseYear?.let { "$title ($it)" } ?: title
    }.orEmpty()

fun Track.nowPlayingAudioInfoLabel(playbackEngineName: String? = null): String =
    if (playbackEngineName == "JLayer") {
        "MP3  192 kbps"
    } else {
        audioInfo?.nowPlayingLabel().orEmpty()
    }

fun Track.toNowPlayingDetailSections(
    embeddedTags: List<Pair<String, String>>? = null,
): List<NaviampDetailSectionUi> =
    buildList {
        add(
            NaviampDetailSectionUi(
                title = "Song",
                rows = listOfNotNull(
                    "Title" to title,
                    "Artist" to artistName,
                    "Album" to albumTitle.orUnknown(),
                    albumReleaseYear?.let { "Year" to it.toString() },
                    durationSeconds?.let { "Duration" to it.durationLabel() },
                ),
            ),
        )
        if (embeddedTags != null) {
            add(
                NaviampDetailSectionUi(
                    title = "Embedded tags",
                    rows = when {
                        embeddedTags.isEmpty() -> listOf("Status" to "No readable ID3/Vorbis tags found")
                        else -> embeddedTags
                    },
                ),
            )
        }
        add(
            NaviampDetailSectionUi(
                title = "File",
                rows = listOfNotNull(
                    "Codec" to audioInfo?.codec.orUnknown(),
                    audioInfo?.bitrateKbps?.let { "Bitrate" to "$it kbps" },
                    audioInfo?.samplingRateHz?.let { "Sample rate" to "$it Hz" },
                    audioInfo?.bitDepth?.let { "Bit depth" to "$it bit" },
                    "Content type" to audioInfo?.contentType.orUnknown(),
                ),
            ),
        )
        add(
            NaviampDetailSectionUi(
                title = "Library",
                rows = listOfNotNull(
                    "Track ID" to id.value,
                    artistId?.let { "Artist ID" to it.value },
                    albumId?.let { "Album ID" to it.value },
                    "Favorite" to if (favoritedAtIso8601 != null) "Yes" else "No",
                    userRating?.let { "Rating" to it.ratingLabel() },
                ),
            ),
        )
        replayGain?.let { replayGain ->
            add(
                NaviampDetailSectionUi(
                    title = "Replay gain",
                    rows = listOfNotNull(
                        replayGain.trackGainDb?.let { "Track gain" to "${it.twoDecimalLabel()} dB" },
                        replayGain.albumGainDb?.let { "Album gain" to "${it.twoDecimalLabel()} dB" },
                        replayGain.trackPeak?.let { "Track peak" to it.sixDecimalLabel() },
                        replayGain.albumPeak?.let { "Album peak" to it.sixDecimalLabel() },
                    ),
                ),
            )
        }
    }.filter { it.rows.isNotEmpty() }

data class NowPlayingTrackUiConfig(
    val stateLabel: String,
    val coverArtUrl: String?,
    val playbackEngineName: String? = null,
    val waveform: AudioWaveform? = null,
    val positionSeconds: Double? = null,
    val durationSeconds: Double? = null,
    val volumePercent: Int = 100,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val canPlayPause: Boolean = true,
    val canSeek: Boolean = true,
    val canChangeVolume: Boolean = true,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val shuffleActive: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val canRepeat: Boolean = false,
    val canStartRadio: Boolean = false,
    val canAddToPlaylist: Boolean = false,
    val canFavorite: Boolean = false,
    val canRate: Boolean = false,
    val lyricsAvailable: Boolean = false,
    val lyricsVisible: Boolean = false,
    val lyricsStatus: String? = null,
    val lyrics: Lyrics? = null,
    val menuEnabled: Boolean = false,
    val embeddedTags: List<Pair<String, String>>? = null,
    val playlistChoices: List<NaviampPlaylistChoiceUi> = emptyList(),
    val useInlinePlaylistPicker: Boolean = true,
    val playlistActionStatus: String? = null,
    val backTo: List<NaviampNowPlayingItemUi> = emptyList(),
    val upNext: List<NaviampNowPlayingItemUi> = emptyList(),
    val related: List<NaviampNowPlayingItemUi> = emptyList(),
)

data class NowPlayingRadioUiConfig(
    val streamTitle: String? = null,
    val stateLabel: String,
    val volumePercent: Int = 100,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val canPlayPause: Boolean = true,
    val canChangeVolume: Boolean = false,
    val radioStations: List<NaviampNowPlayingItemUi> = emptyList(),
)

data class MiniNowPlayingUiConfig(
    val stateLabel: String,
    val coverArtUrl: String?,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val canPlayPause: Boolean = true,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)

fun Track.toNowPlayingUi(config: NowPlayingTrackUiConfig): NowPlayingUi =
    NowPlayingUi(
        id = id.value,
        title = title,
        subtitle = artistName,
        stateLabel = config.stateLabel,
        coverArtUrl = config.coverArtUrl,
        albumLine = nowPlayingAlbumLine(),
        audioInfo = nowPlayingAudioInfoLabel(config.playbackEngineName),
        waveform = config.waveform,
        positionSeconds = config.positionSeconds,
        durationSeconds = durationSeconds?.toDouble() ?: config.durationSeconds,
        volumePercent = config.volumePercent,
        isPlaying = config.isPlaying,
        isPaused = config.isPaused,
        canPlayPause = config.canPlayPause,
        canSeek = config.canSeek,
        canChangeVolume = config.canChangeVolume,
        hasPrevious = config.hasPrevious,
        hasNext = config.hasNext,
        shuffleEnabled = config.shuffleEnabled,
        shuffleActive = config.shuffleActive,
        repeatMode = config.repeatMode.toNaviampRepeatMode(),
        canRepeat = config.canRepeat,
        canStartRadio = config.canStartRadio,
        canAddToPlaylist = config.canAddToPlaylist,
        favoriteActive = favoritedAtIso8601 != null,
        canFavorite = config.canFavorite,
        userRating = userRating,
        canRate = config.canRate,
        lyricsAvailable = config.lyricsAvailable,
        lyricsVisible = config.lyricsVisible,
        lyricsStatus = config.lyrics?.takeIf { it.lines.isNotEmpty() }?.let { null } ?: config.lyricsStatus,
        lyricsOffsetMillis = config.lyrics?.offsetMillis ?: 0,
        lyricsLines = config.lyrics?.lines.orEmpty().map { line ->
            NaviampLyricLineUi(startMillis = line.startMillis, text = line.text)
        },
        menuEnabled = config.menuEnabled,
        detailSections = toNowPlayingDetailSections(config.embeddedTags),
        playlistChoices = config.playlistChoices,
        useInlinePlaylistPicker = config.useInlinePlaylistPicker,
        playlistActionStatus = config.playlistActionStatus,
        backTo = config.backTo,
        upNext = config.upNext,
        related = config.related,
    )

fun InternetRadioStation.toNowPlayingUi(config: NowPlayingRadioUiConfig): NowPlayingUi {
    val streamTitle = config.streamTitle?.takeIf { it.isNotBlank() }
    return NowPlayingUi(
        id = id,
        title = streamTitle ?: name,
        subtitle = if (streamTitle == null) "Internet radio" else name,
        stateLabel = config.stateLabel,
        isLive = true,
        volumePercent = config.volumePercent,
        isPlaying = config.isPlaying,
        isPaused = config.isPaused,
        canPlayPause = config.canPlayPause,
        canSeek = false,
        canChangeVolume = config.canChangeVolume,
        hasPrevious = false,
        hasNext = false,
        canRepeat = false,
        canStartRadio = false,
        canAddToPlaylist = false,
        menuEnabled = false,
        radioStations = config.radioStations,
    )
}

fun Track?.toMiniNowPlayingUi(config: MiniNowPlayingUiConfig): NowPlayingUi =
    NowPlayingUi(
        id = this?.id?.value.orEmpty(),
        title = this?.title ?: "Queue is empty",
        subtitle = this?.artistName ?: "Nothing Playing",
        stateLabel = config.stateLabel,
        coverArtUrl = config.coverArtUrl,
        isPlaying = config.isPlaying,
        isPaused = config.isPaused,
        canPlayPause = config.canPlayPause,
        hasPrevious = config.hasPrevious,
        hasNext = config.hasNext,
    )

fun Playlist.toPlaylistChoiceUi(): NaviampPlaylistChoiceUi =
    NaviampPlaylistChoiceUi(
        id = id,
        name = name,
        subtitle = "$trackCount tracks",
    )

fun Playlist.toSharedPlaylistDetailUi(
    tracks: List<Track>,
    coverArtUrl: (String?) -> String?,
): SharedPlaylistDetailUi =
    SharedPlaylistDetailUi(
        playlist = toSharedMediaItemUi(coverArtUrl, tracks),
        tracks = tracks.map { it.toAndroidTrackRowUi(coverArtUrl) },
    )

fun AlbumDetails.toSharedAlbumDetailUi(coverArtUrl: (String?) -> String?): SharedAlbumDetailUi =
    SharedAlbumDetailUi(
        album = album.toSharedMediaItemUi(coverArtUrl),
        tracks = tracks.map { it.toAndroidTrackRowUi(coverArtUrl) },
        totalDurationLabel = tracks.totalDurationLabel(),
    )

fun ArtistDetails.toSharedArtistDetailUi(coverArtUrl: (String?) -> String?): SharedArtistDetailUi =
    SharedArtistDetailUi(
        artist = artist.toSharedMediaItemUi(),
        albums = albums.map { it.toSharedMediaItemUi(coverArtUrl) },
    )

fun MediaSearchResults.toSharedSearchResultsUi(coverArtUrl: (String?) -> String?): SharedSearchResultsUi =
    SharedSearchResultsUi(
        artists = artists.map { it.toSharedMediaItemUi() },
        albums = albums.map { it.toSharedMediaItemUi(coverArtUrl) },
        tracks = tracks.map { it.toAndroidTrackRowUi(coverArtUrl) },
    )

fun InternetRadioStation.toNowPlayingStationUi(): NaviampNowPlayingItemUi =
    NaviampNowPlayingItemUi(
        id = id,
        title = name,
        subtitle = homePageUrl ?: "Internet radio",
    )

fun RepeatMode.toNaviampRepeatMode(): NaviampRepeatMode =
    when (this) {
        RepeatMode.Off -> NaviampRepeatMode.Off
        RepeatMode.Queue -> NaviampRepeatMode.Queue
        RepeatMode.Track -> NaviampRepeatMode.Track
    }

private fun app.naviamp.domain.AudioInfo.nowPlayingLabel(): String =
    buildList {
        val normalizedCodec = codec?.takeIf { it.isNotBlank() }?.uppercase()
        val sampleRate = samplingRateHz
        val depth = bitDepth
        val bitrate = bitrateKbps
        normalizedCodec?.let(::add)
        when {
            normalizedCodec in LosslessCodecs && sampleRate != null && depth != null ->
                add("${sampleRate.sampleRateKhzLabel()} / $depth")
            normalizedCodec in LosslessCodecs && bitrate != null -> add("$bitrate kbps")
            bitrate != null -> add("$bitrate kbps")
        }
    }.joinToString("  ")

private val LosslessCodecs = setOf("FLAC", "ALAC", "WAV", "AIFF", "AIF", "APE", "DSF", "DFF")

private fun Int.sampleRateKhzLabel(): String {
    val khz = this / 1000.0
    return if (this % 1000 == 0) {
        khz.toInt().toString()
    } else {
        khz.toString()
    }
}

private fun String?.orUnknown(): String =
    this?.takeIf { it.isNotBlank() } ?: "Unknown"
