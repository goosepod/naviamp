package app.naviamp.desktop

import app.naviamp.desktop.settings.RecentRadioKind
import app.naviamp.desktop.settings.RecentRadioStream
import app.naviamp.desktop.settings.SavedAlbum
import app.naviamp.desktop.settings.SavedArtist
import app.naviamp.desktop.settings.SavedTrack
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.Track

fun libraryRecentRadioStream(): RecentRadioStream =
    RecentRadioStream(
        id = "library",
        label = "Library radio",
        kind = RecentRadioKind.Library,
    )

fun genreRecentRadioStream(genre: Genre): RecentRadioStream =
    RecentRadioStream(
        id = "genre:${genre.name}",
        label = "${genre.name} radio",
        kind = RecentRadioKind.Genre,
        genre = genre.name,
    )

fun decadeRecentRadioStream(fromYear: Int, toYear: Int): RecentRadioStream =
    RecentRadioStream(
        id = "decade:$fromYear:$toYear",
        label = "$fromYear-$toYear radio",
        kind = RecentRadioKind.Decade,
        fromYear = fromYear,
        toYear = toYear,
    )

fun randomAlbumRecentRadioStream(album: Album): RecentRadioStream =
    RecentRadioStream(
        id = "random-album:${album.id.value}",
        label = "${album.title} radio",
        kind = RecentRadioKind.RandomAlbum,
        album = SavedAlbum.fromAlbum(album),
    )

fun artistRecentRadioStream(artist: Artist): RecentRadioStream =
    RecentRadioStream(
        id = "artist:${artist.id.value}",
        label = "${artist.name} radio",
        kind = RecentRadioKind.Artist,
        artist = SavedArtist.fromArtist(artist),
    )

fun albumRecentRadioStream(album: Album): RecentRadioStream =
    RecentRadioStream(
        id = "album:${album.id.value}",
        label = "${album.title} radio",
        kind = RecentRadioKind.Album,
        album = SavedAlbum.fromAlbum(album),
    )

fun trackRecentRadioStream(track: Track): RecentRadioStream =
    RecentRadioStream(
        id = "track:${track.id.value}",
        label = "${track.title} radio",
        kind = RecentRadioKind.Track,
        track = SavedTrack.fromTrack(track),
    )

fun popularTracksRecentRadioStream(seedTrack: Track): RecentRadioStream =
    RecentRadioStream(
        id = "popular:${seedTrack.artistId?.value ?: seedTrack.artistName}",
        label = "${seedTrack.artistName} popular tracks radio",
        kind = RecentRadioKind.Track,
        track = SavedTrack.fromTrack(seedTrack),
    )
