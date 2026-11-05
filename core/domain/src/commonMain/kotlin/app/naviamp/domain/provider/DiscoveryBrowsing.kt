package app.naviamp.domain.provider

import kotlin.time.Instant

enum class DiscoveryBrowsePage { Recommendations, Search }
enum class DiscoveryRecommendationView { ForYou, All }
enum class DiscoveryRecommendationSort { Rank, Name, FirstSeen }
enum class DiscoveryRecommendationFilter { All, New, Direct, Further }

sealed interface DiscoveryBrowseAction {
    data class Open(val page: DiscoveryBrowsePage) : DiscoveryBrowseAction
    data class Query(val query: String) : DiscoveryBrowseAction
    data class Scope(val scope: ProviderCatalogScope) : DiscoveryBrowseAction
    data object Search : DiscoveryBrowseAction
    data class Album(val album: ProviderCatalogAlbum) : DiscoveryBrowseAction
    data object External : DiscoveryBrowseAction
    data object Close : DiscoveryBrowseAction
    data object Refresh : DiscoveryBrowseAction
    data object More : DiscoveryBrowseAction
    data class View(val view: DiscoveryRecommendationView) : DiscoveryBrowseAction
    data class Sort(val sort: DiscoveryRecommendationSort) : DiscoveryBrowseAction
    data class Filter(val filter: DiscoveryRecommendationFilter) : DiscoveryBrowseAction
    data class Genre(val genre: String?) : DiscoveryBrowseAction
    data class Seed(val seed: String?) : DiscoveryBrowseAction
    data class Viewport(val index: Int, val offset: Int) : DiscoveryBrowseAction
}

data class DiscoveryBrowserState(
    val page: DiscoveryBrowsePage = DiscoveryBrowsePage.Recommendations,
    val query: String = "",
    val submittedQuery: String = "",
    val scope: ProviderCatalogScope = ProviderCatalogScope.Artist,
    val catalog: ProviderCatalogPage = ProviderCatalogPage(),
    val recommendations: List<ProviderDiscoveryArtist> = emptyList(),
    val seeds: List<ProviderDiscoverySeed> = emptyList(),
    val genres: List<String> = emptyList(),
    val lastUpdated: String? = null,
    val runId: String? = null,
    val total: Int? = null,
    val nextOffset: Int? = 0,
    val loading: Boolean = false,
    val status: ProviderDiscoveryStatus = ProviderDiscoveryStatus.Ready,
    val building: Boolean = false,
    val view: DiscoveryRecommendationView = DiscoveryRecommendationView.ForYou,
    val sort: DiscoveryRecommendationSort = DiscoveryRecommendationSort.Rank,
    val filter: DiscoveryRecommendationFilter = DiscoveryRecommendationFilter.All,
    val genre: String? = null,
    val seed: String? = null,
    val firstVisibleItem: Int = 0,
    val firstVisibleOffset: Int = 0,
    val nowMillis: Long = 0L,
) {
    val hasDepth: Boolean get() = recommendations.any { it.recommendation?.depth != null }
    val hasDates: Boolean get() = recommendations.any { it.firstSeenMillis() != null }
    val newCutoff: Long get() = recommendations.mapNotNull { it.firstSeenMillis() }.minOrNull()?.let {
        maxOf(it + 1, nowMillis - 7L * 24 * 60 * 60 * 1000)
    } ?: Long.MAX_VALUE
    fun isNew(artist: ProviderDiscoveryArtist) = artist.firstSeenMillis()?.let { it >= newCutoff } == true
    fun likedSeeds(artist: ProviderDiscoveryArtist): List<String> =
        (artist.recommendation?.supportingSeeds.orEmpty() + artist.recommendation?.sourceArtists.orEmpty()).distinct()
            .filter { name -> seeds.any { it.name == name } }
    fun filtered(): List<ProviderDiscoveryArtist> = recommendations.filter { artist ->
        (genre == null || (artist.genres + artist.recommendation?.matchedTags.orEmpty()).any { it.equals(genre, true) }) &&
        (seed == null || (artist.recommendation?.depth ?: 1) < 2 && seed in likedSeeds(artist)) && when (filter) {
            DiscoveryRecommendationFilter.All -> true
            DiscoveryRecommendationFilter.New -> isNew(artist)
            DiscoveryRecommendationFilter.Direct -> artist.recommendation?.depth == 1
            DiscoveryRecommendationFilter.Further -> (artist.recommendation?.depth ?: 0) >= 2
        }
    }.let { rows -> when (sort) {
        DiscoveryRecommendationSort.Rank -> rows.sortedBy { it.recommendation?.rank ?: Int.MAX_VALUE }
        DiscoveryRecommendationSort.Name -> rows.sortedBy { it.name.lowercase() }
        DiscoveryRecommendationSort.FirstSeen -> rows.sortedByDescending { it.firstSeenMillis() ?: Long.MIN_VALUE }
    } }
    /** Each pick belongs to one section; absent metadata never invents new/direct status. */
    fun groups(): List<Pair<DiscoveryRecommendationGroup, List<ProviderDiscoveryArtist>>> {
        val rows = filtered(); val used = mutableSetOf<String>()
        fun take(candidates: List<ProviderDiscoveryArtist>) = candidates.filter { used.add(it.id) }
        return buildList {
            rows.firstOrNull()?.let { add(DiscoveryRecommendationGroup(DiscoveryRecommendationGroupKind.Top) to take(listOf(it))) }
            if (hasDates) add(DiscoveryRecommendationGroup(DiscoveryRecommendationGroupKind.New) to take(rows.filter(::isNew)))
            seeds.map { it.name }.map { seed -> seed to rows.filter {
                (it.recommendation?.depth ?: 1) == 1 && likedSeeds(it).firstOrNull() == seed && it.id !in used
            } }.filter { it.second.size >= 4 }.sortedByDescending { it.second.size }.take(3).forEach { (seed, picks) ->
                add(DiscoveryRecommendationGroup(DiscoveryRecommendationGroupKind.Seed, seed) to take(picks))
            }
            if (hasDepth) {
                add(DiscoveryRecommendationGroup(DiscoveryRecommendationGroupKind.Direct) to take(rows.filter { it.recommendation?.depth == 1 }))
                add(DiscoveryRecommendationGroup(DiscoveryRecommendationGroupKind.Further) to take(rows.filter { (it.recommendation?.depth ?: 0) >= 2 }))
            }
            add(DiscoveryRecommendationGroup(DiscoveryRecommendationGroupKind.Other) to take(rows))
        }.filter { it.second.isNotEmpty() }
    }
}
enum class DiscoveryRecommendationGroupKind { Top, New, Seed, Direct, Further, Other }
data class DiscoveryRecommendationGroup(val kind: DiscoveryRecommendationGroupKind, val seed: String? = null)
private fun ProviderDiscoveryArtist.firstSeenMillis(): Long? = recommendation?.firstDiscoveredAt?.let {
    runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull()
}
