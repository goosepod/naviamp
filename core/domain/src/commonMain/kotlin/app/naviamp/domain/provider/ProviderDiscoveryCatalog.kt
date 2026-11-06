package app.naviamp.domain.provider

private val DiscoveryMbid = Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")

/** These namespaces intentionally have no conversion to playable Library IDs. */
data class DiscoveryArtistMbid(val value: String) { init { require(DiscoveryMbid.matches(value)) } }
data class DiscoveryReleaseGroupMbid(val value: String) { init { require(DiscoveryMbid.matches(value)) } }
data class DiscoveryRecordingMbid(val value: String) { init { require(DiscoveryMbid.matches(value)) } }
data class DiscoveryDeezerPreviewId(val value: String) { init { require(value.toLongOrNull()?.let { it > 0 } == true) } }
data class DiscoveryCanonicalAlbumId(val value: String) { init { require(value.toLongOrNull()?.let { it > 0 } == true) } }
data class DiscoveryCanonicalArtistId(val value: String) { init { require(value.toLongOrNull()?.let { it > 0 } == true) } }
data class DiscoveryJobId(val value: String) { init { require(value.isNotBlank()) } }
data class DiscoveryRequestGroupId(val value: String) { init { require(value.isNotBlank()) } }
enum class DiscoveryLibraryManager { Aurral, Lidarr }
data class DiscoveryManagedAlbumId(val manager: DiscoveryLibraryManager, val value: String) {
    init { require(value.toLongOrNull()?.let { it > 0 } == true) }
}

val ProviderDiscoveryArtist.artistMbid: DiscoveryArtistMbid?
    get() = id.takeIf(DiscoveryMbid::matches)?.let(::DiscoveryArtistMbid)
val ProviderDiscoveryRelease.releaseGroupMbid: DiscoveryReleaseGroupMbid?
    get() = id.takeIf(DiscoveryMbid::matches)?.let(::DiscoveryReleaseGroupMbid)

/** Correlation only: metadata saved, queued, and available in Subsonic are separate facts. */
data class ProviderDiscoveryRequestIdentity(
    val requestGroupId: DiscoveryRequestGroupId? = null,
    val jobIds: List<DiscoveryJobId> = emptyList(),
    val canonicalAlbumId: DiscoveryCanonicalAlbumId? = null,
    val managedAlbumId: DiscoveryManagedAlbumId? = null,
)

data class ProviderRecommendationContext(
    val rank: Int? = null,
    val matchedTags: List<String> = emptyList(),
    val sourceArtists: List<String> = emptyList(),
    val supportingSeeds: List<String> = emptyList(),
    val sourceType: String? = null,
    val tier: String? = null,
    // Null means unavailable. Older servers must not invent direct/new semantics.
    val depth: Int? = null,
    val firstDiscoveredAt: String? = null,
)

data class ProviderDiscoverySeed(val name: String, val mbid: DiscoveryArtistMbid? = null)

/** Offset advances by raw server rows, before invalid-row filtering or deduplication. */
data class ProviderRecommendationPage(
    val status: ProviderDiscoveryStatus = ProviderDiscoveryStatus.Unsupported,
    val artists: List<ProviderDiscoveryArtist> = emptyList(),
    val offset: Int = 0,
    val nextOffset: Int? = null,
    val totalCount: Int? = null,
    val seeds: List<ProviderDiscoverySeed> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val lastUpdated: String? = null,
    val runId: String? = null,
)

enum class ProviderCatalogScope { Artist, Album }
data class ProviderCatalogAlbum(
    val release: ProviderDiscoveryRelease,
    val artistName: String,
    val artistMbid: DiscoveryArtistMbid? = null,
    // Server hints are not validated playable identities.
    val inLibrary: Boolean? = null,
    val availability: String? = null,
)
data class ProviderCatalogPage(
    val status: ProviderDiscoveryStatus = ProviderDiscoveryStatus.Unsupported,
    val artists: List<ProviderDiscoveryArtist> = emptyList(),
    val albums: List<ProviderCatalogAlbum> = emptyList(),
    val offset: Int = 0,
    val nextOffset: Int? = null,
    val totalCount: Int? = null,
)

data class ProviderDiscoveryReleaseTrack(
    val recordingMbid: DiscoveryRecordingMbid?,
    val title: String,
    val discNumber: Int?,
    val trackNumber: Int?,
    val durationMillis: Long?,
    val previewId: DiscoveryDeezerPreviewId? = null,
)
data class ProviderDiscoveryReleaseTracks(
    val status: ProviderDiscoveryStatus = ProviderDiscoveryStatus.Unsupported,
    val tracks: List<ProviderDiscoveryReleaseTrack> = emptyList(),
)

data class ProviderDiscoveryArtistCredit(val name: String, val mbid: DiscoveryArtistMbid? = null)
data class ProviderDiscoveryReleaseDetails(
    val status: ProviderDiscoveryStatus = ProviderDiscoveryStatus.Unsupported,
    val release: ProviderDiscoveryRelease? = null,
    val credits: List<ProviderDiscoveryArtistCredit> = emptyList(),
    val overview: String? = null,
    val genres: List<String> = emptyList(),
)

/** Server activity is not proof that an imported track is available through the playback provider. */
data class ProviderDiscoveryActivity(
    val id: String,
    val status: String?,
    val identity: ProviderDiscoveryRequestIdentity?,
    val releaseGroupMbid: DiscoveryReleaseGroupMbid? = null,
)
data class ProviderDiscoveryActivities(
    val status: ProviderDiscoveryStatus = ProviderDiscoveryStatus.Unsupported,
    val items: List<ProviderDiscoveryActivity> = emptyList(),
)
