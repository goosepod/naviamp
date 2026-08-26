package app.naviamp.ui

import app.naviamp.domain.settings.HomeSectionIds

private const val TelevisionHomeMaximumItemsPerRail = 30

private val TelevisionHomeRailGroups = listOf(
    setOf(
        HomeSectionIds.RecentRadio,
        HomeSectionIds.RecentlyPlayed,
        HomeSectionIds.RecentInternetRadio,
    ),
    setOf(
        HomeSectionIds.RecentlyAdded,
        HomeSectionIds.RecentAlbums,
    ),
    setOf(HomeSectionIds.SimilarToStarredTracks),
    setOf(
        HomeSectionIds.MixesForYou,
        HomeSectionIds.NavibeatMixes,
        HomeSectionIds.MixBuilders,
        HomeSectionIds.MoreLikeRecentPlays,
    ),
    setOf(HomeSectionIds.RecentPlaylists),
)

/**
 * Selects the small, ordered set of Home rails suitable for a ten-foot interface.
 *
 * The input is already ordered by the user's shared Home settings. That order chooses between
 * equivalent rails inside a TV priority group, while the group order itself remains stable for
 * predictable D-pad navigation. Standard Home layout choices are intentionally ignored.
 */
internal fun televisionHomeSections(
    sections: List<SharedHomeCollectionSectionUi>,
): List<SharedHomeCollectionSectionUi> {
    val eligibleSections = sections.filter { it.visible && it.items.isNotEmpty() }
    return TelevisionHomeRailGroups.mapNotNull { group ->
        eligibleSections.firstOrNull { it.id in group }?.let { section ->
            section.copy(
                items = section.items.take(
                    minOf(
                        section.homeItemLimit ?: TelevisionHomeMaximumItemsPerRail,
                        TelevisionHomeMaximumItemsPerRail,
                    ),
                ),
            )
        }
    }
}
