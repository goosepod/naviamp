package app.naviamp.domain.lyrics

import app.naviamp.domain.Lyrics

fun selectPreferredLyrics(
    providerLyrics: Lyrics?,
    embeddedLyrics: Lyrics?,
    onlineLyrics: Lyrics?,
): Lyrics? {
    return when {
        providerLyrics != null -> providerLyrics
        embeddedLyrics != null -> embeddedLyrics
        else -> onlineLyrics
    }
}
