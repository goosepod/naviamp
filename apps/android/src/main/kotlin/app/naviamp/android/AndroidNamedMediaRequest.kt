package app.naviamp.android

import android.os.Bundle
import android.provider.MediaStore
import app.naviamp.domain.playback.NamedMediaKind
import app.naviamp.domain.playback.NamedMediaRequest
import app.naviamp.domain.playback.namedMediaRequest

/** Translates Android's media-search extras into the host-neutral request. */
internal fun androidNamedMediaRequest(query: String, extras: Bundle?): NamedMediaRequest? {
    val hintedKind = when (extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)) {
        MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> NamedMediaKind.Artist
        MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> NamedMediaKind.Album
        MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE -> NamedMediaKind.Playlist
        else -> null
    }
    val hintedName = when (hintedKind) {
        NamedMediaKind.Artist -> extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)
        NamedMediaKind.Album -> extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)
        NamedMediaKind.Playlist -> extras?.getString(MediaStore.EXTRA_MEDIA_PLAYLIST)
        else -> null
    }
    return namedMediaRequest(query, hintedKind, hintedName)
}
