package app.naviamp.android

import android.support.v4.media.session.PlaybackStateCompat
import app.naviamp.presentation.NaviampExternalPlaybackSnapshot
import app.naviamp.ui.NaviampExternalPlaybackAction
import app.naviamp.ui.NaviampTransportIcons
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorPath
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidExternalPlaybackActionsTest {
    @Test
    fun nativeRepeatResourcesStayAlignedWithTheSharedRepeatDesign() {
        val namespace = "http://schemas.android.com/apk/res/android"
        for ((name, shared) in listOf("ic_repeat_24" to NaviampTransportIcons.Repeat,
            "ic_repeat_off_24" to NaviampTransportIcons.Repeat,
            "ic_repeat_one_24" to NaviampTransportIcons.RepeatOne)) {
            val resource = File("src/main/res/drawable/$name.xml").takeIf { it.isFile }
                ?: File("apps/android/src/main/res/drawable/$name.xml")
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val paths = factory.newDocumentBuilder().parse(resource).getElementsByTagName("path")
            assertEquals(shared.root.size, paths.length, name)
            for (index in 0 until paths.length) {
                val native = paths.item(index) as org.w3c.dom.Element
                val expected = shared.root[index] as VectorPath
                assertEquals(expected.pathData,
                    PathParser().parsePathString(native.getAttributeNS(namespace, "pathData")).toNodes(), name)
                assertEquals(expected.strokeLineWidth, native.getAttributeNS(namespace, "strokeWidth").toFloat(), name)
                assertEquals("round", native.getAttributeNS(namespace, "strokeLineCap"), name)
                val opacity = native.getAttributeNS(namespace, "strokeAlpha").toFloatOrNull() ?: 1f
                if (name == "ic_repeat_off_24") assertTrue(opacity > 0f && opacity < 1f)
                else assertEquals(1f, opacity)
            }
        }
        assertEquals(R.drawable.ic_repeat_off_24, NaviampExternalPlaybackAction.RepeatOff.androidCustomActionIcon())
        assertEquals(R.drawable.ic_repeat_24, NaviampExternalPlaybackAction.RepeatQueue.androidCustomActionIcon())
        assertEquals(R.drawable.ic_repeat_one_24, NaviampExternalPlaybackAction.RepeatTrack.androidCustomActionIcon())
        assertEquals(NaviampExternalPlaybackAction.RepeatOff.androidCustomActionId(),
            NaviampExternalPlaybackAction.RepeatTrack.androidCustomActionId())
    }

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
