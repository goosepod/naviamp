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

/** App Actions deliver a structured media name through an explicit Android activity action. */
internal fun androidAppActionNamedMediaRequest(action: String?, extras: Bundle?): NamedMediaRequest? {
    val kind = when (action) {
        AppActionPlayArtistRadio -> NamedMediaKind.ArtistRadio
        AppActionPlayArtist -> NamedMediaKind.Artist
        AppActionPlayAlbum -> NamedMediaKind.Album
        AppActionPlayPlaylist -> NamedMediaKind.Playlist
        else -> return null
    }
    val name = extras?.getString(AppActionMediaName).orEmpty()
    return namedMediaRequest(name, kind, name)
}

internal fun isAndroidNamedMediaAppAction(action: String?): Boolean = when (action) {
    AppActionPlayArtistRadio, AppActionPlayArtist, AppActionPlayAlbum, AppActionPlayPlaylist -> true
    else -> false
}

internal const val AppActionPlayArtistRadio = "app.naviamp.android.action.PLAY_ARTIST_RADIO"
internal const val AppActionPlayArtist = "app.naviamp.android.action.PLAY_ARTIST"
internal const val AppActionPlayAlbum = "app.naviamp.android.action.PLAY_ALBUM"
internal const val AppActionPlayPlaylist = "app.naviamp.android.action.PLAY_PLAYLIST"
internal const val AppActionMediaName = "app.naviamp.android.extra.MEDIA_NAME"
