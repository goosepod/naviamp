package app.naviamp.domain.library

fun nextLibraryLimit(
    visibleCount: Int,
    currentLimit: Int,
    pageSize: Int,
): Int =
    if (visibleCount < currentLimit) currentLimit else currentLimit + pageSize

fun libraryLimitForOffset(offset: Int, pageSize: Int): Int =
    ((offset / pageSize) + 1) * pageSize

/** The quick index has A–Z groups and a separate group for numbers and symbols. */
fun libraryTitleLetter(title: String): Char =
    title.trimStart().firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

fun libraryLetterJumpIndex(titles: List<String>, letter: Char): Int {
    if (titles.isEmpty()) return -1
    val requested = letter.uppercaseChar()
    if (requested == '#') return 0
    val groups = titles.map(::libraryTitleLetter)
    val target = groups.filter { it in requested..'Z' }.minOrNull() ?: return -1
    return groups.indexOf(target)
}
