package app.naviamp.android.playback

object AndroidAutoPlaybackControls {
    const val MediaIdRoot = "naviamp.root"
    const val MediaIdNowPlaying = "naviamp.now_playing"
    const val MediaIdQueue = "naviamp.queue"
    const val MediaIdQueuePagePrefix = "naviamp.queue.page:"
    const val MediaIdHome = "naviamp.home"
    const val MediaIdHomeMixes = "naviamp.home.mixes"
    const val MediaIdHomeDjs = "naviamp.home.djs"
    const val MediaIdHomeSmartTemplates = "naviamp.home.smart_templates"
    const val MediaIdHomeRecentPlays = "naviamp.home.recent_plays"
    const val MediaIdHomeRecentlyAdded = "naviamp.home.recently_added"
    const val MediaIdLibrary = "naviamp.library"
    const val MediaIdLibraryArtists = "naviamp.library.artists"
    const val MediaIdLibraryAlbums = "naviamp.library.albums"
    const val MediaIdLibraryTracks = "naviamp.library.tracks"
    const val MediaIdCharts = "naviamp.charts"
    const val MediaIdChartsArtists = "naviamp.charts.artists"
    const val MediaIdChartsAlbums = "naviamp.charts.albums"
    const val MediaIdChartsTracks = "naviamp.charts.tracks"
    const val MediaIdRadio = "naviamp.radio"
    const val MediaIdRadioLibrary = "naviamp.radio.library"
    const val MediaIdRadioStations = "naviamp.radio.stations"
    const val MediaIdRadioRecent = "naviamp.radio.recent"
    const val MediaIdDownloads = "naviamp.downloads"
    const val MediaIdMore = "naviamp.more"
    const val MediaIdPlaylists = "naviamp.playlists"
    const val MediaIdSmartPlaylists = "naviamp.smart_playlists"
    const val MediaIdNoSource = "naviamp.no_source"

    const val MediaIdArtistPrefix = "naviamp.artist:"
    const val MediaIdArtistGroupPrefix = "naviamp.artist.group:"
    const val MediaIdArtistPlayPrefix = "naviamp.artist.play:"
    const val MediaIdArtistTrackPrefix = "naviamp.artist.track:"
    const val MediaIdArtistShufflePrefix = "naviamp.artist.shuffle:"
    const val MediaIdAlbumPrefix = "naviamp.album:"
    const val MediaIdAlbumPlayPrefix = "naviamp.album.play:"
    const val MediaIdTrackPrefix = "naviamp.track:"
    const val MediaIdAlbumTrackPrefix = "naviamp.album.track:"
    const val MediaIdAlbumShufflePrefix = "naviamp.album.shuffle:"
    const val MediaIdQueueTrackPrefix = "naviamp.queue.track:"
    const val MediaIdDownloadPrefix = "naviamp.download:"
    const val MediaIdPlaylistPrefix = "naviamp.playlist:"
    const val MediaIdPlaylistPlayPrefix = "naviamp.playlist.play:"
    const val MediaIdPlaylistShufflePrefix = "naviamp.playlist.shuffle:"
    const val MediaIdPlaylistTrackPrefix = "naviamp.playlist.track:"
    const val MediaIdRadioStationPrefix = "naviamp.radio.station:"
    const val MediaIdRecentRadioPrefix = "naviamp.radio.recent:"
    const val MediaIdRadioDjPrefix = "naviamp.radio.dj:"
    const val MediaIdSmartTemplatePrefix = "naviamp.smart.template:"

    const val CommandPlayPause = "play_pause"
    const val CommandPrevious = "previous"
    const val CommandNext = "next"

    fun isNonPlayableMediaId(mediaId: String): Boolean =
        mediaId == MediaIdNoSource ||
            mediaId.endsWith(".empty") ||
            mediaId.endsWith(".error") ||
            mediaId in nonPlayableContainerIds ||
            mediaId.startsWith(MediaIdQueuePagePrefix) ||
            mediaId.startsWith(MediaIdArtistGroupPrefix) ||
            mediaId.startsWith(MediaIdArtistPrefix) ||
            mediaId.startsWith(MediaIdAlbumPrefix) ||
            mediaId.startsWith(MediaIdPlaylistPrefix) ||
            mediaId.startsWith(MediaIdSmartPlaylists)

    private val nonPlayableContainerIds = setOf(
        MediaIdRoot,
        MediaIdQueue,
        MediaIdHome,
        MediaIdHomeMixes,
        MediaIdHomeDjs,
        MediaIdHomeSmartTemplates,
        MediaIdHomeRecentPlays,
        MediaIdHomeRecentlyAdded,
        MediaIdLibrary,
        MediaIdLibraryArtists,
        MediaIdLibraryAlbums,
        MediaIdLibraryTracks,
        MediaIdCharts,
        MediaIdChartsArtists,
        MediaIdChartsAlbums,
        MediaIdChartsTracks,
        MediaIdRadio,
        MediaIdRadioStations,
        MediaIdRadioRecent,
        MediaIdDownloads,
        MediaIdMore,
        MediaIdPlaylists,
        MediaIdSmartPlaylists,
    )
}
