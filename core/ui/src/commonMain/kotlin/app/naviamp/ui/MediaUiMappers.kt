package app.naviamp.ui

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.homeStations
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.SleepTimerState
import app.naviamp.domain.playback.sleepTimerDisplayLabel
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.waveform.AudioWaveform
import kotlin.math.absoluteValue

fun Artist.toSharedMediaItemUi(
    coverArtUrl: ((String?) -> String?)? = null,
    canFavorite: Boolean = false,
): SharedMediaItemUi =
    SharedMediaItemUi(
        id = id.value,
        title = name,
        subtitle = "Artist",
        coverArtUrl = coverArtUrl?.invoke(id.value),
        favoriteActive = favoritedAtIso8601 != null,
        canFavorite = canFavorite,
    )

fun Album.toSharedMediaItemUi(
    coverArtUrl: (String?) -> String?,
    canFavorite: Boolean = false,
): SharedMediaItemUi =
    SharedMediaItemUi(
        id = id.value,
        title = title,
        subtitle = artistName,
        meta = releaseYear?.toString().orEmpty(),
        coverArtUrl = coverArtUrl(coverArtId ?: id.value),
        favoriteActive = favoritedAtIso8601 != null,
        canFavorite = canFavorite,
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
        isSmartPlaylist = isSmart,
    )

fun InternetRadioStation.toSharedMediaItemUi(): SharedMediaItemUi =
    SharedMediaItemUi(
        id = id,
        title = name,
        subtitle = homePageUrl ?: "Internet radio",
        coverArtUrl = radioStationArtworkUrl(this),
    )

fun Genre.toSharedGenreMixItemUi(): SharedGenreMixItemUi =
    SharedGenreMixItemUi(
        id = name,
        title = name,
        subtitle = listOfNotNull(
            albumCount?.let { "$it albums" },
            trackCount?.let { "$it tracks" },
        ).joinToString(" - "),
    )

fun InternetRadioStation.defaultRadioArtworkUrl(): String =
    radioStationArtworkUrl(this)

fun HomeContent.toSharedHomeUi(
    coverArtUrl: (String?) -> String?,
    playlistTracksById: Map<String, List<Track>> = emptyMap(),
    canFavoriteAlbums: Boolean = false,
): SharedHomeUi =
    SharedHomeUi(
        mixBuilders = sharedMixBuilders(),
        recentlyAddedAlbums = recentlyAddedAlbums.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbums) },
        mixAlbums = mixAlbums.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbums) },
        recentAlbums = recentAlbums.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbums) },
        frequentAlbums = frequentAlbums.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbums) },
        randomAlbums = randomAlbums.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbums) },
        playlists = playlists.map { playlist ->
            playlist.toSharedMediaItemUi(
                coverArtUrl = coverArtUrl,
                tracks = playlistTracksById[playlist.id].orEmpty(),
            )
        },
        recentRadioStreams = recentRadioStreams.map {
            val coverArtUrls = it.coverArtIds.mapNotNull(coverArtUrl).distinct().take(4)
            SharedMediaItemUi(
                id = it.id,
                title = it.label,
                subtitle = "Radio",
                coverArtUrl = coverArtUrls.firstOrNull(),
                coverArtUrls = coverArtUrls,
            )
        },
        radioStations = recentInternetRadioStations.map { it.toSharedMediaItemUi() },
        stations = homeStations(this).map {
            SharedHomeStationUi(id = it.id, title = it.title, subtitle = it.subtitle)
        },
        genreSpotlightTitle = genreSpotlight?.name,
        genreSpotlightAlbums = genreSpotlightAlbums.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbums) },
        decadeLabel = decadeLabel,
        decadeAlbums = decadeAlbums.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbums) },
    )

fun sharedMixBuilders(): List<SharedMixBuilderUi> =
    listOf(
        SharedMixBuilderUi("artist", "Artist Mix", "Build a station from selected artists"),
        SharedMixBuilderUi("album", "Album Mix", "Build a station from selected albums"),
        SharedMixBuilderUi("genre", "Genre Mix", "Start a station from a genre"),
    )

