package app.naviamp.ui

import app.naviamp.domain.settings.HomeSectionIds
import app.naviamp.domain.settings.InterfaceSettings

private const val TelevisionHomeMaximumItemsPerRail = 30
private val TelevisionUnsupportedHomeSectionIds = setOf(HomeSectionIds.MixBuilders)

/**
 * Adapts every available shared Home section to the ten-foot carousel presentation.
 *
 * The input is already ordered by the user's shared Home settings. Standard Home layout choices
 * are intentionally ignored because TV consistently presents sections as horizontal rails.
 */
internal fun televisionHomeSections(
    sections: List<SharedHomeCollectionSectionUi>,
): List<SharedHomeCollectionSectionUi> {
    return sections.filter {
        it.id !in TelevisionUnsupportedHomeSectionIds && it.visible && it.items.isNotEmpty()
    }.map { section ->
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

internal fun InterfaceSettings.televisionHomeSectionOptions(): List<HomeScreenSectionOption> =
    orderedHomeScreenSectionOptions().filter { it.id !in TelevisionUnsupportedHomeSectionIds }

internal fun InterfaceSettings.withOrderedTelevisionHomeSections(
    orderedTelevisionSections: List<HomeScreenSectionOption>,
): InterfaceSettings {
    val televisionSections = orderedTelevisionSections.iterator()
    val mergedSections = orderedHomeScreenSectionOptions().map { section ->
        if (section.id in TelevisionUnsupportedHomeSectionIds) section else televisionSections.next()
    }
    return withOrderedHomeScreenSections(mergedSections)
}

internal fun televisionHomeMoveScrollAnchor(
    movingIndex: Int,
    visibleIndices: List<Int>,
): Int? {
    if (movingIndex < 0 || visibleIndices.isEmpty()) return null
    val visibleRange = visibleIndices.first()..visibleIndices.last()
    return when {
        movingIndex in visibleRange -> null
        movingIndex < visibleRange.first -> movingIndex
        else -> (movingIndex - visibleIndices.size + 1).coerceAtLeast(0)
    }
}
