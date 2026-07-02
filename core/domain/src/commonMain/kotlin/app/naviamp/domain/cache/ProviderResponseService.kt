package app.naviamp.domain.cache

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ArtistInfo
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.ReplayGain
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.AlbumListType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProviderResponseService(
    private val cacheRepository: ProviderResponseCacheRepository,
    private val json: Json = ProviderResponseJson,
) {
    suspend fun artists(
        provider: MediaProvider,
        limit: Int,
    ): List<Artist> =
        cacheRepository.cachedProviderResponse(
            provider = provider,
            resourceType = ArtistsResourceType,
            resourceId = limitedResourceId(limit),
            decode = { json.decodeFromString<List<ArtistDto>>(it).map { dto -> dto.toArtist() } },
            encode = { json.encodeToString(it.map { artist -> ArtistDto.fromArtist(artist) }) },
            fetch = { provider.artists(limit) },
        )

    suspend fun playlists(
        provider: MediaProvider,
        limit: Int,
    ): List<Playlist> =
        cacheRepository.cachedProviderResponse(
            provider = provider,
            resourceType = PlaylistsResourceType,
            resourceId = limitedResourceId(limit),
            decode = { json.decodeFromString<List<PlaylistDto>>(it).map { dto -> dto.toPlaylist() } },
            encode = { json.encodeToString(it.map { playlist -> PlaylistDto.fromPlaylist(playlist) }) },
            fetch = { provider.playlists(limit) },
        )

    suspend fun internetRadioStations(provider: MediaProvider): List<InternetRadioStation> =
        cacheRepository.cachedProviderResponse(
            provider = provider,
            resourceType = InternetRadioStationsResourceType,
            resourceId = SingletonResourceId,
            decode = { json.decodeFromString<List<InternetRadioStationDto>>(it).map { dto -> dto.toInternetRadioStation() } },
            encode = { json.encodeToString(it.map { station -> InternetRadioStationDto.fromInternetRadioStation(station) }) },
            fetch = { provider.internetRadioStations() },
        )

    fun invalidateInternetRadioStations(provider: MediaProvider) {
        cacheRepository.invalidateProviderResponse(provider, InternetRadioStationsResourceType, SingletonResourceId)
    }

    suspend fun playlistTracks(
        provider: MediaProvider,
        playlistId: String,
    ): List<Track> =
        cacheRepository.cachedProviderResponse(
            provider = provider,
            resourceType = PlaylistTracksResourceType,
            resourceId = playlistId,
            decode = { json.decodeFromString<List<TrackDto>>(it).map { dto -> dto.toTrack() } },
            encode = { json.encodeToString(it.map { track -> TrackDto.fromTrack(track) }) },
            fetch = { provider.playlistTracks(playlistId) },
        )

    fun invalidatePlaylists(provider: MediaProvider) {
        cacheRepository.invalidateProviderResponses(provider, PlaylistsResourceType)
    }

    fun invalidatePlaylistTracks(
        provider: MediaProvider,
        playlistId: String,
    ) {
        cacheRepository.invalidateProviderResponse(provider, PlaylistTracksResourceType, playlistId)
    }

    fun invalidatePlaylistResponses(
        provider: MediaProvider,
        playlistId: String? = null,
    ) {
        invalidatePlaylists(provider)
        playlistId?.let { invalidatePlaylistTracks(provider, it) }
    }

    suspend fun albumList(
        provider: MediaProvider,
        type: AlbumListType,
        limit: Int,
    ): List<Album> =
        cacheRepository.cachedProviderResponse(
            provider = provider,
            resourceType = AlbumListResourceType,
            resourceId = albumListResourceId(type, limit),
            decode = { json.decodeFromString<List<AlbumDto>>(it).map { dto -> dto.toAlbum() } },
            encode = { json.encodeToString(it.map { album -> AlbumDto.fromAlbum(album) }) },
            fetch = { provider.albumList(type, limit) },
        )

    suspend fun albumsByGenre(
        provider: MediaProvider,
        genre: String,
        limit: Int,
    ): List<Album> =
        cacheRepository.cachedProviderResponse(
            provider = provider,
            resourceType = AlbumsByGenreResourceType,
            resourceId = albumsByGenreResourceId(genre, limit),
            decode = { json.decodeFromString<List<AlbumDto>>(it).map { dto -> dto.toAlbum() } },
            encode = { json.encodeToString(it.map { album -> AlbumDto.fromAlbum(album) }) },
            fetch = { provider.albumsByGenre(genre, limit) },
        )

    suspend fun albumsByYear(
        provider: MediaProvider,
        fromYear: Int,
        toYear: Int,
        limit: Int,
    ): List<Album> =
        cacheRepository.cachedProviderResponse(
            provider = provider,
            resourceType = AlbumsByYearResourceType,
            resourceId = albumsByYearResourceId(fromYear, toYear, limit),
            decode = { json.decodeFromString<List<AlbumDto>>(it).map { dto -> dto.toAlbum() } },
            encode = { json.encodeToString(it.map { album -> AlbumDto.fromAlbum(album) }) },
            fetch = { provider.albumsByYear(fromYear, toYear, limit) },
        )

    suspend fun album(
        provider: MediaProvider,
        albumId: AlbumId,
    ): AlbumDetails =
        cacheRepository.cachedProviderResponse(
            provider = provider,
            resourceType = AlbumResourceType,
            resourceId = albumId.value,
            decode = { json.decodeFromString<AlbumDetailsDto>(it).toAlbumDetails() },
            encode = { json.encodeToString(AlbumDetailsDto.fromAlbumDetails(it)) },
            fetch = { provider.album(albumId) },
        )

    suspend fun artist(
        provider: MediaProvider,
        artistId: ArtistId,
    ): ArtistDetails =
        cacheRepository.cachedProviderResponse(
            provider = provider,
            resourceType = ArtistResourceType,
            resourceId = artistId.value,
            decode = { json.decodeFromString<ArtistDetailsDto>(it).toArtistDetails() },
            encode = { json.encodeToString(ArtistDetailsDto.fromArtistDetails(it)) },
            fetch = { provider.artist(artistId) },
        )

    suspend fun search(
        provider: MediaProvider,
        query: String,
        limit: Int,
    ): MediaSearchResults =
        cacheRepository.cachedProviderResponse(
            provider = provider,
            resourceType = SearchResourceType,
            resourceId = searchResourceId(query, limit),
            decode = { json.decodeFromString<MediaSearchResultsDto>(it).toMediaSearchResults() },
            encode = { json.encodeToString(MediaSearchResultsDto.fromMediaSearchResults(it)) },
            fetch = { provider.search(query, limit) },
        )
}

