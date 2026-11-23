package app.naviamp.domain.smartplaylist

/** A deliberately small set of familiar headings for scanning the field catalog. */
enum class SmartPlaylistFieldCategory { Common, Track, AlbumAndArtist, Activity, Dates, AudioAndFile, Identifiers, Other }

data class SmartPlaylistFieldChoiceGroup(
    val category: SmartPlaylistFieldCategory,
    val fields: List<SmartPlaylistFieldOption>,
)

val SmartPlaylistFieldOption.category: SmartPlaylistFieldCategory
    get() = when (field) {
        SmartPlaylistFields.Title, SmartPlaylistFields.SortTitle, SmartPlaylistFields.Genre,
        SmartPlaylistFields.TrackNumber, SmartPlaylistFields.DiscNumber, SmartPlaylistFields.DiscSubtitle,
        SmartPlaylistFields.Comment, SmartPlaylistFields.Lyrics, SmartPlaylistFields.Bpm,
        SmartPlaylistFields.ExplicitStatus, SmartPlaylistFields.Year, SmartPlaylistFields.OriginalYear,
        SmartPlaylistFields.ReleaseYear, SmartPlaylistFields.Compilation,
        -> SmartPlaylistFieldCategory.Track
        SmartPlaylistFields.Artist, SmartPlaylistFields.Album, SmartPlaylistFields.SortArtist,
        SmartPlaylistFields.SortAlbum, SmartPlaylistFields.SortAlbumArtist, SmartPlaylistFields.AlbumComment,
        SmartPlaylistFields.CatalogNumber, SmartPlaylistFields.AlbumDuration, SmartPlaylistFields.AlbumSongCount,
        SmartPlaylistFields.AlbumSize, SmartPlaylistFields.HasCoverArt,
        -> SmartPlaylistFieldCategory.AlbumAndArtist
        SmartPlaylistFields.Loved, SmartPlaylistFields.Rating, SmartPlaylistFields.AverageRating,
        SmartPlaylistFields.PlayCount, SmartPlaylistFields.AlbumRating, SmartPlaylistFields.AlbumLoved,
        SmartPlaylistFields.AlbumPlayCount, SmartPlaylistFields.ArtistRating, SmartPlaylistFields.ArtistLoved,
        SmartPlaylistFields.ArtistPlayCount,
        -> SmartPlaylistFieldCategory.Activity
        SmartPlaylistFields.Size, SmartPlaylistFields.Duration, SmartPlaylistFields.Bitrate,
        SmartPlaylistFields.BitDepth, SmartPlaylistFields.SampleRate, SmartPlaylistFields.Channels,
        SmartPlaylistFields.Codec, SmartPlaylistFields.FilePath, SmartPlaylistFields.FileType,
        SmartPlaylistFields.Missing, SmartPlaylistFields.ReplayGainAlbumGain, SmartPlaylistFields.ReplayGainAlbumPeak,
        SmartPlaylistFields.ReplayGainTrackGain, SmartPlaylistFields.ReplayGainTrackPeak,
        -> SmartPlaylistFieldCategory.AudioAndFile
        SmartPlaylistFields.MusicBrainzAlbumId, SmartPlaylistFields.MusicBrainzAlbumArtistId,
        SmartPlaylistFields.MusicBrainzArtistId, SmartPlaylistFields.MusicBrainzRecordingId,
        SmartPlaylistFields.MusicBrainzReleaseTrackId, SmartPlaylistFields.MusicBrainzReleaseGroupId,
        -> SmartPlaylistFieldCategory.Identifiers
        SmartPlaylistFields.LibraryId, SmartPlaylistFields.Random, "id" -> SmartPlaylistFieldCategory.Other
        else -> if (valueType == SmartPlaylistValueType.Date) SmartPlaylistFieldCategory.Dates
            else SmartPlaylistFieldCategory.Track
    }

