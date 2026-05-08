package app.naviamp.domain.queue

import app.naviamp.domain.Track

data class PlayQueue(
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = 0,
) {
    val current: Track?
        get() = tracks.getOrNull(currentIndex)

    fun replace(tracks: List<Track>): PlayQueue =
        PlayQueue(tracks = tracks, currentIndex = 0)

    fun next(): PlayQueue =
        if (currentIndex < tracks.lastIndex) copy(currentIndex = currentIndex + 1) else this

    fun previous(): PlayQueue =
        if (currentIndex > 0) copy(currentIndex = currentIndex - 1) else this
}

