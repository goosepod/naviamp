package app.naviamp.presentation

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.albummix.AlbumMixBuilderService
import app.naviamp.domain.artistmix.ArtistMixBuilderService
import app.naviamp.domain.genremix.GenreMixBuilderService
import app.naviamp.domain.library.LibraryGenreOntologyProjection
import app.naviamp.domain.mixbuilder.albumMixGeneratedQueue
import app.naviamp.domain.mixbuilder.artistMixGeneratedQueue
import app.naviamp.domain.mixbuilder.genreMixGeneratedQueue
import app.naviamp.ui.SharedAlbumMixBuilderUi
import app.naviamp.ui.SharedArtistMixBuilderUi
import app.naviamp.ui.SharedGenreMixBuilderUi
import app.naviamp.ui.SharedGenreMixItemUi
import app.naviamp.ui.SharedGenreMixTreeRowUi
import app.naviamp.ui.genreDisplayTitle
import app.naviamp.ui.toSharedGenreMixItemUi
import app.naviamp.ui.toSharedMediaItemUi

interface NaviampCoreStandardMixPlaybackPort {
    suspend fun playArtistMix(artists: List<Artist>, seedTracks: List<Track>)
    suspend fun playAlbumMix(albums: List<Album>, seedTracks: List<Track>)
    suspend fun playGenreMix(genres: List<Genre>)
}

