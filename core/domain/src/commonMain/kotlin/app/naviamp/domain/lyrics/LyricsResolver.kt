package app.naviamp.domain.lyrics

import app.naviamp.domain.Lyrics

fun selectPreferredLyrics(
    providerLyrics: Lyrics?,
    embeddedLyrics: Lyrics?,
    onlineLyrics: Lyrics?,
): Lyrics? {
    val localLyrics = providerLyrics ?: embeddedLyrics
    return when {
        localLyrics == null -> onlineLyrics
        localLyrics.synced -> localLyrics
        onlineLyrics?.synced == true -> onlineLyrics
        else -> localLyrics
    }
}
