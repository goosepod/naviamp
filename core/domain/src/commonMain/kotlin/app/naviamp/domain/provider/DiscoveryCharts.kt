package app.naviamp.domain.provider

/** Chart keys are provider chart identities, never Library IDs or implicit preview IDs. */
data class DiscoveryChartSong(val key: String, val title: String, val artistName: String,
    val albumTitle: String? = null, val imageUrl: String? = null, val position: Int? = null,
    val movement: Int? = null, val newEntry: Boolean = false, val previewId: DiscoveryDeezerPreviewId? = null)
data class DiscoveryChartAlbum(val key: String, val title: String, val artistName: String,
    val imageUrl: String? = null, val releaseDate: String? = null, val position: Int? = null)
data class DiscoveryChartArtist(val key: String, val artist: ProviderDiscoveryArtist, val position: Int? = null)
data class DiscoveryChartGenre(val id: String, val name: String, val covers: List<String> = emptyList())
data class DiscoveryChartSpotlight(val ranks: List<Int?>, val daysAtTop: Int?)
data class ProviderDiscoveryCharts(
    val status: ProviderDiscoveryStatus = ProviderDiscoveryStatus.Unsupported,
    val region: String? = null, val updatedAt: String? = null,
    val songs: List<DiscoveryChartSong>? = null,
    val albums: List<DiscoveryChartAlbum>? = null,
    val artists: List<DiscoveryChartArtist>? = null,
    val genres: List<DiscoveryChartGenre> = emptyList(),
    val playlistId: String? = null, val libraryPlaylistId: String? = null,
    val canSave: Boolean = false, val spotlight: DiscoveryChartSpotlight? = null,
)
data class ProviderDiscoveryGenreChart(val status: ProviderDiscoveryStatus = ProviderDiscoveryStatus.Unsupported,
    val id: String? = null, val name: String? = null, val region: String? = null,
    val updatedAt: String? = null, val songs: List<DiscoveryChartSong> = emptyList())
data class ProviderDiscoveryChartSave(val status: ProviderDiscoveryOperationStatus = ProviderDiscoveryOperationStatus.Unsupported,
    val playlistId: String? = null)
enum class DiscoveryChartSection { Songs, Albums, Artists, Genres }
