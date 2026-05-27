package app.naviamp.domain.library

fun nextLibraryLimit(
    visibleCount: Int,
    currentLimit: Int,
    pageSize: Int,
): Int =
    if (visibleCount < currentLimit) currentLimit else currentLimit + pageSize

fun libraryLimitForOffset(offset: Int, pageSize: Int): Int =
    ((offset / pageSize) + 1) * pageSize
