package app.naviamp.android

import android.content.Context
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.settings.streamQualityForNetwork

internal class AndroidPlaybackQualityController(
    private val context: Context,
    private val state: AndroidAppState,
) {
    fun currentStreamQuality(): StreamQuality =
        state.playbackSettings.streamQualityForNetwork(context.isActiveNetworkMobileData())
}
