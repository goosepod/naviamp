package app.naviamp.android

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNaviampConnectPermissionSettingsInstrumentedTest {
    @Test
    fun opensThisApplicationsSettingsFromApplicationContext() {
        var launched: Intent? = null
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun startActivity(intent: Intent) { launched = intent }
        }
        assertTrue(AndroidNaviampConnectPermissionSettingsEffect(context).open())
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, launched?.action)
        assertEquals("package:${context.packageName}", launched?.data.toString())
        assertTrue(launched!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun unavailableSettingsReturnsFailure() {
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        assertFalse(AndroidNaviampConnectPermissionSettingsEffect(context).open())
    }
}
