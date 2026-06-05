package app.naviamp.desktop

import app.naviamp.domain.Track
import app.naviamp.domain.cache.TrackMetadataRepository
import app.naviamp.storage.NaviampStorageQueries
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DesktopTrackMetadataStore(
    private val queries: NaviampStorageQueries,
    private val json: Json,
) : TrackMetadataRepository {
    override fun updateTrack(updatedTrack: Track) {
        queries.transaction {
            val albumRows = queries.selectResponsesByType("album").executeAsList()
            albumRows.forEach { row ->
                val details = json.decodeFromString<AlbumDetailsDto>(row.payload).toAlbumDetails()
                val updatedDetails = details.copy(
                    tracks = details.tracks.map { track ->
                        if (track.id == updatedTrack.id) updatedTrack else track
                    },
                )
                if (updatedDetails != details) {
                    queries.updateResponsePayload(
                        payload = json.encodeToString(AlbumDetailsDto.fromAlbumDetails(updatedDetails)),
                        cache_key = row.cache_key,
                    )
                }
            }

            val searchRows = queries.selectResponsesByType("search").executeAsList()
            searchRows.forEach { row ->
                val results = json.decodeFromString<MediaSearchResultsDto>(row.payload).toMediaSearchResults()
                val updatedResults = results.copy(
                    tracks = results.tracks.map { track ->
                        if (track.id == updatedTrack.id) updatedTrack else track
                    },
                )
                if (updatedResults != results) {
                    queries.updateResponsePayload(
                        payload = json.encodeToString(MediaSearchResultsDto.fromMediaSearchResults(updatedResults)),
                        cache_key = row.cache_key,
                    )
                }
            }
        }
    }
}