/** Stable, untranslated search terms: the provider field key plus common synonyms. */
val SmartPlaylistFieldOption.searchAliases: List<String>
    get() = listOf(field, label) + when (field) {
        SmartPlaylistFields.Loved -> listOf("loved", "starred", "heart")
        SmartPlaylistFields.DateLoved -> listOf("loved", "starred")
        SmartPlaylistFields.AlbumLoved, SmartPlaylistFields.ArtistLoved -> listOf("loved", "starred")
        SmartPlaylistFields.Rating, SmartPlaylistFields.AlbumRating, SmartPlaylistFields.ArtistRating,
        SmartPlaylistFields.AverageRating -> listOf("stars")
        SmartPlaylistFields.PlayCount, SmartPlaylistFields.AlbumPlayCount, SmartPlaylistFields.ArtistPlayCount -> listOf("plays")
        SmartPlaylistFields.LastPlayed -> listOf("recently played", "played")
        SmartPlaylistFields.DateAdded -> listOf("added", "new")
        SmartPlaylistFields.Bpm -> listOf("tempo", "beats per minute")
        SmartPlaylistFields.Duration, SmartPlaylistFields.AlbumDuration -> listOf("length", "time")
        SmartPlaylistFields.FilePath -> listOf("path", "folder", "location")
        SmartPlaylistFields.FileType, SmartPlaylistFields.Codec -> listOf("format", "extension")
        SmartPlaylistFields.ExplicitStatus -> listOf("explicit", "clean", "parental advisory")
        SmartPlaylistFields.HasCoverArt -> listOf("artwork", "cover")
        SmartPlaylistFields.Compilation -> listOf("various artists")
        SmartPlaylistFields.Random -> listOf("shuffle")
        SmartPlaylistFields.ReplayGainAlbumGain, SmartPlaylistFields.ReplayGainAlbumPeak,
        SmartPlaylistFields.ReplayGainTrackGain, SmartPlaylistFields.ReplayGainTrackPeak -> listOf("loudness", "rg")
        SmartPlaylistFields.MusicBrainzAlbumId, SmartPlaylistFields.MusicBrainzAlbumArtistId,
        SmartPlaylistFields.MusicBrainzArtistId, SmartPlaylistFields.MusicBrainzRecordingId,
        SmartPlaylistFields.MusicBrainzReleaseTrackId, SmartPlaylistFields.MusicBrainzReleaseGroupId -> listOf("mbid")
        "id" -> listOf("playlist", "membership")
        else -> emptyList()
    }

/**
 * Groups field choices for a picker. A blank query leads with the common fields in their curated
 * order; a search ranks label prefix matches above label and alias substring matches.
 */
fun smartPlaylistFieldChoices(
    fields: List<SmartPlaylistFieldOption>,
    query: String,
    commonFields: List<String>,
    label: (SmartPlaylistFieldOption) -> String,
): List<SmartPlaylistFieldChoiceGroup> {
    val needle = query.searchKey()
    if (needle.isEmpty()) {
        val common = commonFields.mapNotNull { key -> fields.firstOrNull { it.field == key } }
        val rest = fields.filterNot { it in common }.sortedBy { label(it).lowercase() }
        return listOf(SmartPlaylistFieldChoiceGroup(SmartPlaylistFieldCategory.Common, common))
            .plus(rest.groupedByCategory())
            .filter { it.fields.isNotEmpty() }
    }
    val ranked = fields.mapNotNull { option ->
        val name = label(option).searchKey()
        val rank = when {
            name.startsWith(needle) -> 0
            name.split(' ').any { it.startsWith(needle) } -> 1
            needle in name -> 2
            option.searchAliases.any { needle in it.searchKey() || needle.compact() in it.searchKey().compact() } -> 3
            else -> return@mapNotNull null
        }
        option to rank
    }.sortedWith(compareBy<Pair<SmartPlaylistFieldOption, Int>> { it.second }.thenBy { label(it.first).lowercase() })
    return ranked.map { it.first }.groupedByCategory()
}

private fun List<SmartPlaylistFieldOption>.groupedByCategory(): List<SmartPlaylistFieldChoiceGroup> =
    SmartPlaylistFieldCategory.entries.mapNotNull { category ->
        filter { it.category == category }.takeIf { it.isNotEmpty() }?.let { SmartPlaylistFieldChoiceGroup(category, it) }
    }

private fun String.searchKey(): String = trim().lowercase().replace('_', ' ').replace(Regex("\\s+"), " ")

private fun String.compact(): String = replace(" ", "")
