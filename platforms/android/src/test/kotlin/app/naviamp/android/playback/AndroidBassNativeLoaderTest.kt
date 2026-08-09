package app.naviamp.android.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidBassNativeLoaderTest {
    @Test
    fun codecInventoryMatchesBundledAndroidPlugins() {
        assertEquals(12, AndroidBassNativeLoader.codecLibraries.size)
        assertTrue("bassflac" in AndroidBassNativeLoader.codecLibraries)
        assertTrue("bassopus" in AndroidBassNativeLoader.codecLibraries)
        assertFalse("bass_spx" in AndroidBassNativeLoader.codecLibraries)
        assertFalse("basswma" in AndroidBassNativeLoader.codecLibraries)
        assertFalse("bass_fx" in AndroidBassNativeLoader.codecLibraries)
        assertFalse("bassloud" in AndroidBassNativeLoader.codecLibraries)
    }

    @Test
    fun nativePluginFilenameUsesAndroidSharedLibraryConvention() {
        assertEquals("libbassflac.so", androidBassPluginFileName("bassflac"))
    }
}
