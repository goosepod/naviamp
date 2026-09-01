package app.naviamp.presentation

import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.app.NaviampRecentRadioStreamController
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Track
import app.naviamp.domain.playback.EmptyPlaybackProfileRepository
import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackProfileRepository
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.PlaybackQueueGroup
import app.naviamp.domain.media.favoriteAlbumUpdate
import app.naviamp.domain.media.favoriteArtistUpdate
import app.naviamp.domain.media.favoriteTrackUpdate
import app.naviamp.domain.media.resolveTrackArtistNavigation
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.radio.SeededRadioBuildResult
import app.naviamp.domain.radio.SeededRadioRequest
import app.naviamp.domain.radio.albumMixSeededRadioRequest
import app.naviamp.domain.radio.albumSeededRadioRequest
import app.naviamp.domain.radio.artistMixSeededRadioRequest
import app.naviamp.domain.radio.artistSeededRadioRequest
import app.naviamp.domain.radio.decadeRecentRadioStream
import app.naviamp.domain.radio.genreMixRadioRequest
import app.naviamp.domain.radio.genreRecentRadioStream
import app.naviamp.domain.radio.libraryRecentRadioStream
import app.naviamp.domain.radio.popularTracksRadioRequest
import app.naviamp.domain.radio.seededRadioBuildResult
import app.naviamp.domain.radio.trackRadioRequest
import app.naviamp.domain.radio.withRadioCoverArtIds
import app.naviamp.domain.radio.sessionSubtitle
import app.naviamp.domain.Genre
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.withRecentRadioStreams

fun interface NaviampCoreExternalUriPort {
    fun open(uri: String)
}

/** One Core-owned transaction for every action that replaces the active track queue. */
class NaviampCoreQueuePlaybackController(
    private val playback: NaviampLivePlaybackController,
    private val queue: NaviampPlaybackQueueCoordinator,
    private val effects: NaviampCorePlaybackEffectPort,
    private val publishNowPlaying: () -> Unit,
    private val openNowPlaying: () -> Unit,
    private val activeSourceId: () -> String? = { null },
    private val profiles: PlaybackProfileRepository = EmptyPlaybackProfileRepository,
) {
    fun play(
        tracks: List<Track>,
        index: Int = 0,
        shuffle: Boolean = false,
        groupTarget: PlaybackProfileTarget? = null,
        groupLabel: String = "",
        groupWithoutProfile: Boolean = false,
    ): Boolean {
        val selected = if (shuffle) tracks.shuffled() else tracks
        if (selected.isEmpty()) return false
        val profile = groupTarget?.let { target ->
            activeSourceId()?.let { sourceId -> profiles.playbackProfile(sourceId, target) }
        }
        val group = if (!shuffle && groupTarget != null && (groupWithoutProfile || profile != null)) {
            PlaybackQueueGroup(
                id = "${groupTarget.type}:${groupTarget.id}",
                target = groupTarget,
                label = groupLabel,
                startIndex = 0,
                endIndexExclusive = selected.size,
                profile = profile ?: PlaybackProfile(),
            )
        } else {
            null
        }
        val requestedQueue = PlaybackQueue(
            tracks = selected,
            currentIndex = index.coerceIn(selected.indices),
            groups = listOfNotNull(group),
        )
        val update = queue.replaceQueue(requestedQueue)
        if (update.changed) effects.applyQueue(update.queue, update.clearPreparedNext)
        playback.updateCurrentTrack(update.queue.current)
        effects.playQueueSelection(update.queue, update.queue.currentIndex)
        publishNowPlaying()
        openNowPlaying()
        return true
    }

    fun addToQueue(
        tracks: List<Track>,
        groupTarget: PlaybackProfileTarget,
        groupLabel: String,
    ) = queue.appendTracks(
        tracksToAdd = tracks,
        label = "tracks",
        group = savedProfileGroup(groupTarget, groupLabel),
    )

    fun playNext(
        tracks: List<Track>,
        groupTarget: PlaybackProfileTarget,
        groupLabel: String,
    ) = queue.playNextTracks(
        tracksToAdd = tracks,
        label = "tracks",
        group = savedProfileGroup(groupTarget, groupLabel),
    )

    private fun savedProfileGroup(
        target: PlaybackProfileTarget,
        label: String,
    ): PlaybackQueueGroup? {
        val sourceId = activeSourceId() ?: return null
        val profile = profiles.playbackProfile(sourceId, target) ?: return null
        return PlaybackQueueGroup(
            id = "${target.type}:${target.id}",
            target = target,
            label = label,
            startIndex = 0,
            endIndexExclusive = 0,
            profile = profile,
        )
    }
}

