package app.naviamp.domain.smartplaylist

enum class SmartPlaylistMoveDirection(val offset: Int) { Up(-1), Down(1) }

/** Movement never crosses a logical container or changes any row's identity/value. */
fun SmartPlaylistDraft.moveCondition(
    conditionId: Long,
    direction: SmartPlaylistMoveDirection,
    groupId: Long? = null,
): SmartPlaylistDraft = if (groupId == null) {
    copy(conditions = conditions.moveRow(conditionId, direction) { it.id })
} else {
    copy(groups = groups.map { group ->
        if (group.id != groupId) group else
            group.copy(conditions = group.conditions.moveRow(conditionId, direction) { it.id })
    })
}

fun SmartPlaylistDraft.moveGroup(groupId: Long, direction: SmartPlaylistMoveDirection): SmartPlaylistDraft =
    copy(groups = groups.moveRow(groupId, direction) { it.id })

fun SmartPlaylistDraft.moveSort(sortId: Long, direction: SmartPlaylistMoveDirection): SmartPlaylistDraft =
    copy(sort = sort.moveRow(sortId, direction) { it.id })

private fun <T> List<T>.moveRow(id: Long, direction: SmartPlaylistMoveDirection, rowId: (T) -> Long): List<T> {
    val from = indexOfFirst { rowId(it) == id }
    val to = from + direction.offset
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