fun searchResourceId(query: String, limit: Int): String =
    "${query.trim().lowercase()}:$limit"

fun albumListResourceId(type: AlbumListType, limit: Int): String =
    "${type.providerValue}:$limit"

fun albumsByGenreResourceId(genre: String, limit: Int): String =
    "${genre.trim().lowercase()}:$limit"

fun albumsByYearResourceId(fromYear: Int, toYear: Int, limit: Int): String =
    "$fromYear:$toYear:$limit"

fun limitedResourceId(limit: Int): String =
    limit.toString()

private val ProviderResponseJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private const val SearchResourceType = "search"
private const val AlbumResourceType = "album"
private const val ArtistResourceType = "artist"
private const val ArtistsResourceType = "artists"
private const val PlaylistsResourceType = "playlists"
private const val PlaylistTracksResourceType = "playlistTracks"
private const val InternetRadioStationsResourceType = "internetRadioStations"
private const val AlbumListResourceType = "albumList"
private const val AlbumsByGenreResourceType = "albumsByGenre"
private const val AlbumsByYearResourceType = "albumsByYear"
private const val SingletonResourceId = "all"

@Serializable
private data class MediaSearchResultsDto(
    val artists: List<ArtistDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList(),
    val tracks: List<TrackDto> = emptyList(),
) {
    fun toMediaSearchResults(): MediaSearchResults =
        MediaSearchResults(
            artists = artists.map { it.toArtist() },
            albums = albums.map { it.toAlbum() },
            tracks = tracks.map { it.toTrack() },
        )

    companion object {
        fun fromMediaSearchResults(results: MediaSearchResults): MediaSearchResultsDto =
            MediaSearchResultsDto(
                artists = results.artists.map { ArtistDto.fromArtist(it) },
                albums = results.albums.map { AlbumDto.fromAlbum(it) },
                tracks = results.tracks.map { TrackDto.fromTrack(it) },
            )
    }
}