/** Shared media transactions used by every route; only final audio/URI effects cross the host boundary. */
interface NaviampCoreTrackRadioTransactions {
    suspend fun startTrackRadio(seed: Track)
    suspend fun addTrackRadio(seed: Track, playNext: Boolean)
}

class NaviampCoreMediaTransactions(
    private val stateStore: NaviampCoreStateStore,
    private val busyIndicator: NaviampCoreBusyIndicator,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val registry: NaviampCoreMediaRegistry,
    private val playback: NaviampLivePlaybackController,
    private val queue: NaviampPlaybackQueueCoordinator,
    private val effects: NaviampCorePlaybackEffectPort,
    private val queuePlayback: NaviampCoreQueuePlaybackController,
    private val downloads: NaviampCoreDownloadsController,
    private val mediaDetails: NaviampCoreMediaDetailController,
    private val recentRadioStreams: NaviampRecentRadioStreamController,
    private val externalUri: NaviampCoreExternalUriPort,
    private val favoritedAtIso8601: () -> String,
    private val publishNowPlaying: () -> Unit,
    private val openNowPlaying: () -> Unit,
    private val selectRadioSeed: (List<Track>) -> Track? = { tracks -> tracks.randomOrNull() },
) : NaviampCoreTrackRadioTransactions {
    fun play(tracks: List<Track>, index: Int = 0, shuffle: Boolean = false) {
        if (!queuePlayback.play(tracks, index, shuffle)) publish("No tracks are available.")
    }

    fun playAlbum(album: Album, tracks: List<Track>, shuffle: Boolean = false) {
        if (!queuePlayback.play(
                tracks = tracks,
                shuffle = shuffle,
                groupTarget = PlaybackProfileTarget(PlaybackProfileTargetType.Album, album.id.value),
                groupLabel = album.title,
                groupWithoutProfile = true,
            )
        ) {
            publish("No tracks are available.")
        }
    }

    fun playNext(tracks: List<Track>) = apply(queue.playNextTracks(tracks, "tracks"))
    fun playNextTrack(track: Track) = apply(queue.playNextTrack(track))
    fun addToQueue(tracks: List<Track>) = apply(queue.appendTracks(tracks, "tracks"))

    fun addAlbumToQueue(album: Album, tracks: List<Track>) = apply(
        queuePlayback.addToQueue(
            tracks = tracks,
            groupTarget = PlaybackProfileTarget(PlaybackProfileTargetType.Album, album.id.value),
            groupLabel = album.title,
        ),
    )

    override suspend fun startTrackRadio(seed: Track) {
        val settings = stateStore.state.value.shell.playback.settings
        startSeededMix(trackRadioRequest(seed, settings.sonicSimilarityEnabled))
    }

    override suspend fun addTrackRadio(seed: Track, playNext: Boolean) {
        val provider = providerOrPublish() ?: return
        busyIndicator.during("Loading track radio...") {
            runCatching {
                val settings = stateStore.state.value.shell.playback.settings
                RadioService(provider, tuning = settings.radioTuning)
                    .trackRadio(seed, settings.sonicSimilarityEnabled)
            }.onSuccess { if (playNext) playNext(it) else addToQueue(it) }
                .onFailure { publish(it.message ?: "Could not load track radio.") }
        }
    }

    suspend fun startAlbumRadio(album: Album) {
        val cachedTracks = registry.albumDetails?.takeIf { it.album.id == album.id }?.tracks.orEmpty()
            .ifEmpty { registry.tracks().filter { it.albumId == album.id } }
        val loadedTracks = if (cachedTracks.isNotEmpty()) {
            cachedTracks
        } else {
            val provider = providerOrPublish() ?: return
            publish("Finding a track from ${album.title}…")
            runCatching { provider.album(album.id).tracks }
                .getOrElse { return publish(it.message ?: "Could not load ${album.title}.") }
        }
        val seed = selectRadioSeed(loadedTracks)
        if (seed != null) {
            startSeededMix(albumSeededRadioRequest(album, seed, loadedTracks))
        } else {
            publish("${album.title} has no tracks to play.")
        }
    }

    suspend fun startArtistRadio(artist: Artist) {
        val cachedPopularTracks = registry.artistPopularTracks.takeIf {
            registry.artistDetails?.artist?.id == artist.id
        }.orEmpty()
        val popularTracks = if (cachedPopularTracks.isNotEmpty()) {
            cachedPopularTracks
        } else {
            val provider = providerOrPublish() ?: return
            publish("Finding a track by ${artist.name}…")
            runCatching {
                val albums = provider.artist(artist.id).albums
                val album = albums.randomOrNull() ?: return@runCatching emptyList()
                provider.album(album.id).tracks
            }.getOrElse { return publish(it.message ?: "Could not load tracks by ${artist.name}.") }
        }
        val seed = selectRadioSeed(popularTracks)
        if (seed != null) {
            startSeededMix(artistSeededRadioRequest(artist, seed))
        } else {
            publish("${artist.name} has no tracks to play.")
        }
    }

    suspend fun startLibraryRadio() {
        val provider = providerOrPublish() ?: return
        publish("Finding something to play…")
        val seed = runCatching { selectRadioSeed(provider.tracks(limit = 50)) }
            .getOrElse { return publish(it.message ?: "Could not load Library Radio.") }
            ?: return publish("No library tracks are available.")
        startSeededMix(
            SeededRadioRequest(
                label = "Library Radio",
                seedTrack = seed,
                recentRadioStream = libraryRecentRadioStream(),
                loadRest = { it.libraryRadio() },
            ),
        )
    }

    suspend fun startGenreRadio(genre: String) {
        val provider = providerOrPublish() ?: return
        publish("Finding a $genre track…")
        val seed = runCatching {
            val album = provider.albumsByGenre(genre, limit = 10).randomOrNull()
                ?: return@runCatching null
            selectRadioSeed(provider.album(album.id).tracks)
        }.getOrElse { return publish(it.message ?: "Could not load $genre radio.") }
            ?: return publish("No $genre tracks are available.")
        startSeededMix(
            SeededRadioRequest(
                label = "$genre radio",
                seedTrack = seed,
                recentRadioStream = genreRecentRadioStream(Genre(genre)),
                loadRest = { it.genreRadio(genre) },
            ),
        )
    }

    suspend fun startDecadeRadio(fromYear: Int, toYear: Int) {
        val provider = providerOrPublish() ?: return
        val label = "$fromYear–$toYear radio"
        publish("Finding something from $fromYear–$toYear…")
        val seed = runCatching {
            val album = provider.albumsByYear(fromYear, toYear, limit = 10).randomOrNull()
                ?: return@runCatching null
            selectRadioSeed(provider.album(album.id).tracks)
        }.getOrElse { return publish(it.message ?: "Could not load $label.") }
            ?: return publish("No tracks from $fromYear–$toYear are available.")
        startSeededMix(
            SeededRadioRequest(
                label = label,
                seedTrack = seed,
                recentRadioStream = decadeRecentRadioStream(fromYear, toYear),
                loadRest = { it.decadeRadio(fromYear, toYear) },
            ),
        )
    }

    suspend fun startArtistMix(artists: List<Artist>, seedTracks: List<Track>) {
        val seed = seedTracks.firstOrNull() ?: return publish("Select artists with matched songs first.")
        startSeededMix(artistMixSeededRadioRequest(artists, seed, seedTracks))
    }

    suspend fun startPopularTracksRadio(tracks: List<Track>) {
        val distinctTracks = tracks.distinctBy(Track::id)
        val selectedSeed = selectRadioSeed(distinctTracks)
        val orderedTracks = selectedSeed?.let { seed -> listOf(seed) + distinctTracks.filterNot { it.id == seed.id } }
            ?: distinctTracks
        val request = popularTracksRadioRequest(orderedTracks)
            ?: return publish("No popular tracks are available.")
        startSeededMix(request)
    }

    suspend fun startAlbumMix(albums: List<Album>, seedTracks: List<Track>) {
        val seed = seedTracks.firstOrNull() ?: return publish("Select albums with matched songs first.")
        startSeededMix(albumMixSeededRadioRequest(albums, seed, seedTracks))
    }

    suspend fun startGenreMix(genres: List<Genre>) {
        val provider = providerOrPublish() ?: return
        val request = genreMixRadioRequest(genres)
        publish("Finding a track for ${request.label}…")
        val seed = runCatching {
            val genre = genres.randomOrNull()?.name ?: return@runCatching null
            val album = provider.albumsByGenre(genre, limit = 10).randomOrNull()
                ?: return@runCatching null
            selectRadioSeed(provider.album(album.id).tracks)
        }.getOrElse { return publish(it.message ?: "Could not build ${request.label}.") }
            ?: return publish("${request.label} did not return any tracks.")
        startSeededMix(
            SeededRadioRequest(
                label = request.label,
                seedTrack = seed,
                recentRadioStream = request.recentRadioStream,
                loadRest = request.loadTracks,
            ),
        )
    }

    suspend fun startRandomAlbumRadio() {
        val provider = providerOrPublish() ?: return
        publish("Finding a random album…")
        val albumAndTracks = runCatching {
            val album = provider.albumList(app.naviamp.domain.provider.AlbumListType.Random, 1).firstOrNull()
                ?: return@runCatching null
            album to provider.album(album.id).tracks
        }.getOrElse { return publish(it.message ?: "Could not start random album radio.") }
            ?: return publish("No random album is available.")
        val (album, tracks) = albumAndTracks
        val seed = selectRadioSeed(tracks) ?: return publish("${album.title} has no tracks to play.")
        startSeededMix(app.naviamp.domain.radio.randomAlbumSeededRadioRequest(album, seed))
    }

    fun download(label: String, tracks: List<Track>) {
        downloads.downloadTracks(label, tracks, includeCompletedCount = false)
    }

    suspend fun addToPlaylist(tracks: List<Track>, choice: NaviampPlaylistChoiceUi) {
        val provider = providerOrPublish() ?: return
        runCatching { provider.addTracksToPlaylist(choice.id, tracks.map(Track::id)) }
            .onSuccess {
                downloads.playlistTracksChanged(choice.id)
                publish("Added ${tracks.size} tracks to ${choice.name}.")
            }
            .onFailure { publish(it.message ?: "Could not add tracks to playlist.") }
    }

    suspend fun createPlaylist(tracks: List<Track>, requestedName: String) {
        val provider = providerOrPublish() ?: return
        val name = requestedName.trim()
        if (name.isEmpty()) return publish("Playlist name cannot be blank.")
        runCatching { provider.createPlaylist(name, tracks.map(Track::id)) }
            .onSuccess { publish("Created $name.") }
            .onFailure { publish(it.message ?: "Could not create playlist.") }
    }

    suspend fun toggleFavorite(track: Track) {
        val provider = providerOrPublish() ?: return
        mutate("Track favorites are not supported.", { favoriteTrackUpdate(provider, track, favoritedAtIso8601()) }) {
            registry.updateTrack(it)
            val update = queue.updateTrack(it)
            if (update.changed) effects.applyQueue(update.queue, update.clearPreparedNext)
            if (playback.state.value.currentTrack?.id == it.id) playback.updateCurrentTrack(it)
            updateTrackFavoriteUi(it.id.value, it.favoritedAtIso8601 != null)
            publishNowPlaying()
        }
    }

    suspend fun toggleFavorite(album: Album) {
        val provider = providerOrPublish() ?: return
        mutate("Album favorites are not supported.", { favoriteAlbumUpdate(provider, album, favoritedAtIso8601()) }) {
            registry.updateAlbum(it)
            updateAlbumFavoriteUi(it.id.value, it.favoritedAtIso8601 != null)
        }
    }

    suspend fun toggleFavorite(artist: Artist) {
        val provider = providerOrPublish() ?: return
        mutate("Artist favorites are not supported.", { favoriteArtistUpdate(provider, artist, favoritedAtIso8601()) }) {
            registry.updateArtist(it)
            updateArtistFavoriteUi(it.id.value, it.favoritedAtIso8601 != null)
        }
    }

    suspend fun openAlbum(track: Track) {
        val id = track.albumId ?: return publish("Album is not available for this track.")
        mediaDetails.execute(
            NaviampCoreCommand.Media.ItemAction(
                app.naviamp.ui.NaviampMediaItemActionRequest(
                    SharedMediaItemUi(id.value, track.albumTitle ?: "Album", track.artistName),
                    app.naviamp.ui.NaviampMediaItemCommand.Album(app.naviamp.ui.NaviampArtistAlbumCommand.Select),
                ),
            ),
        )
    }

    suspend fun openArtist(track: Track, artistId: String?, artistName: String?) {
        val artist = resolveTrackArtistNavigation(track, artistId, artistName) { query, limit ->
            providerSource.current()?.search(query, limit)?.artists.orEmpty()
        } ?: return publish("Artist is not available for this track.")
        mediaDetails.execute(
            NaviampCoreCommand.Media.ItemAction(
                app.naviamp.ui.NaviampMediaItemActionRequest(
                    SharedMediaItemUi(artist.id.value, artist.name, ""),
                    app.naviamp.ui.NaviampMediaItemCommand.Artist(app.naviamp.ui.NaviampArtistMediaCommand.Select),
                ),
            ),
        )
    }

    fun openExternal(uri: String) {
        if (uri.isBlank()) publish("Artist link is missing.") else externalUri.open(uri)
    }

    private suspend fun startSeededMix(request: app.naviamp.domain.radio.SeededRadioRequest) {
        val provider = providerOrPublish() ?: return
        play(listOf(request.seedTrack))
        publish("Playing ${request.label} while the queue builds.")
        when (val result = seededRadioBuildResult(request, RadioService(provider, tuning = radioTuning()))) {
            is SeededRadioBuildResult.Ready -> {
                val stillPlayingSeed = playback.state.value.currentTrack?.id == request.seedTrack.id &&
                    playback.state.value.queue.current?.id == request.seedTrack.id
                if (!stillPlayingSeed) return
                val update = queue.replaceGeneratedRadioUpcomingTracks(
                    currentTrack = request.seedTrack,
                    fetchedTracks = result.queue.filterNot { it.id == request.seedTrack.id },
                    requestIsCurrent = true,
                )
                if (update.changed) {
                    effects.applyQueue(update.queue, update.clearPreparedNext)
                    publishNowPlaying()
                }
                rememberRecentRadio(result.recentRadioStream, result.queue)
                publish("Playing ${request.label}.")
            }
            is SeededRadioBuildResult.Failed ->
                publish("Playing ${request.label}; the rest of the queue could not be built.")
        }
    }

    private fun radioTuning() = stateStore.state.value.shell.playback.settings.radioTuning

    private fun rememberRecentRadio(stream: RecentRadioStream?, tracks: List<Track>) {
        if (stream == null) return
        val updated = recentRadioStreams.remember(stream.withRadioCoverArtIds(tracks), tracks)
        val provider = providerSource.current()
        stateStore.updateShell { shell ->
            val recentItems = updated.map { recent ->
                val coverArtUrls = recent.coverArtIds
                    .mapNotNull { id -> provider?.coverArtUrl(id) }
                    .distinct()
                    .take(4)
                SharedMediaItemUi(
                    id = recent.id,
                    title = recent.label,
                    subtitle = recent.sessionSubtitle(),
                    coverArtUrl = coverArtUrls.firstOrNull(),
                    coverArtUrls = coverArtUrls,
                )
            }
            val content = shell.home.content.withRecentRadioStreams(
                streams = recentItems,
                interfaceSettings = shell.general.interfaceSettings,
            )
            val collectionPage = shell.home.collectionPage?.let { page ->
                content.collectionSections
                    .firstOrNull { it.id == page.section.id }
                    ?.let { section -> page.copy(section = section) }
            }
            shell.copy(
                home = shell.home.copy(
                    content = content,
                    collectionPage = collectionPage,
                ),
            )
        }
    }

    private suspend fun <T> mutate(unsupported: String, mutation: suspend () -> T?, apply: (T) -> Unit) {
        runCatching { mutation() }.onSuccess { if (it == null) publish(unsupported) else apply(it) }
            .onFailure { publish(it.message ?: "Could not update favorite.") }
    }

    private fun apply(update: app.naviamp.domain.playback.PlaybackQueueUpdate) {
        publish(update.status)
        if (update.tracksChanged) {
            effects.applyQueue(update.queue, clearPreparedNext = true)
            publishNowPlaying()
        }
    }

    private fun providerOrPublish() = providerSource.current().also {
        if (it == null) publish("Connect to Navidrome to use this action.")
    }

    fun publish(message: String) {
        stateStore.update { it.copy(overlays = it.overlays.copy(status = message)) }
    }

    private fun updateAlbumFavoriteUi(id: String, active: Boolean) {
        fun List<SharedMediaItemUi>.updated() = map { if (it.id == id) it.copy(favoriteActive = active) else it }
        stateStore.updateShell { shell ->
            val home = shell.home.content
            val artist = shell.artistDetail.detail
            shell.copy(
                home = shell.home.copy(content = home.copy(
                    recentlyAddedAlbums = home.recentlyAddedAlbums.updated(),
                    mixAlbums = home.mixAlbums.updated(),
                    recentAlbums = home.recentAlbums.updated(),
                    frequentAlbums = home.frequentAlbums.updated(),
                    randomAlbums = home.randomAlbums.updated(),
                    genreSpotlightAlbums = home.genreSpotlightAlbums.updated(),
                    decadeAlbums = home.decadeAlbums.updated(),
                )),
                search = shell.search.copy(results = shell.search.results.copy(albums = shell.search.results.albums.updated())),
                albumDetail = shell.albumDetail.copy(
                    selectedAlbum = shell.albumDetail.selectedAlbum?.let { if (it.id == id) it.copy(favoriteActive = active) else it },
                    detail = shell.albumDetail.detail?.let { detail ->
                        detail.copy(album = if (detail.album.id == id) detail.album.copy(favoriteActive = active) else detail.album)
                    },
                ),
                artistDetail = shell.artistDetail.copy(detail = artist?.copy(
                    albums = artist.albums.updated(),
                    albumSections = artist.albumSections.map { it.copy(albums = it.albums.updated()) },
                )),
            )
        }
    }

    private fun updateArtistFavoriteUi(id: String, active: Boolean) {
        fun List<SharedMediaItemUi>.updated() = map { if (it.id == id) it.copy(favoriteActive = active) else it }
        stateStore.updateShell { shell -> shell.copy(
            search = shell.search.copy(results = shell.search.results.copy(artists = shell.search.results.artists.updated())),
            library = shell.library.copy(artists = shell.library.artists.updated()),
            artistDetail = shell.artistDetail.copy(
                selectedArtist = shell.artistDetail.selectedArtist?.let { if (it.id == id) it.copy(favoriteActive = active) else it },
                detail = shell.artistDetail.detail?.let { detail ->
                    detail.copy(artist = if (detail.artist.id == id) detail.artist.copy(favoriteActive = active) else detail.artist)
                },
            ),
        ) }
    }

    private fun updateTrackFavoriteUi(id: String, active: Boolean) {
        fun List<app.naviamp.ui.SharedTrackRowUi>.updated() = map {
            if (it.id == id) it.copy(favoriteActive = active) else it
        }
        stateStore.updateShell { shell -> shell.copy(
            home = shell.home.copy(content = shell.home.content.copy(
                recentlyPlayedTracks = shell.home.content.recentlyPlayedTracks.updated(),
                sonicDiscoveryRows = shell.home.content.sonicDiscoveryRows.map { it.copy(tracks = it.tracks.updated()) },
            )),
            search = shell.search.copy(results = shell.search.results.copy(tracks = shell.search.results.tracks.updated())),
            albumDetail = shell.albumDetail.copy(detail = shell.albumDetail.detail?.let { it.copy(tracks = it.tracks.updated()) }),
            artistDetail = shell.artistDetail.copy(detail = shell.artistDetail.detail?.let { it.copy(popularTracks = it.popularTracks.updated()) }),
            playlistDetail = shell.playlistDetail.copy(detail = shell.playlistDetail.detail?.let { it.copy(tracks = it.tracks.updated()) }),
        ) }
    }
}
