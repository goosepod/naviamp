package app.naviamp.android

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.domain.playback.NamedMediaKind
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidNamedMediaAppActionInstrumentedTest {
    @Test fun appActionsKeepTheStructuredTitleAndMediaType() {
        val extras = Bundle().apply { putString(AppActionMediaName, "The Album Leaf") }
        assertEquals(NamedMediaKind.Artist,
            androidAppActionNamedMediaRequest(AppActionPlayArtist, extras)?.kind)
        assertEquals("The Album Leaf",
            androidAppActionNamedMediaRequest(AppActionPlayArtist, extras)?.name)
        assertEquals(NamedMediaKind.ArtistRadio,
            androidAppActionNamedMediaRequest(AppActionPlayArtistRadio, extras)?.kind)
        assertEquals(NamedMediaKind.Album,
            androidAppActionNamedMediaRequest(AppActionPlayAlbum, extras)?.kind)
        assertEquals(NamedMediaKind.Playlist,
            androidAppActionNamedMediaRequest(AppActionPlayPlaylist, extras)?.kind)
        assertNull(androidAppActionNamedMediaRequest(AppActionPlayArtist, Bundle()))
        assertFalse(isAndroidNamedMediaAppAction("unknown"))
    }

    @Test fun packagedCapabilitiesTargetTheInstalledApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val names = mutableSetOf<String>()
        context.resources.getXml(R.xml.shortcuts).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "capability") {
                    parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")?.let(names::add)
                }
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "intent") {
                    assertEquals(context.packageName,
                        parser.getAttributeValue("http://schemas.android.com/apk/res/android", "targetPackage"))
                }
                parser.next()
            }
        }
        assertEquals(4, names.size)
        assertTrue(names.all { it.startsWith("custom.actions.intent.PLAY_") })
    }
}
