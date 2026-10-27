package app.naviamp.domain.provider

/** Discovery identities are deliberately separate from playable Library artist/album/track IDs. */
data class ProviderDiscoveryArtist(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val genres: List<String> = emptyList(),
    val matchPercent: Int? = null,
    val externalUrl: String? = null,
    val detailsAvailable: Boolean = false,
)

enum class ProviderDiscoveryStatus {
    Ready, Building, Unsupported, PasswordRequired, SignInRequired, Forbidden, Unavailable,
}

data class ProviderDiscoveryFeed(
    val status: ProviderDiscoveryStatus = ProviderDiscoveryStatus.Ready,
    val recommendations: List<ProviderDiscoveryArtist> = emptyList(),
    val trending: List<ProviderDiscoveryArtist> = emptyList(),
    val lastUpdated: String? = null,
)

/** Optional native discovery; failure never disables the provider's standard playback contract. */
fun interface ProviderDiscovery {
    suspend fun load(): ProviderDiscoveryFeed
    suspend fun artistDetails(artist: ProviderDiscoveryArtist): ProviderDiscoveryArtistResult =
        ProviderDiscoveryArtistResult(ProviderDiscoveryStatus.Unsupported)
    suspend fun samples(artist: ProviderDiscoveryArtist): ProviderDiscoverySamples = ProviderDiscoverySamples()
    suspend fun requestTrack(artist: ProviderDiscoveryArtist, sample: ProviderDiscoverySample): ProviderDiscoveryOperation = ProviderDiscoveryOperation()
    suspend fun requestAlbum(artist: ProviderDiscoveryArtist, release: ProviderDiscoveryRelease): ProviderDiscoveryOperation = ProviderDiscoveryOperation()
    suspend fun setMonitoring(artist: ProviderDiscoveryArtist, enabled: Boolean): ProviderDiscoveryOperation = ProviderDiscoveryOperation()
    suspend fun toggleFeedback(artist: ProviderDiscoveryArtist, action: ProviderDiscoveryFeedbackAction): ProviderDiscoveryFeedbackResult =
        ProviderDiscoveryFeedbackResult(ProviderDiscoveryStatus.Unsupported)
}

enum class ProviderDiscoveryFeedbackAction { MoreLikeThis, LessLikeThis, BlockArtist }

data class ProviderDiscoveryRelease(
    val id: String,
    val title: String,
    val releaseDate: String? = null,
    val type: String? = null,
    val secondaryTypes: List<String> = emptyList(),
    val imageUrl: String? = null,
) {
    val section: app.naviamp.domain.media.AlbumReleaseSection
        get() = app.naviamp.domain.media.albumReleaseSection(listOfNotNull(type) + secondaryTypes)
}

data class ProviderDiscoveryArtistDetails(
    val artist: ProviderDiscoveryArtist,
    val biography: String? = null,
    val releases: List<ProviderDiscoveryRelease> = emptyList(),
    val libraryArtist: app.naviamp.domain.Artist? = null,
    val canRequestTracks: Boolean = false,
    val canRequestAlbums: Boolean = false,
    val canChangeMonitoring: Boolean = false,
    val monitored: Boolean? = null,
    val feedbackAvailable: Boolean = false,
    val feedback: Set<ProviderDiscoveryFeedbackAction> = emptySet(),
)

data class ProviderDiscoveryArtistResult(
    val status: ProviderDiscoveryStatus,
    val details: ProviderDiscoveryArtistDetails? = null,
)

data class ProviderDiscoveryFeedbackResult(
    val status: ProviderDiscoveryStatus,
    val selected: Set<ProviderDiscoveryFeedbackAction> = emptySet(),
)

/** A sample's Deezer identity is never a Library track identity. */
data class ProviderDiscoverySample(val id: String, val title: String, val albumTitle: String? = null, val imageUrl: String? = null) {
    fun playbackTrack(artistName: String) = app.naviamp.domain.Track(
        id = app.naviamp.domain.TrackId(DiscoveryPreviewTrackPrefix + id), title = title,
        artistName = artistName, albumTitle = albumTitle, durationSeconds = null,
        coverArtId = imageUrl, audioInfo = null, replayGain = null,
    )
}
const val DiscoveryPreviewTrackPrefix = "discovery-preview:"
fun app.naviamp.domain.TrackId.isDiscoveryPreview(): Boolean = value.startsWith(DiscoveryPreviewTrackPrefix)
data class ProviderDiscoverySamples(val status: ProviderDiscoveryStatus = ProviderDiscoveryStatus.Unsupported, val tracks: List<ProviderDiscoverySample> = emptyList())
enum class ProviderDiscoveryOperationStatus { Queued, AlreadyQueued, AlreadyOwned, Saved, MonitoringEnabled, MonitoringDisabled, MissingSource, Forbidden, Unsupported, Unavailable, Uncertain }
data class ProviderDiscoveryOperation(val status: ProviderDiscoveryOperationStatus = ProviderDiscoveryOperationStatus.Unsupported)
sealed interface ProviderDiscoveryAction {
    data class PlaySample(val id: String) : ProviderDiscoveryAction
    data class RequestTrack(val id: String) : ProviderDiscoveryAction
    data class RequestAlbum(val id: String) : ProviderDiscoveryAction
    data class SetMonitoring(val enabled: Boolean) : ProviderDiscoveryAction
}