/** Owns Artist, Album, and Genre builder state, selection, discovery, and playback intent. */
class NaviampCoreStandardMixController(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val artistService: () -> ArtistMixBuilderService,
    private val albumService: () -> AlbumMixBuilderService,
    private val genreService: () -> GenreMixBuilderService,
    private val playback: NaviampCoreStandardMixPlaybackPort,
) : NaviampCoreCommandController {
    private var selectedArtists = emptyList<Artist>()
    private var artistSuggestions = emptyList<Artist>()
    private var artistTracks = emptyMap<String, List<Track>>()
    private var selectedAlbums = emptyList<Album>()
    private var albumSuggestions = emptyList<Album>()
    private var albumTracks = emptyMap<String, List<Track>>()
    private var selectedGenres = emptyList<Genre>()
    private var genreSuggestions = emptyList<Genre>()
    private var genreProjection = LibraryGenreOntologyProjection()
    private var expandedGenreOntologyIds = emptySet<String>()
    private var artistGeneration = 0L
    private var albumGeneration = 0L
    private var genreGeneration = 0L

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        is NaviampCoreCommand.MixBuilder.Artist -> when (val action = command.action) {
            is NaviampCoreCommand.ArtistAction.ChangeQuery -> {
                updateArtistUi { it.copy(query = action.query) }
                NaviampCoreImmediateCommandResult.Handled()
            }
            else -> NaviampCoreImmediateCommandResult.Deferred
        }
        is NaviampCoreCommand.MixBuilder.Album -> when (val action = command.action) {
            is NaviampCoreCommand.AlbumAction.ChangeQuery -> {
                updateAlbumUi { it.copy(query = action.query) }
                NaviampCoreImmediateCommandResult.Handled()
            }
            else -> NaviampCoreImmediateCommandResult.Deferred
        }
        is NaviampCoreCommand.MixBuilder.Genre -> when (val action = command.action) {
            is NaviampCoreCommand.GenreAction.ChangeQuery -> {
                updateGenreUi { it.copy(query = action.query) }
                NaviampCoreImmediateCommandResult.Handled()
            }
            else -> NaviampCoreImmediateCommandResult.Deferred
        }
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            is NaviampCoreCommand.MixBuilder.Artist -> executeArtist(command.action)
            is NaviampCoreCommand.MixBuilder.Album -> executeAlbum(command.action)
            is NaviampCoreCommand.MixBuilder.Genre -> executeGenre(command.action)
            else -> return null
        }
        return NaviampCoreCommandResult.Completed
    }

    suspend fun initializeArtist() = loadArtistSuggestions(initial = true)
    suspend fun initializeAlbum() = loadAlbumSuggestions(initial = true)
    suspend fun initializeGenre() = loadGenreSuggestions()

    private suspend fun executeArtist(action: NaviampCoreCommand.ArtistAction) {
        when (action) {
            is NaviampCoreCommand.ArtistAction.ChangeQuery ->
                updateArtistUi { it.copy(query = action.query) }
            NaviampCoreCommand.ArtistAction.Search -> loadArtistSuggestions(initial = false)
            is NaviampCoreCommand.ArtistAction.Select -> selectArtist(action.artist.id)
            is NaviampCoreCommand.ArtistAction.Remove -> removeArtist(action.artist.id)
            NaviampCoreCommand.ArtistAction.Reset -> resetArtist()
            NaviampCoreCommand.ArtistAction.Play -> playArtistMix()
        }
    }

    private suspend fun executeAlbum(action: NaviampCoreCommand.AlbumAction) {
        when (action) {
            is NaviampCoreCommand.AlbumAction.ChangeQuery -> updateAlbumUi { it.copy(query = action.query) }
            NaviampCoreCommand.AlbumAction.Search -> loadAlbumSuggestions(initial = false)
            is NaviampCoreCommand.AlbumAction.Select -> selectAlbum(action.album.id)
            is NaviampCoreCommand.AlbumAction.Remove -> removeAlbum(action.album.id)
            NaviampCoreCommand.AlbumAction.Reset -> resetAlbum()
            NaviampCoreCommand.AlbumAction.Play -> playAlbumMix()
        }
    }

    private suspend fun executeGenre(action: NaviampCoreCommand.GenreAction) {
        when (action) {
            is NaviampCoreCommand.GenreAction.ChangeQuery -> updateGenreUi { it.copy(query = action.query) }
            NaviampCoreCommand.GenreAction.Search -> loadGenreSuggestions()
            is NaviampCoreCommand.GenreAction.Select -> selectGenre(action.genre.id)
            is NaviampCoreCommand.GenreAction.Remove -> removeGenre(action.genre.id)
            is NaviampCoreCommand.GenreAction.ToggleBranch -> toggleGenreBranch(action.ontologyId)
            NaviampCoreCommand.GenreAction.Reset -> resetGenre()
            NaviampCoreCommand.GenreAction.Play -> playGenreMix()
        }
    }

    private suspend fun loadArtistSuggestions(initial: Boolean) {
        val generation = ++artistGeneration
        updateArtistUi { it.copy(loading = true, status = null) }
        runCatching {
            if (initial) artistService().initialSuggestions(selectedArtists)
            else artistService().searchSuggestions(currentArtistUi().query, selectedArtists)
        }.onSuccess { suggestions ->
            if (generation != artistGeneration) return@onSuccess
            artistSuggestions = suggestions
            publishArtist(
                loading = false,
                status = if (suggestions.isEmpty()) {
                    if (initial) "No artist suggestions yet." else "No artists matched."
                } else null,
            )
        }.onFailure { cause ->
            if (generation == artistGeneration) publishArtist(false, cause.message ?: "Could not load artists.")
        }
    }

    private suspend fun selectArtist(id: String) {
        val artist = artistSuggestions.firstOrNull { it.id.value == id }
        if (artist == null) {
            publishArtist(false, "Artist is no longer available.")
            return
        }
        selectedArtists = (selectedArtists + artist).distinctBy { it.id }
        publishArtist(true, "Loading ${artist.name} songs...")
        runCatching { artistService().popularTracks(artist) }
            .onSuccess { tracks ->
                artistTracks = artistTracks + (artist.id.value to tracks)
                publishArtist(false, if (tracks.isEmpty()) "${artist.name} popular songs were not matched." else null)
            }
            .onFailure { cause -> publishArtist(false, cause.message ?: "Could not load ${artist.name} songs.") }
        loadArtistRelatedSuggestions(artist)
    }

    private suspend fun removeArtist(id: String) {
        selectedArtists = selectedArtists.filterNot { it.id.value == id }
        artistTracks = artistTracks - id
        loadArtistSuggestions(initial = true)
    }

    private suspend fun loadArtistRelatedSuggestions(seed: Artist) {
        val generation = ++artistGeneration
        updateArtistUi { it.copy(loading = true) }
        runCatching { artistService().relatedSuggestions(selectedArtists, seed) }
            .onSuccess { suggestions ->
                if (generation != artistGeneration) return@onSuccess
                artistSuggestions = suggestions
                publishArtist(false, if (suggestions.isEmpty()) "No artist suggestions yet." else null)
            }
            .onFailure { cause ->
                if (generation == artistGeneration) {
                    publishArtist(false, cause.message ?: "Could not load similar artists.")
                }
            }
    }

    private suspend fun resetArtist() {
        selectedArtists = emptyList()
        artistSuggestions = emptyList()
        artistTracks = emptyMap()
        updateArtistUi { SharedArtistMixBuilderUi() }
        loadArtistSuggestions(initial = true)
    }

    private suspend fun playArtistMix() {
        val queue = artistMixGeneratedQueue(selectedArtists, artistTracks)
        if (queue.artists.isEmpty() || queue.popularTracks.isEmpty()) {
            publishArtist(false, "Select artists with matched songs first.")
            return
        }
        runCatching { playback.playArtistMix(queue.artists, queue.popularTracks) }
            .onSuccess { publishArtist(false, null) }
            .onFailure { publishArtist(false, it.message ?: "Could not play artist mix.") }
    }

    private suspend fun loadAlbumSuggestions(initial: Boolean) {
        val generation = ++albumGeneration
        updateAlbumUi { it.copy(loading = true, status = null) }
        runCatching {
            if (initial) albumService().initialSuggestions(selectedAlbums)
            else albumService().searchSuggestions(currentAlbumUi().query, selectedAlbums)
        }.onSuccess { suggestions ->
            if (generation != albumGeneration) return@onSuccess
            albumSuggestions = suggestions
            publishAlbum(false, if (suggestions.isEmpty()) {
                if (initial) "No album suggestions yet." else "No albums matched."
            } else null)
        }.onFailure { cause ->
            if (generation == albumGeneration) publishAlbum(false, cause.message ?: "Could not load albums.")
        }
    }

    private suspend fun selectAlbum(id: String) {
        val album = albumSuggestions.firstOrNull { it.id.value == id }
        if (album == null) {
            publishAlbum(false, "Album is no longer available.")
            return
        }
        selectedAlbums = (selectedAlbums + album).distinctBy { it.id }
        publishAlbum(true, "Loading ${album.title} songs...")
        runCatching { albumService().selectedTracks(album) }
            .onSuccess { tracks ->
                albumTracks = albumTracks + (album.id.value to tracks)
                publishAlbum(false, if (tracks.isEmpty()) "${album.title} did not return tracks." else null)
            }
            .onFailure { cause -> publishAlbum(false, cause.message ?: "Could not load ${album.title} songs.") }
        loadAlbumRelatedSuggestions(album)
    }

    private suspend fun removeAlbum(id: String) {
        selectedAlbums = selectedAlbums.filterNot { it.id.value == id }
        albumTracks = albumTracks - id
        loadAlbumSuggestions(initial = true)
    }

    private suspend fun loadAlbumRelatedSuggestions(seed: Album) {
        val generation = ++albumGeneration
        updateAlbumUi { it.copy(loading = true) }
        runCatching { albumService().relatedSuggestions(selectedAlbums, seed) }
            .onSuccess { suggestions ->
                if (generation != albumGeneration) return@onSuccess
                albumSuggestions = suggestions
                publishAlbum(false, if (suggestions.isEmpty()) "No album suggestions yet." else null)
            }
            .onFailure { cause ->
                if (generation == albumGeneration) {
                    publishAlbum(false, cause.message ?: "Could not load related albums.")
                }
            }
    }

    private suspend fun resetAlbum() {
        selectedAlbums = emptyList()
        albumSuggestions = emptyList()
        albumTracks = emptyMap()
        updateAlbumUi { SharedAlbumMixBuilderUi() }
        loadAlbumSuggestions(initial = true)
    }

    private suspend fun playAlbumMix() {
        val queue = albumMixGeneratedQueue(selectedAlbums, albumTracks)
        if (queue.albums.isEmpty() || queue.selectedTracks.isEmpty()) {
            publishAlbum(false, "Select albums with matched songs first.")
            return
        }
        runCatching { playback.playAlbumMix(queue.albums, queue.selectedTracks) }
            .onSuccess { publishAlbum(false, null) }
            .onFailure { publishAlbum(false, it.message ?: "Could not play album mix.") }
    }

    private suspend fun loadGenreSuggestions() {
        val generation = ++genreGeneration
        updateGenreUi { it.copy(loading = true, status = null) }
        runCatching {
            val service = genreService()
            service.browseProjection() to service.searchSuggestions(currentGenreUi().query, selectedGenres)
        }.onSuccess { (projection, suggestions) ->
            if (generation != genreGeneration) return@onSuccess
            genreProjection = projection
            genreSuggestions = suggestions
                publishGenre(false, if (suggestions.isEmpty()) "No genres matched." else null)
            }
            .onFailure { cause ->
                if (generation == genreGeneration) publishGenre(false, cause.message ?: "Could not load genres.")
            }
    }

    private suspend fun selectGenre(id: String) {
        val genre = (genreSuggestions + genreProjection.selectableGenres).firstOrNull { it.name == id }
        if (genre == null) {
            publishGenre(false, "Genre is no longer available.")
            return
        }
        selectedGenres = (selectedGenres + genre).distinctBy { it.name.lowercase() }
        loadGenreSuggestions()
    }

    private suspend fun removeGenre(id: String) {
        selectedGenres = selectedGenres.filterNot { it.name == id }
        loadGenreSuggestions()
    }

    private fun toggleGenreBranch(ontologyId: String) {
        val node = genreProjection.nodes.firstOrNull { it.id == ontologyId } ?: return
        if (node.childIds.isEmpty()) return
        expandedGenreOntologyIds = if (ontologyId in expandedGenreOntologyIds) {
            expandedGenreOntologyIds - ontologyId
        } else {
            expandedGenreOntologyIds + ontologyId
        }
        publishGenre(loading = false, status = currentGenreUi().status)
    }

    private suspend fun resetGenre() {
        selectedGenres = emptyList()
        genreSuggestions = emptyList()
        genreProjection = LibraryGenreOntologyProjection()
        expandedGenreOntologyIds = emptySet()
        updateGenreUi { SharedGenreMixBuilderUi(initialized = true) }
        loadGenreSuggestions()
    }

    private suspend fun playGenreMix() {
        val queue = genreMixGeneratedQueue(selectedGenres)
        if (queue.genres.isEmpty()) {
            publishGenre(false, "Select at least one genre first.")
            return
        }
        runCatching { playback.playGenreMix(queue.genres) }
            .onSuccess { publishGenre(false, null) }
            .onFailure { publishGenre(false, it.message ?: "Could not play genre mix.") }
    }

    private fun publishArtist(loading: Boolean, status: String?) {
        val art = coverArtUrl()
        updateArtistUi {
            it.copy(
                selectedArtists = selectedArtists.map { artist -> artist.toSharedMediaItemUi(art) },
                suggestedArtists = artistSuggestions.map { artist -> artist.toSharedMediaItemUi(art) },
                loading = loading,
                status = status,
            )
        }
    }

    private fun publishAlbum(loading: Boolean, status: String?) {
        val art = coverArtUrl()
        updateAlbumUi {
            it.copy(
                selectedAlbums = selectedAlbums.map { album -> album.toSharedMediaItemUi(art) },
                suggestedAlbums = albumSuggestions.map { album -> album.toSharedMediaItemUi(art) },
                loading = loading,
                status = status,
            )
        }
    }

    private fun publishGenre(loading: Boolean, status: String?) {
        updateGenreUi {
            it.copy(
                selectedGenres = selectedGenres.map(Genre::toSharedGenreMixItemUi),
                suggestedGenres = genreSuggestions.map(Genre::toSharedGenreMixItemUi),
                treeRows = genreTreeRows(genreProjection, expandedGenreOntologyIds, selectedGenres),
                unmatchedGenres = genreProjection.unmatchedGenreNames
                    .map(::Genre)
                    .map(Genre::toSharedGenreMixItemUi),
                loading = loading,
                status = status,
                initialized = true,
            )
        }
    }

    private fun coverArtUrl(): (String?) -> String? {
        val provider = providerSource.current()
        return { id -> id?.let { provider?.coverArtUrl(it) } }
    }

    private fun currentArtistUi() = stateStore.state.value.shell.artistMixBuilder
    private fun currentAlbumUi() = stateStore.state.value.shell.albumMixBuilder
    private fun currentGenreUi() = stateStore.state.value.shell.genreMixBuilder

    private fun updateArtistUi(transform: (SharedArtistMixBuilderUi) -> SharedArtistMixBuilderUi) {
        stateStore.updateShell { shell -> shell.copy(artistMixBuilder = transform(shell.artistMixBuilder)) }
    }

    private fun updateAlbumUi(transform: (SharedAlbumMixBuilderUi) -> SharedAlbumMixBuilderUi) {
        stateStore.updateShell { shell -> shell.copy(albumMixBuilder = transform(shell.albumMixBuilder)) }
    }

    private fun updateGenreUi(transform: (SharedGenreMixBuilderUi) -> SharedGenreMixBuilderUi) {
        stateStore.updateShell { shell -> shell.copy(genreMixBuilder = transform(shell.genreMixBuilder)) }
    }
}

