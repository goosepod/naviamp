package app.naviamp.domain.settings

import kotlinx.serialization.Serializable

enum class AlbumArtworkPreference {
    Inherit,
    Track,
    Album,
}

fun AlbumArtworkPreference.resolvePreferTrack(globalPreference: Boolean): Boolean = when (this) {
    AlbumArtworkPreference.Inherit -> globalPreference
    AlbumArtworkPreference.Track -> true
    AlbumArtworkPreference.Album -> false
}

/** A portable, source-scoped override for one specific album edition. */
@Serializable
data class AlbumArtworkOverride(
    val sourceId: String,
    val albumId: String,
    val preference: String,
) {
    fun normalized(): AlbumArtworkOverride? {
        val source = sourceId.trim().takeIf(String::isNotEmpty) ?: return null
        val album = albumId.trim().takeIf(String::isNotEmpty) ?: return null
        val choice = AlbumArtworkPreference.entries.firstOrNull { it.name == preference }
            ?.takeUnless { it == AlbumArtworkPreference.Inherit } ?: return null
        return copy(sourceId = source, albumId = album, preference = choice.name)
    }
}

fun InterfaceSettings.albumArtworkPreference(sourceId: String?, albumId: String?): AlbumArtworkPreference {
    if (sourceId.isNullOrBlank() || albumId.isNullOrBlank()) return AlbumArtworkPreference.Inherit
    val source = sourceId.trim()
    val album = albumId.trim()
    return albumArtworkOverrides.firstOrNull { it.sourceId == source && it.albumId == album }
        ?.let { override -> AlbumArtworkPreference.entries.firstOrNull { it.name == override.preference } }
        ?: AlbumArtworkPreference.Inherit
}

fun InterfaceSettings.withAlbumArtworkPreference(
    sourceId: String,
    albumId: String,
    preference: AlbumArtworkPreference,
): InterfaceSettings {
    val source = sourceId.trim()
    val album = albumId.trim()
    if (source.isEmpty() || album.isEmpty()) return this
    val remaining = albumArtworkOverrides.filterNot { it.sourceId == source && it.albumId == album }
    return copy(
        albumArtworkOverrides = if (preference == AlbumArtworkPreference.Inherit) remaining else
            remaining + AlbumArtworkOverride(source, album, preference.name),
    ).normalized()
}

fun InterfaceSettings.preferTrackArtwork(sourceId: String?, albumId: String?): Boolean =
    albumArtworkPreference(sourceId, albumId).resolvePreferTrack(nowPlaying.showTrackCover)
