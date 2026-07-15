package app.naviamp.ui

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumExplicitStatus
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.audio.AudioTag
import app.naviamp.domain.audio.replayGainFromAudioTags
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.homeStations
import app.naviamp.domain.media.RelatedTracksSource
import app.naviamp.domain.media.groupedByReleaseSection
import app.naviamp.domain.settings.AlbumSortOrder
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackReplayGain
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.SleepTimerRequest
import app.naviamp.domain.playback.label
import app.naviamp.domain.playback.playbackReplayGainAdjustment
import app.naviamp.domain.playback.SleepTimerState
import app.naviamp.domain.playback.sleepTimerDisplayLabel
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.sonichome.SonicHomeDiscoveryRows
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
        meta = listOfNotNull(
            releaseYear?.toString(),
            "Explicit".takeIf { explicitStatus == AlbumExplicitStatus.Explicit },
        ).joinToString(" "),
        releaseYear = releaseYear,
        coverArtUrl = coverArtUrl(coverArtId ?: id.value),
        favoriteActive = favoritedAtIso8601 != null,
        canFavorite = canFavorite,
    )

fun List<SharedMediaItemUi>.sortedForAlbumDisplay(order: AlbumSortOrder): List<SharedMediaItemUi> =
    when (order) {
        AlbumSortOrder.ReleaseYearAscending -> sortedWith(
            compareBy<SharedMediaItemUi> { it.releaseYear ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        AlbumSortOrder.ReleaseYearDescending -> sortedWith(
            compareByDescending<SharedMediaItemUi> { it.releaseYear ?: Int.MIN_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        AlbumSortOrder.Title -> sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER, SharedMediaItemUi::title)
                .thenBy { it.releaseYear ?: Int.MAX_VALUE },
        )
    }

fun Playlist.toSharedMediaItemUi(
    coverArtUrl: (String?) -> String?,
    tracks: List<Track> = emptyList(),
    keepDownloadedActive: Boolean = false,
): SharedMediaItemUi =
    SharedMediaItemUi(
        id = id,
        title = name,
        subtitle = "$trackCount tracks",
        meta = durationSeconds?.durationLabel().orEmpty(),
        coverArtUrl = coverArtUrl(coverArtId),
        coverArtUrls = tracks.mapNotNull { coverArtUrl(it.coverArtId) }.distinct().take(4),
        isSmartPlaylist = isSmart,
        keepDownloadedActive = keepDownloadedActive,
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
    keepDownloadedPlaylistIds: Set<String> = emptySet(),
    sonicDiscoveryRows: SonicHomeDiscoveryRows = SonicHomeDiscoveryRows(),
    canFavoriteAlbums: Boolean = false,
    showSonicPathBuilder: Boolean = false,
    showSonicMixBuilder: Boolean = false,
): SharedHomeUi =
    SharedHomeUi(
        mixBuilders = sharedMixBuilders(showSonicPathBuilder, showSonicMixBuilder),
        sonicDiscoveryRows = sonicDiscoveryRows.rows.map { row ->
            SharedHomeDiscoveryTrackRowUi(
                id = row.id.value,
                title = row.title,
                tracks = row.tracks.map { track -> track.toSharedTrackRowUi(coverArtUrl) },
            )
        },
        recentlyAddedAlbums = recentlyAddedAlbums.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbums) },
        mixAlbums = mixAlbums.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbums) },
        recentAlbums = recentAlbums.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbums) },
        frequentAlbums = frequentAlbums.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbums) },
        randomAlbums = randomAlbums.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbums) },
        playlists = playlists.map { playlist ->
            playlist.toSharedMediaItemUi(
                coverArtUrl = coverArtUrl,
                tracks = playlistTracksById[playlist.id].orEmpty(),
                keepDownloadedActive = playlist.id in keepDownloadedPlaylistIds,
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
        recentlyPlayedTracks = recentlyPlayedTracks.map { track ->
            track.toSharedTrackRowUi(coverArtUrl).copy(
                meta = listOfNotNull(
                    track.lastPlayedAtIso8601?.take(10)?.let { "Played $it" },
                    track.playCount?.let { count -> "$count plays" },
                ).joinToString(" - ").ifBlank { track.durationSeconds?.durationLabel().orEmpty() },
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

fun sharedMixBuilders(
    showSonicPathBuilder: Boolean = false,
    showSonicMixBuilder: Boolean = false,
): List<SharedMixBuilderUi> =
    listOfNotNull(
        SharedMixBuilderUi("artist", "Artist Mix", "Build a station from selected artists"),
        SharedMixBuilderUi("album", "Album Mix", "Build a station from selected albums"),
        SharedMixBuilderUi("genre", "Genre Mix", "Start a station from a genre"),
        SharedMixBuilderUi("sonic-path", "Sonic Path", "Find a path between two tracks").takeIf {
            showSonicPathBuilder
        },
        SharedMixBuilderUi("sonic-mix", "Sonic Mix", "Blend multiple seed tracks").takeIf {
            showSonicMixBuilder
        },
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
        favoriteActive = favoritedAtIso8601 != null,
        hasAlbum = albumId != null,
        hasArtist = artistId != null,
        detailSections = toNowPlayingDetailSections(),
    )

fun Track.toDownloadedTrackUi(
    id: String,
    sizeBytes: Long,
    qualityLabel: String = "",
    coverArtUrl: (String?) -> String?,
): NaviampDownloadedTrackUi =
    NaviampDownloadedTrackUi(
        id = id,
        track = toSharedTrackRowUi(coverArtUrl).copy(
            canToggleFavorite = false,
            hasAlbum = false,
            hasArtist = false,
        ),
        sizeBytes = sizeBytes,
        qualityLabel = qualityLabel,
    )

fun Track.toNowPlayingItemUi(coverArtUrl: (String?) -> String?): NaviampNowPlayingItemUi =
    NaviampNowPlayingItemUi(
        id = id.value,
        title = title,
        subtitle = listOfNotNull(artistName, albumTitle).joinToString(" - "),
        meta = durationSeconds?.durationLabel().orEmpty(),
        coverArtUrl = coverArtUrl(coverArtId),
        favoriteActive = favoritedAtIso8601 != null,
        hasAlbum = albumId != null,
        hasArtist = artistId != null,
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
        favoriteActive = favoritedAtIso8601 != null,
        hasAlbum = albumId != null,
        hasArtist = artistId != null,
    )

fun List<Track>.toNowPlayingItemUis(
    coverArtUrl: (Track) -> String?,
    id: (index: Int, track: Track) -> String = { _, track -> track.id.value },
    meta: (Track) -> String = { track -> track.durationSeconds?.durationLabel().orEmpty() },
): List<NaviampNowPlayingItemUi> =
    mapIndexed { index, track ->
        track.toNowPlayingItemUi(
            id = id(index, track),
            coverArtUrl = coverArtUrl(track),
            meta = meta(track),
        )
    }

enum class NowPlayingSectionItemIds {
    TrackIds,
    QueueAndRelatedIndexes,
}

data class NowPlayingSectionsUi(
    val backTo: List<NaviampNowPlayingItemUi>,
    val upNext: List<NaviampNowPlayingItemUi>,
    val related: List<NaviampNowPlayingItemUi>,
    val relatedLabels: NowPlayingRelatedUiLabels,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val shuffleEnabled: Boolean,
)

fun nowPlayingSectionsUi(
    tracks: List<Track>,
    currentTrack: Track?,
    playNextCount: Int = 0,
    relatedTracks: List<Track>,
    coverArtUrl: (Track) -> String?,
    sonicSimilarityEnabled: Boolean,
    relatedTracksSource: RelatedTracksSource = relatedTracksSourceForPreference(sonicSimilarityEnabled),
    relatedSimilarityByTrackId: Map<TrackId, Double> = emptyMap(),
    repeatMode: RepeatMode,
    itemIds: NowPlayingSectionItemIds = NowPlayingSectionItemIds.TrackIds,
): NowPlayingSectionsUi {
    val currentIndex = currentTrack?.let { track -> tracks.indexOfFirst { it.id == track.id } } ?: -1
    return nowPlayingSectionsUi(
        tracks = tracks,
        currentIndex = currentIndex,
        playNextCount = playNextCount,
        relatedTracks = relatedTracks,
        coverArtUrl = coverArtUrl,
        sonicSimilarityEnabled = sonicSimilarityEnabled,
        relatedTracksSource = relatedTracksSource,
        relatedSimilarityByTrackId = relatedSimilarityByTrackId,
        repeatMode = repeatMode,
        itemIds = itemIds,
    )
}

fun PlaybackQueue.toNowPlayingSectionsUi(
    relatedTracks: List<Track>,
    coverArtUrl: (Track) -> String?,
    sonicSimilarityEnabled: Boolean,
    relatedTracksSource: RelatedTracksSource = relatedTracksSourceForPreference(sonicSimilarityEnabled),
    relatedSimilarityByTrackId: Map<TrackId, Double> = emptyMap(),
    repeatMode: RepeatMode,
    itemIds: NowPlayingSectionItemIds = NowPlayingSectionItemIds.QueueAndRelatedIndexes,
): NowPlayingSectionsUi =
    nowPlayingSectionsUi(
        tracks = tracks,
        currentIndex = currentIndex,
        playNextCount = playNextCount,
        relatedTracks = relatedTracks,
        coverArtUrl = coverArtUrl,
        sonicSimilarityEnabled = sonicSimilarityEnabled,
        relatedTracksSource = relatedTracksSource,
        relatedSimilarityByTrackId = relatedSimilarityByTrackId,
        repeatMode = repeatMode,
        itemIds = itemIds,
    )

private fun nowPlayingSectionsUi(
    tracks: List<Track>,
    currentIndex: Int,
    playNextCount: Int,
    relatedTracks: List<Track>,
    coverArtUrl: (Track) -> String?,
    sonicSimilarityEnabled: Boolean,
    relatedTracksSource: RelatedTracksSource = relatedTracksSourceForPreference(sonicSimilarityEnabled),
    relatedSimilarityByTrackId: Map<TrackId, Double> = emptyMap(),
    repeatMode: RepeatMode,
    itemIds: NowPlayingSectionItemIds,
): NowPlayingSectionsUi {
    val hasCurrent = currentIndex in tracks.indices
    val firstBackToQueueIndex = currentIndex - 1
    val firstUpNextQueueIndex = currentIndex + 1
    val backTo = if (hasCurrent) tracks.take(currentIndex).asReversed() else emptyList()
    val upNext = if (hasCurrent) tracks.drop(currentIndex + 1) else emptyList()
    val queueItemId: (Int, Int, Track) -> String = { index, queueIndex, track ->
        when (itemIds) {
            NowPlayingSectionItemIds.TrackIds -> track.id.value
            NowPlayingSectionItemIds.QueueAndRelatedIndexes -> nowPlayingQueueItemId(queueIndex)
        }
    }
    val relatedItemId: (Int, Track) -> String = { index, track ->
        when (itemIds) {
            NowPlayingSectionItemIds.TrackIds -> track.id.value
            NowPlayingSectionItemIds.QueueAndRelatedIndexes -> nowPlayingRelatedItemId(index)
        }
    }

    return NowPlayingSectionsUi(
        backTo = backTo.toNowPlayingItemUis(
            coverArtUrl = coverArtUrl,
            id = { index, track -> queueItemId(index, firstBackToQueueIndex - index, track) },
            meta = { track -> track.durationSeconds?.durationLabel().orEmpty() },
        ),
        upNext = upNext.toNowPlayingItemUis(
            coverArtUrl = coverArtUrl,
            id = { index, track -> queueItemId(index, firstUpNextQueueIndex + index, track) },
            meta = { track -> track.durationSeconds?.durationLabel().orEmpty() },
        ).mapIndexed { index, item ->
            item.copy(playNextPriority = index < playNextCount.coerceIn(0, upNext.size))
        },
        related = relatedTracks.toNowPlayingItemUis(
            coverArtUrl = coverArtUrl,
            id = relatedItemId,
            meta = { track -> relatedTrackMeta(track, relatedTracksSource, relatedSimilarityByTrackId) },
        ),
        relatedLabels = nowPlayingRelatedUiLabels(relatedTracksSource),
        hasPrevious = currentIndex > 0 || (repeatMode == RepeatMode.Queue && tracks.size > 1),
        hasNext = (hasCurrent && currentIndex < tracks.lastIndex) ||
            (repeatMode == RepeatMode.Queue && tracks.size > 1),
        shuffleEnabled = upNext.size > 1,
    )
}

sealed interface NowPlayingItemTarget {
    data class QueueIndex(val index: Int) : NowPlayingItemTarget
    data class RelatedIndex(val index: Int) : NowPlayingItemTarget
    data class TrackId(val id: String) : NowPlayingItemTarget
}

enum class NowPlayingItemAction {
    StartRadio,
    PlayTrackRadioNext,
    AddTrackRadioToQueue,
    PlayNext,
    AddToQueue,
    AddToPlaylist,
    CreatePlaylistAndAdd,
    Download,
    GoToAlbum,
    GoToArtist,
    ToggleFavorite,
    RemoveFromQueue,
}

enum class NowPlayingItemSource {
    Queue,
    Related,
    TrackId,
}

enum class NowPlayingCurrentTrackAction {
    StartRadio,
    AddToPlaylist,
    CreatePlaylistAndAdd,
    Download,
    GoToAlbum,
    GoToArtist,
    ToggleFavorite,
    SetRating,
}

data class NowPlayingCurrentTrackActionRequest(
    val track: Track,
    val action: NowPlayingCurrentTrackAction,
    val playlistChoice: NaviampPlaylistChoiceUi? = null,
    val playlistName: String? = null,
    val rating: Int? = null,
)

data class NowPlayingCurrentTrackUiActionRequest(
    val action: NowPlayingCurrentTrackAction,
    val playlistChoice: NaviampPlaylistChoiceUi? = null,
    val playlistName: String? = null,
    val rating: Int? = null,
)

enum class NowPlayingPlaybackAction {
    Pause,
    Resume,
    PlayCurrent,
    Seek,
    Previous,
    Next,
    ToggleShuffle,
    CycleRepeatMode,
    ChangeVolume,
}

data class NowPlayingPlaybackActionRequest(
    val action: NowPlayingPlaybackAction,
    val seekSeconds: Double? = null,
    val volumePercent: Int? = null,
)

enum class NowPlayingDisplayAction {
    ToggleLyrics,
    ChangeLyricsOffset,
    ToggleVisualizer,
    SelectVisualizer,
    SelectRadioDj,
    Collapse,
}

data class NowPlayingDisplayActionRequest(
    val action: NowPlayingDisplayAction,
    val lyricsOffsetMillis: Int? = null,
    val visualizer: NaviampVisualizer? = null,
    val radioDjId: String? = null,
)

enum class NowPlayingQueueAction {
    SaveQueueAsPlaylist,
    MoveToNext,
    RemoveFromQueue,
    EmptyQueue,
}

data class NowPlayingQueueActionRequest(
    val action: NowPlayingQueueAction,
    val playlistName: String? = null,
    val queueIndex: Int? = null,
)

enum class NowPlayingSleepTimerAction {
    Select,
    Cancel,
}

data class NowPlayingSleepTimerActionRequest(
    val action: NowPlayingSleepTimerAction,
    val request: SleepTimerRequest? = null,
)

enum class NowPlayingSelectionAction {
    SelectQueueItem,
    SelectRelatedItem,
    SelectRadioStation,
}

data class NowPlayingSelectionActionRequest(
    val item: NaviampNowPlayingItemUi,
    val action: NowPlayingSelectionAction,
)

data class NowPlayingItemActionRequest(
    val item: NaviampNowPlayingItemUi,
    val target: NowPlayingItemTarget,
    val action: NowPlayingItemAction,
    val playlistChoice: NaviampPlaylistChoiceUi? = null,
    val playlistName: String? = null,
)

data class ResolvedNowPlayingItemAction(
    val request: NowPlayingItemActionRequest,
    val source: NowPlayingItemSource,
    val track: Track?,
) {
    val item: NaviampNowPlayingItemUi
        get() = request.item

    val action: NowPlayingItemAction
        get() = request.action

    val playlistChoice: NaviampPlaylistChoiceUi?
        get() = request.playlistChoice

    val playlistName: String?
        get() = request.playlistName

    val isRelated: Boolean
        get() = source == NowPlayingItemSource.Related
}

fun nowPlayingQueueItemId(index: Int): String = "queue:$index"

fun nowPlayingRelatedItemId(index: Int): String = "related:$index"

fun nowPlayingListItemKey(index: Int, item: NaviampNowPlayingItemUi): String =
    "$index:${item.id}"

fun nowPlayingItemTarget(item: NaviampNowPlayingItemUi): NowPlayingItemTarget =
    item.id.removePrefix("queue:")
        .takeIf { it != item.id }
        ?.toIntOrNull()
        ?.let(NowPlayingItemTarget::QueueIndex)
        ?: item.id.removePrefix("related:")
            .takeIf { it != item.id }
            ?.toIntOrNull()
            ?.let(NowPlayingItemTarget::RelatedIndex)
        ?: NowPlayingItemTarget.TrackId(item.id)

fun nowPlayingQueueIndex(item: NaviampNowPlayingItemUi): Int? =
    (nowPlayingItemTarget(item) as? NowPlayingItemTarget.QueueIndex)?.index

fun nowPlayingRelatedIndex(item: NaviampNowPlayingItemUi): Int? =
    (nowPlayingItemTarget(item) as? NowPlayingItemTarget.RelatedIndex)?.index

fun nowPlayingItemActionRequest(
    item: NaviampNowPlayingItemUi,
    action: NowPlayingItemAction,
    playlistChoice: NaviampPlaylistChoiceUi? = null,
    playlistName: String? = null,
): NowPlayingItemActionRequest =
    NowPlayingItemActionRequest(
        item = item,
        target = nowPlayingItemTarget(item),
        action = action,
        playlistChoice = playlistChoice,
        playlistName = playlistName,
    )

fun resolveNowPlayingItemTrack(
    item: NaviampNowPlayingItemUi,
    queueTracks: List<Track> = emptyList(),
    relatedTracks: List<Track> = emptyList(),
    knownTracks: List<Track> = emptyList(),
): Track? =
    resolveNowPlayingTargetTrack(nowPlayingItemTarget(item), queueTracks, relatedTracks, knownTracks)

fun NowPlayingItemActionRequest.resolveTrack(
    queueTracks: List<Track> = emptyList(),
    relatedTracks: List<Track> = emptyList(),
    knownTracks: List<Track> = emptyList(),
): Track? =
    resolveNowPlayingTargetTrack(target, queueTracks, relatedTracks, knownTracks)

fun NowPlayingItemActionRequest.resolveAction(
    queueTracks: List<Track> = emptyList(),
    relatedTracks: List<Track> = emptyList(),
    knownTracks: List<Track> = emptyList(),
    fallbackTrack: Track? = null,
): ResolvedNowPlayingItemAction =
    ResolvedNowPlayingItemAction(
        request = this,
        source = target.source,
        track = resolveTrack(queueTracks, relatedTracks, knownTracks) ?: fallbackTrack,
    )

private val NowPlayingItemTarget.source: NowPlayingItemSource
    get() = when (this) {
        is NowPlayingItemTarget.QueueIndex -> NowPlayingItemSource.Queue
        is NowPlayingItemTarget.RelatedIndex -> NowPlayingItemSource.Related
        is NowPlayingItemTarget.TrackId -> NowPlayingItemSource.TrackId
    }

data class ResolvedSharedTrackRowAction(
    val request: SharedTrackRowActionRequest,
    val track: Track?,
) {
    val action: SharedTrackRowAction
        get() = request.action

    val row: SharedTrackRowUi
        get() = request.track

    val playlistChoice: NaviampPlaylistChoiceUi?
        get() = request.playlistChoice

    val playlistName: String?
        get() = request.playlistName
}

fun SharedTrackRowActionRequest.resolveAction(
    knownTracks: List<Track> = emptyList(),
    fallbackTrack: Track? = null,
): ResolvedSharedTrackRowAction =
    ResolvedSharedTrackRowAction(
        request = this,
        track = knownTracks.firstOrNull { track -> track.id.value == this.track.id } ?: fallbackTrack,
    )

private fun resolveNowPlayingTargetTrack(
    target: NowPlayingItemTarget,
    queueTracks: List<Track>,
    relatedTracks: List<Track>,
    knownTracks: List<Track>,
): Track? =
    when (target) {
        is NowPlayingItemTarget.QueueIndex -> queueTracks.getOrNull(target.index)
        is NowPlayingItemTarget.RelatedIndex -> relatedTracks.getOrNull(target.index)
        is NowPlayingItemTarget.TrackId ->
            (knownTracks + queueTracks + relatedTracks).firstOrNull { track -> track.id.value == target.id }
    }

data class NowPlayingRelatedUiLabels(
    val tabLabel: String,
    val emptyLabel: String,
)

fun nowPlayingRelatedUiLabels(sonicSimilarityEnabled: Boolean): NowPlayingRelatedUiLabels =
    nowPlayingRelatedUiLabels(relatedTracksSourceForPreference(sonicSimilarityEnabled))

fun nowPlayingRelatedUiLabels(source: RelatedTracksSource): NowPlayingRelatedUiLabels =
    when (source) {
        RelatedTracksSource.SonicSimilarity -> NowPlayingRelatedUiLabels(
            tabLabel = "SONIC",
            emptyLabel = "Sonic matches are not loaded.",
        )
        RelatedTracksSource.None,
        RelatedTracksSource.LocalLibrary,
        RelatedTracksSource.ProviderRadio,
        -> NowPlayingRelatedUiLabels(
            tabLabel = "RELATED",
            emptyLabel = "Related tracks are not loaded.",
        )
    }

private fun relatedTracksSourceForPreference(sonicSimilarityEnabled: Boolean): RelatedTracksSource =
    if (sonicSimilarityEnabled) RelatedTracksSource.SonicSimilarity else RelatedTracksSource.LocalLibrary

private fun relatedTrackMeta(
    track: Track,
    source: RelatedTracksSource,
    similarityByTrackId: Map<TrackId, Double>,
): String =
    if (source == RelatedTracksSource.SonicSimilarity) {
        similarityByTrackId[track.id]?.let { similarity ->
            "${(similarity.coerceIn(0.0, 1.0) * 100).toInt()}% match"
        }.orEmpty()
    } else {
        ""
    }

data class NowPlayingTrackCapabilities(
    val canPlayPause: Boolean,
    val canSeek: Boolean,
    val canChangeVolume: Boolean,
    val canRepeat: Boolean,
    val canStartRadio: Boolean,
    val canAddToPlaylist: Boolean,
    val canSaveQueueAsPlaylist: Boolean,
    val canFavorite: Boolean,
    val canRate: Boolean,
    val lyricsAvailable: Boolean,
)

fun nowPlayingTrackCapabilities(
    isLiveStream: Boolean,
    playbackState: PlaybackState,
    hasPlaybackTarget: Boolean = true,
    supportsPause: Boolean = true,
    supportsSeek: Boolean = true,
    supportsSoftwareVolume: Boolean = false,
    supportsTrackRadio: Boolean = false,
    supportsTrackFavorites: Boolean = false,
    supportsTrackRatings: Boolean = false,
    canRepeatQueue: Boolean = false,
    canSaveQueueAsPlaylist: Boolean = false,
    canAddToPlaylist: Boolean = true,
): NowPlayingTrackCapabilities =
    NowPlayingTrackCapabilities(
        canPlayPause = hasPlaybackTarget &&
            playbackState != PlaybackState.Loading &&
            playbackState !is PlaybackState.Error &&
            (supportsPause || playbackState != PlaybackState.Playing),
        canSeek = supportsSeek && !isLiveStream,
        canChangeVolume = supportsSoftwareVolume,
        canRepeat = canRepeatQueue && !isLiveStream,
        canStartRadio = supportsTrackRadio && !isLiveStream,
        canAddToPlaylist = canAddToPlaylist && !isLiveStream,
        canSaveQueueAsPlaylist = canSaveQueueAsPlaylist && !isLiveStream,
        canFavorite = supportsTrackFavorites && !isLiveStream,
        canRate = supportsTrackRatings && !isLiveStream,
        lyricsAvailable = !isLiveStream,
    )

fun nowPlayingEmbeddedTagRows(tags: List<Pair<String, String>>?): List<Pair<String, String>> =
    tags ?: listOf("Status" to "Loading from cached audio")

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
    replayGainInspectorEnabled: Boolean = false,
    replayGainMode: ReplayGainMode = ReplayGainMode.Off,
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
        if (replayGainInspectorEnabled && replayGainMode != ReplayGainMode.Off) {
            add(
                NaviampDetailSectionUi(
                    title = "ReplayGain inspector",
                    rows = replayGainInspectorRows(replayGainMode, embeddedTags),
                ),
            )
        }
        if (!replayGainInspectorEnabled) replayGain?.let { replayGain ->
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

private fun Track.replayGainInspectorRows(
    replayGainMode: ReplayGainMode,
    embeddedTags: List<Pair<String, String>>?,
): List<Pair<String, String>> {
    val source = replayGain
        ?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) }
        ?: embeddedTags?.replayGainFromTagRows()
            ?.let { PlaybackReplayGain(it, ReplayGainSource.LocalTags) }
    val adjustment = playbackReplayGainAdjustment(
        PlaybackRequest(
            url = "",
            mediaId = id.value,
            replayGainMode = replayGainMode,
            replayGain = source,
        ),
    )
    return buildList {
        add("Selected mode" to replayGainMode.displayName)
        add("Source" to (source?.source?.displayName ?: "No ReplayGain metadata"))
        add("Active gain" to (adjustment.gainDb?.let { "${it.twoDecimalLabel()} dB" } ?: "Not applied"))
        adjustment.peak?.let { add("Active peak" to it.sixDecimalLabel()) }
        add("Volume factor" to adjustment.volumeFactor.toDouble().sixDecimalLabel())
        if (adjustment.clippingPrevented) add("Clipping prevention" to "Reduced gain")
        source?.replayGain?.trackGainDb?.let { add("Track gain" to "${it.twoDecimalLabel()} dB") }
        source?.replayGain?.albumGainDb?.let { add("Album gain" to "${it.twoDecimalLabel()} dB") }
        source?.replayGain?.trackPeak?.let { add("Track peak" to it.sixDecimalLabel()) }
        source?.replayGain?.albumPeak?.let { add("Album peak" to it.sixDecimalLabel()) }
    }
}

private fun List<Pair<String, String>>.replayGainFromTagRows() =
    replayGainFromAudioTags(map { (key, value) -> AudioTag(key, value) })

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
    val replayGainInspectorEnabled: Boolean = false,
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
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
        albumTitle = albumTitle.orEmpty(),
        albumYear = albumReleaseYear,
        audioInfo = nowPlayingAudioInfoLabel(config.playbackEngineName),
        waveform = config.waveform,
        visualizerFrame = config.visualizerFrame,
        bpm = bpm,
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
            replayGainInspectorEnabled = config.replayGainInspectorEnabled,
            replayGainMode = config.replayGainMode,
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

fun Track.toTrackNowPlayingUi(
    stateLabel: String,
    coverArtUrl: String?,
    playbackProgress: PlaybackProgress,
    playbackState: PlaybackState,
    capabilities: NowPlayingTrackCapabilities,
    hasPrevious: Boolean,
    hasNext: Boolean,
    shuffleEnabled: Boolean,
    shuffleActive: Boolean,
    repeatMode: RepeatMode,
    sleepTimer: NaviampSleepTimerUi,
    relatedLabels: NowPlayingRelatedUiLabels,
    playbackEngineName: String? = null,
    waveform: AudioWaveform? = null,
    visualizerAvailable: Boolean = false,
    visualizerVisible: Boolean = false,
    durationSeconds: Double? = playbackProgress.durationSeconds,
    lyricsVisible: Boolean = false,
    lyricsStatus: String? = null,
    lyrics: Lyrics? = null,
    streamQuality: StreamQuality? = null,
    embeddedTags: List<Pair<String, String>>? = null,
    replayGainInspectorEnabled: Boolean = false,
    replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    playlistChoices: List<NaviampPlaylistChoiceUi> = emptyList(),
    useInlinePlaylistPicker: Boolean = true,
    playlistActionStatus: String? = null,
    backTo: List<NaviampNowPlayingItemUi> = emptyList(),
    upNext: List<NaviampNowPlayingItemUi> = emptyList(),
    related: List<NaviampNowPlayingItemUi> = emptyList(),
    volumePercent: Int = 100,
): NowPlayingUi =
    toNowPlayingUi(
        NowPlayingTrackUiConfig(
            stateLabel = stateLabel,
            coverArtUrl = coverArtUrl,
            playbackEngineName = playbackEngineName,
            waveform = waveform,
            visualizerAvailable = visualizerAvailable,
            visualizerVisible = visualizerVisible,
            positionSeconds = playbackProgress.positionSeconds,
            durationSeconds = durationSeconds,
            volumePercent = volumePercent,
            isPlaying = playbackState == PlaybackState.Playing,
            isPaused = playbackState == PlaybackState.Paused,
            canPlayPause = capabilities.canPlayPause,
            canSeek = capabilities.canSeek,
            canChangeVolume = capabilities.canChangeVolume,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            shuffleEnabled = shuffleEnabled,
            shuffleActive = shuffleActive,
            repeatMode = repeatMode,
            canRepeat = capabilities.canRepeat,
            canStartRadio = capabilities.canStartRadio,
            canAddToPlaylist = capabilities.canAddToPlaylist,
            canSaveQueueAsPlaylist = capabilities.canSaveQueueAsPlaylist,
            sleepTimer = sleepTimer,
            canFavorite = capabilities.canFavorite,
            canRate = capabilities.canRate,
            lyricsAvailable = capabilities.lyricsAvailable,
            lyricsVisible = lyricsVisible,
            lyricsStatus = lyricsStatus,
            lyrics = lyrics,
            menuEnabled = true,
            streamQuality = streamQuality,
            embeddedTags = nowPlayingEmbeddedTagRows(embeddedTags),
            replayGainInspectorEnabled = replayGainInspectorEnabled,
            replayGainMode = replayGainMode,
            playlistChoices = playlistChoices,
            useInlinePlaylistPicker = useInlinePlaylistPicker,
            playlistActionStatus = playlistActionStatus,
            backTo = backTo,
            upNext = upNext,
            related = related,
            relatedTabLabel = relatedLabels.tabLabel,
            relatedEmptyLabel = relatedLabels.emptyLabel,
        ),
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

fun InternetRadioStation.toRadioNowPlayingUi(
    streamMetadata: PlaybackStreamMetadata,
    playbackState: PlaybackState,
    volumePercent: Int,
    radioStations: List<InternetRadioStation>,
    radioTrackArtworkByKey: Map<String, String?>,
    canPlayPause: Boolean = true,
    canChangeVolume: Boolean = false,
): NowPlayingUi {
    val trackArtworkUrl = radioTrackArtworkKey(this, streamMetadata.title)
        ?.let(radioTrackArtworkByKey::get)
    return toNowPlayingUi(
        NowPlayingRadioUiConfig(
            streamTitle = streamMetadata.title,
            coverArtUrl = radioArtworkUrl(this, streamMetadata.properties, trackArtworkUrl),
            stateLabel = playbackState.label(),
            volumePercent = volumePercent,
            isPlaying = playbackState == PlaybackState.Playing,
            isPaused = playbackState == PlaybackState.Paused,
            canPlayPause = canPlayPause,
            canChangeVolume = canChangeVolume,
            radioStations = radioStations
                .sortedBy { station -> station.name.lowercase() }
                .map { station -> station.toNowPlayingStationUi() },
        ),
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
            ).copy(hasAlbum = false)
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
        albumSections = albums.groupedByReleaseSection().map { group ->
            SharedAlbumSectionUi(
                title = group.section.label,
                albums = group.albums.map { it.toSharedMediaItemUi(coverArtUrl, canFavoriteAlbums) },
            )
        },
        sourceContextLabel = artistSourceContextLabel(
            hasProviderMetadata = info != null,
            hasLocalLibraryMatches = albums.isNotEmpty() || popularTracks.isNotEmpty(),
        ),
        localLibraryLabel = artistLocalLibraryLabel(albums.size, popularTracks.size),
        biography = info?.biography,
        popularTracks = popularTracks.map { it.toSharedTrackRowUi(coverArtUrl).copy(hasArtist = false) },
        popularTracksStatus = popularTracksStatus,
        similarArtists = similarArtists.map { it.toSharedSimilarArtistUi() },
        similarArtistsStatus = similarArtistsStatus,
    )

private fun artistSourceContextLabel(
    hasProviderMetadata: Boolean,
    hasLocalLibraryMatches: Boolean,
): String =
    when {
        hasProviderMetadata && hasLocalLibraryMatches -> "Provider info matched with your library"
        hasLocalLibraryMatches -> "Matched from your library"
        hasProviderMetadata -> "Provider info only"
        else -> "No local library match yet"
    }

private fun artistLocalLibraryLabel(albumCount: Int, popularTrackCount: Int): String =
    listOfNotNull(
        "$albumCount ${if (albumCount == 1) "album" else "albums"}",
        popularTrackCount.takeIf { it > 0 }?.let {
            "$it matched popular ${if (it == 1) "track" else "tracks"}"
        },
    ).joinToString(" - ")

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
