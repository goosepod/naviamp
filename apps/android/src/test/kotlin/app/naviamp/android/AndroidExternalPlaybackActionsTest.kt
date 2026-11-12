package app.naviamp.android

import android.support.v4.media.session.PlaybackStateCompat
import app.naviamp.presentation.NaviampExternalPlaybackSnapshot
import app.naviamp.ui.NaviampExternalPlaybackAction
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidExternalPlaybackActionsTest {
    @Test
    fun liveTransportHasNoUnsupportedTrackControls() {
        val flags = NaviampExternalPlaybackSnapshot(canPlayPause = true).androidPlaybackActions()
        assertEquals(0L, flags and (PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_REWIND or PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_SET_REPEAT_MODE or PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE))
        assertEquals(PlaybackStateCompat.ACTION_PLAY, flags and PlaybackStateCompat.ACTION_PLAY)
        assertEquals(PlaybackStateCompat.ACTION_PAUSE, flags and PlaybackStateCompat.ACTION_PAUSE)
    }

    @Test
    fun nativeMappingPreservesCoreCapabilitiesAndRadioIdentity() {
        val flags = NaviampExternalPlaybackSnapshot(canSeek = true, hasNext = true, canShuffle = true,
            canRepeat = true).androidPlaybackActions()
        for (flag in listOf(PlaybackStateCompat.ACTION_SEEK_TO, PlaybackStateCompat.ACTION_REWIND,
            PlaybackStateCompat.ACTION_FAST_FORWARD, PlaybackStateCompat.ACTION_SKIP_TO_NEXT,
            PlaybackStateCompat.ACTION_SET_REPEAT_MODE, PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)) {
            assertEquals(flag, flags and flag)
        }
        assertEquals(0L, flags and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        assertEquals(AndroidNaviampPlaybackService.CustomActionRadio,
            NaviampExternalPlaybackAction.StartRadio.androidCustomActionId())
        assertEquals(R.drawable.ic_radio_24, NaviampExternalPlaybackAction.StartRadio.androidCustomActionIcon())
    }
}
