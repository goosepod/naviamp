package app.naviamp.domain.popular

import app.naviamp.domain.Artist

class ProviderArtistPopularTracksClient(
    private val clientProvider: () -> ArtistPopularTracksClient?,
    override val source: String = NavidromeAgentMetadataSource,
) : ArtistPopularTracksClient {
    override suspend fun popularTracks(artist: Artist, limit: Int): ArtistPopularTracksResult =
        clientProvider()?.popularTracks(artist, limit)
            ?: ArtistPopularTracksResult(source = source)
}

class ProviderSimilarArtistsClient(
    private val clientProvider: () -> SimilarArtistsClient?,
) : SimilarArtistsClient {
    override suspend fun similarArtists(artist: Artist, limit: Int): List<SimilarArtistCandidate> =
        clientProvider()?.similarArtists(artist, limit).orEmpty()
}