@Serializable
private data class AlbumDetailsDto(
    val album: AlbumDto,
    val tracks: List<TrackDto>,
) {
    fun toAlbumDetails(): AlbumDetails =
        AlbumDetails(
            album = album.toAlbum(),
            tracks = tracks.map { it.toTrack() },
        )

    companion object {
        fun fromAlbumDetails(details: AlbumDetails): AlbumDetailsDto =
            AlbumDetailsDto(
                album = AlbumDto.fromAlbum(details.album),
                tracks = details.tracks.map { TrackDto.fromTrack(it) },
            )
    }
}

@Serializable
private data class ArtistDetailsDto(
    val artist: ArtistDto,
    val albums: List<AlbumDto>,
    val info: ArtistInfoDto? = null,
) {
    fun toArtistDetails(): ArtistDetails =
        ArtistDetails(
            artist = artist.toArtist(),
            albums = albums.map { it.toAlbum() },
            info = info?.toArtistInfo(),
        )

    companion object {
        fun fromArtistDetails(details: ArtistDetails): ArtistDetailsDto =
            ArtistDetailsDto(
                artist = ArtistDto.fromArtist(details.artist),
                albums = details.albums.map { AlbumDto.fromAlbum(it) },
                info = details.info?.let { ArtistInfoDto.fromArtistInfo(it) },
            )
    }
}

@Serializable
private data class ArtistDto(
    val id: String,
    val name: String,
    val favoritedAtIso8601: String? = null,
) {
    fun toArtist(): Artist =
        Artist(
            id = ArtistId(id),
            name = name,
            favoritedAtIso8601 = favoritedAtIso8601,
        )

    companion object {
        fun fromArtist(artist: Artist): ArtistDto =
            ArtistDto(
                id = artist.id.value,
                name = artist.name,
                favoritedAtIso8601 = artist.favoritedAtIso8601,
            )
    }
}

@Serializable
private data class ArtistInfoDto(
    val biography: String? = null,
    val smallImageUrl: String? = null,
    val mediumImageUrl: String? = null,
    val largeImageUrl: String? = null,
) {
    fun toArtistInfo(): ArtistInfo =
        ArtistInfo(
            biography = biography,
            smallImageUrl = smallImageUrl,
            mediumImageUrl = mediumImageUrl,
            largeImageUrl = largeImageUrl,
        )

    companion object {
        fun fromArtistInfo(info: ArtistInfo): ArtistInfoDto =
            ArtistInfoDto(
                biography = info.biography,
                smallImageUrl = info.smallImageUrl,
                mediumImageUrl = info.mediumImageUrl,
                largeImageUrl = info.largeImageUrl,
            )
    }
}

@Serializable
private data class PlaylistDto(
    val id: String,
    val name: String,
    val trackCount: Int,
    val durationSeconds: Int? = null,
    val coverArtId: String? = null,
    val isSmart: Boolean = false,
) {
    fun toPlaylist(): Playlist =
        Playlist(
            id = id,
            name = name,
            trackCount = trackCount,
            durationSeconds = durationSeconds,
            coverArtId = coverArtId,
            isSmart = isSmart,
        )

    companion object {
        fun fromPlaylist(playlist: Playlist): PlaylistDto =
            PlaylistDto(
                id = playlist.id,
                name = playlist.name,
                trackCount = playlist.trackCount,
                durationSeconds = playlist.durationSeconds,
                coverArtId = playlist.coverArtId,
                isSmart = playlist.isSmart,
            )
    }
}

@Serializable
private data class InternetRadioStationDto(
    val id: String,
    val name: String,
    val streamUrl: String,
    val homePageUrl: String? = null,
) {
    fun toInternetRadioStation(): InternetRadioStation =
        InternetRadioStation(
            id = id,
            name = name,
            streamUrl = streamUrl,
            homePageUrl = homePageUrl,
        )

    companion object {
        fun fromInternetRadioStation(station: InternetRadioStation): InternetRadioStationDto =
            InternetRadioStationDto(
                id = station.id,
                name = station.name,
                streamUrl = station.streamUrl,
                homePageUrl = station.homePageUrl,
            )
    }
}

@Serializable
private data class AlbumDto(
    val id: String,
    val title: String,
    val artistName: String,
    val coverArtId: String? = null,
    val recentlyAddedAtIso8601: String? = null,
    val releaseYear: Int? = null,
    val favoritedAtIso8601: String? = null,
) {
    fun toAlbum(): Album =
        Album(
            id = AlbumId(id),
            title = title,
            artistName = artistName,
            coverArtId = coverArtId,
            recentlyAddedAtIso8601 = recentlyAddedAtIso8601,
            releaseYear = releaseYear,
            favoritedAtIso8601 = favoritedAtIso8601,
        )

    companion object {
        fun fromAlbum(album: Album): AlbumDto =
            AlbumDto(
                id = album.id.value,
                title = album.title,
                artistName = album.artistName,
                coverArtId = album.coverArtId,
                recentlyAddedAtIso8601 = album.recentlyAddedAtIso8601,
                releaseYear = album.releaseYear,
                favoritedAtIso8601 = album.favoritedAtIso8601,
            )
    }
}