internal fun genreTreeRows(
    projection: LibraryGenreOntologyProjection,
    expandedIds: Set<String>,
    selectedGenres: List<Genre>,
): List<SharedGenreMixTreeRowUi> {
    val nodesById = projection.nodes.associateBy { it.id }
    val selectedNames = selectedGenres.map { it.name.lowercase() }.toSet()
    val rows = mutableListOf<SharedGenreMixTreeRowUi>()
    fun appendNode(id: String, depth: Int, path: Set<String>) {
        if (id in path) return
        val node = nodesById[id] ?: return
        val providerName = node.libraryGenreNames.firstOrNull()
        rows += SharedGenreMixTreeRowUi(
            ontologyId = node.id,
            title = genreDisplayTitle(node.canonicalName),
            subtitle = genreCountSubtitle(node.trackCount, node.albumCount),
            depth = depth,
            expandable = node.childIds.isNotEmpty(),
            expanded = node.id in expandedIds,
            genre = providerName?.let { SharedGenreMixItemUi(id = it, title = it) },
            selected = providerName?.lowercase() in selectedNames,
        )
        if (node.id in expandedIds) {
            node.childIds
                .mapNotNull(nodesById::get)
                .sortedBy { it.canonicalName.lowercase() }
                .forEach { child -> appendNode(child.id, depth + 1, path + id) }
        }
    }
    projection.rootIds
        .mapNotNull(nodesById::get)
        .sortedBy { it.canonicalName.lowercase() }
        .forEach { appendNode(it.id, depth = 0, path = emptySet()) }
    return rows
}

private fun genreCountSubtitle(trackCount: Int?, albumCount: Int?): String =
    listOfNotNull(
        trackCount?.let { "$it ${if (it == 1) "track" else "tracks"}" },
        albumCount?.let { "$it ${if (it == 1) "album" else "albums"}" },
    ).joinToString(" · ")