fun Track.toSharedTrackRowUi(
    coverArtUrl: (String?) -> String?,
    fallbackCoverArtId: String? = null,
    popular: Boolean = false,
): SharedTrackRowUi =
    SharedTrackRowUi(
        id = id.value,
        title = title,
        subtitle = listOfNotNull(artistName, albumTitle).joinToString(" - "),
        coverArtUrl = coverArtUrl(coverArtId ?: fallbackCoverArtId),
        meta = durationSeconds?.durationLabel().orEmpty(),
        popular = popular,
        detailSections = toNowPlayingDetailSections(),
    )

fun Track.toDownloadedTrackUi(
    id: String,
    sizeBytes: Long,
    coverArtUrl: (String?) -> String?,
): NaviampDownloadedTrackUi =
    NaviampDownloadedTrackUi(
        id = id,
        track = toSharedTrackRowUi(coverArtUrl),
        sizeBytes = sizeBytes,
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
    audioInfo?.nowPlayingLabel().orEmpty()

fun Track.toNowPlayingDetailSections(
    embeddedTags: List<Pair<String, String>>? = null,
    streamQuality: StreamQuality? = null,
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
        streamQuality?.let { quality ->
            add(
                NaviampDetailSectionUi(
                    title = "Stream",
                    rows = listOf(
                        "Transcoded" to if (quality is StreamQuality.Transcoded) "Yes" else "No",
                        "Quality" to quality.label(),
                    ),
                ),
            )
        }
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
                    bpm?.let { "BPM" to it.toString() },
                    moods.takeIf { it.isNotEmpty() }?.let { "Mood" to it.joinToString(", ") },
                    playCount?.let { "Play count" to it.toString() },
                    lastPlayedAtIso8601?.let { "Last played" to it },
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
    val visualizerFrame: PlaybackVisualizerFrame? = null,
    val visualizerAvailable: Boolean = false,
    val visualizerVisible: Boolean = false,
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
    val canSaveQueueAsPlaylist: Boolean = false,
    val sleepTimer: NaviampSleepTimerUi = NaviampSleepTimerUi(),
    val canFavorite: Boolean = false,
    val canRate: Boolean = false,
    val lyricsAvailable: Boolean = false,
    val lyricsVisible: Boolean = false,
    val lyricsStatus: String? = null,
    val lyrics: Lyrics? = null,
    val menuEnabled: Boolean = false,
    val embeddedTags: List<Pair<String, String>>? = null,
    val streamQuality: StreamQuality? = null,
    val playlistChoices: List<NaviampPlaylistChoiceUi> = emptyList(),
    val useInlinePlaylistPicker: Boolean = true,
    val playlistActionStatus: String? = null,
    val backTo: List<NaviampNowPlayingItemUi> = emptyList(),
    val upNext: List<NaviampNowPlayingItemUi> = emptyList(),
    val related: List<NaviampNowPlayingItemUi> = emptyList(),
    val relatedTabLabel: String = "RELATED",
    val relatedEmptyLabel: String = "Related tracks are not loaded.",
)

data class NowPlayingRadioUiConfig(
    val streamTitle: String? = null,
    val coverArtUrl: String?,
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
        visualizerFrame = config.visualizerFrame,
        visualizerAvailable = config.visualizerAvailable,
        visualizerVisible = config.visualizerVisible,
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
        canSaveQueueAsPlaylist = config.canSaveQueueAsPlaylist,
        sleepTimer = config.sleepTimer,
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
        detailSections = toNowPlayingDetailSections(
            embeddedTags = config.embeddedTags,
            streamQuality = config.streamQuality,
        ),
        playlistChoices = config.playlistChoices,
        useInlinePlaylistPicker = config.useInlinePlaylistPicker,
        playlistActionStatus = config.playlistActionStatus,
        backTo = config.backTo,
        upNext = config.upNext,
        related = config.related,
        relatedTabLabel = config.relatedTabLabel,
        relatedEmptyLabel = config.relatedEmptyLabel,
    )

fun InternetRadioStation.toNowPlayingUi(config: NowPlayingRadioUiConfig): NowPlayingUi {
    val streamTitle = config.streamTitle?.takeIf { it.isNotBlank() }
    return NowPlayingUi(
        id = id,
        title = streamTitle ?: name,
        subtitle = if (streamTitle == null) "Internet radio" else name,
        stateLabel = config.stateLabel,
        coverArtUrl = config.coverArtUrl,
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

fun SleepTimerState?.toNaviampSleepTimerUi(nowEpochMillis: Long): NaviampSleepTimerUi =
    this?.let { timer ->
        NaviampSleepTimerUi(active = true, label = sleepTimerDisplayLabel(timer, nowEpochMillis))
    } ?: NaviampSleepTimerUi()

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
        tracks = tracks.map { it.toSharedTrackRowUi(coverArtUrl) },
    )

fun AlbumDetails.toSharedAlbumDetailUi(
    coverArtUrl: (String?) -> String?,
    popularTrackIds: Set<String> = emptySet(),
    canFavoriteAlbum: Boolean = false,
): SharedAlbumDetailUi =
    SharedAlbumDetailUi(
        album = album.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbum),
        tracks = tracks.map {
            it.toSharedTrackRowUi(
                coverArtUrl,
                fallbackCoverArtId = album.coverArtId ?: album.id.value,
                popular = it.id.value in popularTrackIds,
            )
        },
        totalDurationLabel = tracks.totalDurationLabel(),
    )

fun ArtistDetails.toSharedArtistDetailUi(
    coverArtUrl: (String?) -> String?,
    popularTracks: List<Track> = emptyList(),
    popularTracksStatus: String? = null,
    similarArtists: List<SimilarArtistMatch> = emptyList(),
    similarArtistsStatus: String? = null,
    canFavoriteArtist: Boolean = false,
    canFavoriteAlbums: Boolean = false,
): SharedArtistDetailUi =
    SharedArtistDetailUi(
        artist = artist.toSharedMediaItemUi(coverArtUrl, canFavoriteArtist).copy(
            coverArtUrl = info?.largeImageUrl
                ?: info?.mediumImageUrl
                ?: info?.smallImageUrl
                ?: coverArtUrl(artist.id.value),
        ),
        albums = albums.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbums) },
        biography = info?.biography,
        popularTracks = popularTracks.map { it.toSharedTrackRowUi(coverArtUrl) },
        popularTracksStatus = popularTracksStatus,
        similarArtists = similarArtists.map { it.toSharedSimilarArtistUi() },
        similarArtistsStatus = similarArtistsStatus,
    )

