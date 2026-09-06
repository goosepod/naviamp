package app.naviamp.domain.genremix

import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaPage
import app.naviamp.domain.provider.MediaPageRequest

/** Pages the ontology's source tags without broad text searches or random enumeration. */
class GenreTrackBrowser(
    genres: List<Genre>,
    private val load: suspend (String, MediaPageRequest) -> MediaPage<Track>?,
) {
    private val names = genres.map { it.name }.distinctBy { it.lowercase() }
    private var genreIndex = 0
    private var request = MediaPageRequest()
    var tracks: List<Track> = emptyList()
        private set
    val hasMore: Boolean get() = genreIndex < names.size

    suspend fun loadNext() {
        if (!hasMore) return
        val page = load(names[genreIndex], request) ?: throw UnsupportedOperationException()
        val next = page.nextRequest
        check(next == null || next != request)
        tracks = (tracks + page.items).distinctBy { it.id }
        if (next == null) {
            genreIndex++
            request = MediaPageRequest()
        } else request = next
    }
}