@Serializable
private data class TrackDto(
    val id: String,
    val title: String,
    val artistId: String? = null,
    val artistName: String,
    val albumId: String? = null,
    val albumTitle: String? = null,
    val albumReleaseYear: Int? = null,
    val durationSeconds: Int? = null,
    val coverArtId: String? = null,
    val audioInfo: AudioInfoDto? = null,
    val replayGain: ReplayGainDto? = null,
    val favoritedAtIso8601: String? = null,
    val userRating: Int? = null,
    val bpm: Int? = null,
    val moods: List<String> = emptyList(),
    val playCount: Int? = null,
    val lastPlayedAtIso8601: String? = null,
    val musicFolderId: String? = null,
) {
    fun toTrack(): Track =
        Track(
            id = TrackId(id),
            title = title,
            artistId = artistId?.let { ArtistId(it) },
            artistName = artistName,
            albumId = albumId?.let { AlbumId(it) },
            albumTitle = albumTitle,
            albumReleaseYear = albumReleaseYear,
            durationSeconds = durationSeconds,
            coverArtId = coverArtId,
            audioInfo = audioInfo?.toAudioInfo(),
            replayGain = replayGain?.toReplayGain(),
            favoritedAtIso8601 = favoritedAtIso8601,
            userRating = userRating,
            bpm = bpm,
            moods = moods,
            playCount = playCount,
            lastPlayedAtIso8601 = lastPlayedAtIso8601,
            musicFolderId = musicFolderId,
        )

    companion object {
        fun fromTrack(track: Track): TrackDto =
            TrackDto(
                id = track.id.value,
                title = track.title,
                artistId = track.artistId?.value,
                artistName = track.artistName,
                albumId = track.albumId?.value,
                albumTitle = track.albumTitle,
                albumReleaseYear = track.albumReleaseYear,
                durationSeconds = track.durationSeconds,
                coverArtId = track.coverArtId,
                audioInfo = track.audioInfo?.let { AudioInfoDto.fromAudioInfo(it) },
                replayGain = track.replayGain?.let { ReplayGainDto.fromReplayGain(it) },
                favoritedAtIso8601 = track.favoritedAtIso8601,
                userRating = track.userRating,
                bpm = track.bpm,
                moods = track.moods,
                playCount = track.playCount,
                lastPlayedAtIso8601 = track.lastPlayedAtIso8601,
                musicFolderId = track.musicFolderId,
            )
    }
}

@Serializable
private data class AudioInfoDto(
    val codec: String? = null,
    val bitrateKbps: Int? = null,
    val contentType: String? = null,
    val bitDepth: Int? = null,
    val samplingRateHz: Int? = null,
) {
    fun toAudioInfo(): AudioInfo =
        AudioInfo(
            codec = codec,
            bitrateKbps = bitrateKbps,
            contentType = contentType,
            bitDepth = bitDepth,
            samplingRateHz = samplingRateHz,
        )

    companion object {
        fun fromAudioInfo(audioInfo: AudioInfo): AudioInfoDto =
            AudioInfoDto(
                codec = audioInfo.codec,
                bitrateKbps = audioInfo.bitrateKbps,
                contentType = audioInfo.contentType,
                bitDepth = audioInfo.bitDepth,
                samplingRateHz = audioInfo.samplingRateHz,
            )
    }
}

@Serializable
private data class ReplayGainDto(
    val trackGainDb: Double? = null,
    val albumGainDb: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null,
) {
    fun toReplayGain(): ReplayGain =
        ReplayGain(
            trackGainDb = trackGainDb,
            albumGainDb = albumGainDb,
            trackPeak = trackPeak,
            albumPeak = albumPeak,
        )

    companion object {
        fun fromReplayGain(replayGain: ReplayGain): ReplayGainDto =
            ReplayGainDto(
                trackGainDb = replayGain.trackGainDb,
                albumGainDb = replayGain.albumGainDb,
                trackPeak = replayGain.trackPeak,
                albumPeak = replayGain.albumPeak,
            )
    }
}