fun SimilarArtistMatch.toSharedSimilarArtistUi(): SharedSimilarArtistUi =
    SharedSimilarArtistUi(
        id = candidate.sourceArtistId,
        title = candidate.name,
        subtitle = if (matchedArtist != null) "In library" else "View in browser",
        imageUrl = candidate.imageUrl,
        localArtistId = matchedArtist?.id?.value,
        externalUrl = candidate.externalUrl,
    )

fun MediaSearchResults.toSharedSearchResultsUi(
    coverArtUrl: (String?) -> String?,
    canFavoriteArtists: Boolean = false,
    canFavoriteAlbums: Boolean = false,
): SharedSearchResultsUi =
    SharedSearchResultsUi(
        artists = artists.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteArtists) },
        albums = albums.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbums) },
        tracks = tracks.map { it.toSharedTrackRowUi(coverArtUrl) },
    )

fun InternetRadioStation.toNowPlayingStationUi(): NaviampNowPlayingItemUi =
    NaviampNowPlayingItemUi(
        id = id,
        title = name,
        subtitle = homePageUrl ?: "Internet radio",
        coverArtUrl = radioStationArtworkUrl(this),
    )

fun radioArtworkUrl(
    station: InternetRadioStation,
    streamMetadataProperties: Map<String, String> = emptyMap(),
    trackArtworkUrl: String? = null,
): String =
    streamMetadataArtworkUrl(streamMetadataProperties)
        ?: trackArtworkUrl
        ?: radioStationArtworkUrl(station)

fun effectiveNowPlayingCoverArtUrl(
    currentCoverArtUrl: String?,
    nowPlayingTrack: Track?,
    nowPlayingStation: InternetRadioStation?,
    streamMetadata: PlaybackStreamMetadata,
    radioTrackArtworkByKey: Map<String, String?>,
): String? {
    val station = nowPlayingStation ?: return currentCoverArtUrl
    if (nowPlayingTrack?.isInternetRadioTrack() != true && nowPlayingTrack != null) {
        return currentCoverArtUrl
    }
    val trackArtworkUrl = radioTrackArtworkKey(station, streamMetadata.title)
        ?.let(radioTrackArtworkByKey::get)
    return radioArtworkUrl(
        station = station,
        streamMetadataProperties = streamMetadata.properties,
        trackArtworkUrl = trackArtworkUrl,
    )
}

