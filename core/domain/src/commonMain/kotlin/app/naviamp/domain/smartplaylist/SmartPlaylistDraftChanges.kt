package app.naviamp.domain.smartplaylist

/**
 * Whether closing the editor would lose user edits. Row identities are generated, so they are
 * ignored; content and order are what the user changed.
 */
fun SmartPlaylistDraft.hasUnsavedChanges(baseline: SmartPlaylistDraft): Boolean =
    withoutRowIdentities() != baseline.withoutRowIdentities()

private fun SmartPlaylistDraft.withoutRowIdentities(): SmartPlaylistDraft = copy(
    conditions = conditions.map { it.copy(id = 0) },
    groups = groups.map { group -> group.copy(id = 0, conditions = group.conditions.map { it.copy(id = 0) }) },
    sort = sort.map { it.copy(id = 0) },
)
