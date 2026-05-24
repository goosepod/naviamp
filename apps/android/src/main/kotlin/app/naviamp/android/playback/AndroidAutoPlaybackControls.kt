package app.naviamp.android.playback

object AndroidAutoPlaybackControls {
    @Volatile
    var onPlayMediaId: ((String) -> Unit)? = null

    fun clear() {
        onPlayMediaId = null
    }

    const val MediaIdRoot = "naviamp.root"
    const val MediaIdNowPlaying = "naviamp.now_playing"
    const val MediaIdLibrary = "naviamp.library"
    const val MediaIdLibraryArtists = "naviamp.library.artists"
    const val MediaIdLibraryAlbums = "naviamp.library.albums"
    const val MediaIdLibraryTracks = "naviamp.library.tracks"
    const val MediaIdRadio = "naviamp.radio"
    const val MediaIdRadioLibrary = "naviamp.radio.library"
    const val MediaIdDownloads = "naviamp.downloads"

    const val MediaIdArtistPrefix = "naviamp.artist:"
    const val MediaIdAlbumPrefix = "naviamp.album:"
    const val MediaIdTrackPrefix = "naviamp.track:"
    const val MediaIdDownloadPrefix = "naviamp.download:"
}