fun radioArtworkNeedsTrackLookup(
    station: InternetRadioStation,
    streamTitle: String?,
    streamMetadataProperties: Map<String, String> = emptyMap(),
): Boolean =
    streamMetadataArtworkUrl(streamMetadataProperties) == null &&
        knownRadioStationArtworkUrl(station) == null &&
        station.homePageUrl == null &&
        radioTrackArtworkQuery(streamTitle) != null

fun radioTrackArtworkKey(
    station: InternetRadioStation,
    streamTitle: String?,
): String? =
    radioTrackArtworkQuery(streamTitle)?.let { query -> "${station.id}:$query" }

fun radioTrackArtworkQuery(streamTitle: String?): String? =
    streamTitle
        ?.trim()
        ?.takeIf { it.length >= 3 }

private fun streamMetadataArtworkUrl(properties: Map<String, String>): String? {
    val artworkKeys = setOf(
        "artwork",
        "artworkurl",
        "cover",
        "coverart",
        "coverarturl",
        "image",
        "imageurl",
        "logo",
        "logourl",
        "picture",
        "pictureurl",
    )
    return properties.entries.firstNotNullOfOrNull { (key, value) ->
        value.trim()
            .takeIf { key.lowercase().filter(Char::isLetterOrDigit) in artworkKeys }
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
}

private fun radioStationArtworkUrl(station: InternetRadioStation): String =
    knownRadioStationArtworkUrl(station)
        ?: station.homePageUrl?.let(::faviconUrl)
        ?: generatedRadioTileUrl(station.name)

private fun knownRadioStationArtworkUrl(station: InternetRadioStation): String? {
    val haystack = listOf(station.name, station.streamUrl, station.homePageUrl.orEmpty())
        .joinToString(" ")
        .lowercase()
    return when {
        "somafm" in haystack || "soma.fm" in haystack -> {
            val slug = somaFmStationSlug(station.name)
            "https://somafm.com/img3/$slug-400.png"
        }
        else -> null
    }
}

private fun somaFmStationSlug(name: String): String =
    when (name.trim().lowercase()) {
        "deep space one" -> "deepspaceone"
        "drone zone" -> "dronezone"
        "groove salad" -> "groovesalad"
        "groove salad classic" -> "gsclassic"
        "idm tranceponder" -> "idm"
        "space station soma" -> "spacestation"
        "synphaera" -> "synphaera"
        "the trip" -> "thetrip"
        else -> name.lowercase().filter(Char::isLetterOrDigit).ifBlank { "somafm" }
    }

private fun faviconUrl(url: String): String? {
    val host = url.removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("/")
        .substringBefore(":")
        .takeIf { it.isNotBlank() }
        ?: return null
    return "https://www.google.com/s2/favicons?domain=$host&sz=256"
}

private fun generatedRadioTileUrl(name: String): String {
    val initials = name
        .split(Regex("\\s+"))
        .mapNotNull { word -> word.firstOrNull(Char::isLetterOrDigit)?.uppercaseChar()?.toString() }
        .take(3)
        .joinToString("")
        .ifBlank { "RAD" }
    val palette = RadioTilePalettes[name.hashCode().absoluteValue % RadioTilePalettes.size]
    return "naviamp-radio-tile://tile?label=${initials.urlEncode()}&from=${palette.first}&to=${palette.second}"
}

private val RadioTilePalettes = listOf(
    "6f2a37" to "241013",
    "465d7a" to "161f2c",
    "85653d" to "24180e",
    "4d6f62" to "12211d",
    "6b4d84" to "211629",
)

private fun String.urlEncode(): String =
    buildString {
        for (char in this@urlEncode) {
            when {
                char.isLetterOrDigit() -> append(char)
                char == '-' || char == '_' || char == '.' -> append(char)
                else -> append("%${char.code.toString(16).uppercase().padStart(2, '0')}")
            }
        }
    }

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
