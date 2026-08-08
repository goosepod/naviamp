package app.naviamp.domain.navibeat

import app.naviamp.domain.Playlist
import app.naviamp.domain.home.HomeDate

data class NavibeatMix(
    val playlist: Playlist,
    val metadata: NavibeatMixMetadata,
)

data class NavibeatMixMetadata(
    val kind: String,
    val slot: String,
    val generatedOn: NavibeatMixDate,
    val mode: String,
    val trackCount: Int,
    val description: String,
)

data class NavibeatMixDate(
    val year: Int,
    val month: Int,
    val day: Int,
) {
    val iso8601: String
        get() = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

data class NavibeatPlaylistPartition(
    val ordinary: List<Playlist>,
    val mixes: List<NavibeatMix>,
)

fun Playlist.navibeatMixOrNull(): NavibeatMix? {
    val lines = comment
        ?.replace("\r\n", "\n")
        ?.replace('\r', '\n')
        ?.lines()
        ?: return null
    val markerLines = lines.map(String::trim).filter { it.startsWith("nb1:") }
    if (markerLines.size != 1) return null
    val fields = markerLines.single().split(':')
    if (fields.size != NavibeatMarkerFieldCount || fields.first() != NavibeatSchemaVersion) return null

    val kind = fields[1].takeIf(::isValidNavibeatIdentifier) ?: return null
    val slot = fields[2].takeIf(::isValidNavibeatIdentifier) ?: return null
    val generatedOn = parseNavibeatDate(fields[3]) ?: return null
    val mode = fields[4].takeIf(::isValidNavibeatIdentifier) ?: return null
    val markerTrackCount = fields[5].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val markerIndex = lines.indexOfFirst { it.trim() == markerLines.single() }
    val description = lines
        .take(markerIndex)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filterNot { it == NavibeatAttribution }
        .joinToString("\n")

    return NavibeatMix(
        playlist = this,
        metadata = NavibeatMixMetadata(
            kind = kind,
            slot = slot,
            generatedOn = generatedOn,
            mode = mode,
            trackCount = markerTrackCount,
            description = description,
        ),
    )
}

fun List<Playlist>.partitionNavibeatMixes(): NavibeatPlaylistPartition {
    val ordinary = mutableListOf<Playlist>()
    val mixes = mutableListOf<NavibeatMix>()
    forEach { playlist ->
        playlist.navibeatMixOrNull()?.let(mixes::add) ?: ordinary.add(playlist)
    }
    return NavibeatPlaylistPartition(ordinary = ordinary, mixes = mixes)
}

fun List<NavibeatMix>.prioritizedForHour(hourOfDay: Int): List<NavibeatMix> {
    val currentSlot = navibeatTimeSlotForHour(hourOfDay)
    return withIndex()
        .sortedWith(compareBy<IndexedValue<NavibeatMix>> { it.value.metadata.slot != currentSlot }.thenBy { it.index })
        .map(IndexedValue<NavibeatMix>::value)
}

fun navibeatTimeSlotForHour(hourOfDay: Int): String = when (hourOfDay.coerceIn(0, 23)) {
    in 5..10 -> "morning"
    in 11..16 -> "afternoon"
    in 17..22 -> "evening"
    else -> "night"
}

fun NavibeatMix.statusLabel(currentDate: HomeDate): String {
    if (metadata.mode == NavibeatFallbackMode) return "Still learning you"
    val generatedDay = metadata.generatedOn.dayOfYearOrNull() ?: return "Updated ${metadata.generatedOn.iso8601}"
    val difference = if (metadata.generatedOn.year == currentDate.year) {
        currentDate.dayOfYear - generatedDay
    } else {
        Int.MAX_VALUE
    }
    return when (difference) {
        0 -> "Updated today"
        1 -> "Updated yesterday"
        else -> "Updated ${metadata.generatedOn.iso8601}"
    }
}

private fun parseNavibeatDate(value: String): NavibeatMixDate? {
    val fields = value.split('-')
    if (fields.size != 3 || fields[0].length != 4 || fields[1].length != 2 || fields[2].length != 2) return null
    val date = NavibeatMixDate(
        year = fields[0].toIntOrNull() ?: return null,
        month = fields[1].toIntOrNull() ?: return null,
        day = fields[2].toIntOrNull() ?: return null,
    )
    return date.takeIf { it.dayOfYearOrNull() != null }
}

private fun NavibeatMixDate.dayOfYearOrNull(): Int? {
    if (month !in 1..12) return null
    val monthLengths = intArrayOf(31, if (year.isLeapYear()) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    if (day !in 1..monthLengths[month - 1]) return null
    return monthLengths.take(month - 1).sum() + day
}

private fun Int.isLeapYear(): Boolean = this % 4 == 0 && (this % 100 != 0 || this % 400 == 0)

private fun isValidNavibeatIdentifier(value: String): Boolean =
    value.isNotEmpty() && value.all { it.isLowerCase() || it.isDigit() || it == '-' }

private const val NavibeatSchemaVersion = "nb1"
private const val NavibeatMarkerFieldCount = 6
private const val NavibeatAttribution = "Made by NaviBeat  ·  navibeat.app"
private const val NavibeatFallbackMode = "fallback"
