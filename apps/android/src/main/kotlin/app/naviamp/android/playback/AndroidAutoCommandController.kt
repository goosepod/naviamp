package app.naviamp.android.playback

import android.os.Bundle
import android.util.Log

internal class AndroidAutoCommandController(
    private val handleServiceAutoPlayPause: () -> Unit,
    private val handleServicePlayMediaId: (String) -> Boolean,
    private val handleServicePlaySearch: (String) -> Boolean,
    private val launchMainActivityForAutoMediaId: (String) -> Unit,
    private val toggleFavorite: () -> Unit,
    private val toggleShuffle: () -> Unit,
    private val cycleRepeat: () -> Unit,
    private val openQueue: () -> Unit,
    private val startTrackRadio: () -> Unit,
    private val refreshNotification: () -> Unit,
    private val isPlaying: () -> Boolean,
    private val favoriteAction: String,
    private val shuffleAction: String,
    private val repeatAction: String,
    private val queueAction: String,
    private val trackRadioAction: String,
) {
    fun playFromMediaId(mediaId: String, extras: Bundle?) {
        Log.i("NaviampAutoCommand", "Auto requested mediaId=$mediaId extras=${extras?.debugDescription().orEmpty()}")
        if (mediaId == AndroidAutoPlaybackControls.MediaIdNowPlaying && !isPlaying()) {
            handleServiceAutoPlayPause()
            refreshNotification()
            return
        }
        if (AndroidAutoPlaybackControls.isNonPlayableMediaId(mediaId)) {
            Log.w("NaviampAutoCommand", "Ignoring non-playable Auto mediaId=$mediaId")
            return
        }
        if (handleServicePlayMediaId(mediaId)) {
            return
        }
        Log.w("NaviampAutoCommand", "Service could not handle Auto mediaId=$mediaId; opening phone UI for context")
        launchMainActivityForAutoMediaId(mediaId)
    }

    fun playFromSearch(query: String, extras: Bundle?) {
        Log.i("NaviampAutoCommand", "Auto requested search=$query extras=${extras?.debugDescription().orEmpty()}")
        if (!handleServicePlaySearch(query)) {
            Log.w("NaviampAutoCommand", "Service could not handle Auto search=$query")
        }
    }

    fun customAction(action: String, extras: Bundle?) {
        Log.i("NaviampAutoCommand", "Auto requested custom action=$action extras=${extras?.debugDescription().orEmpty()}")
        when (action) {
            favoriteAction -> toggleFavorite()
            shuffleAction -> {
                toggleShuffle()
                refreshNotification()
            }
            repeatAction -> {
                cycleRepeat()
                refreshNotification()
            }
            queueAction -> openQueue()
            trackRadioAction -> {
                startTrackRadio()
                refreshNotification()
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun Bundle.debugDescription(): String =
    keySet().joinToString(prefix = "{", postfix = "}") { key -> "$key=${get(key)}" }
