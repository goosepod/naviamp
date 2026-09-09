package app.naviamp.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import app.naviamp.app.NaviampConnectPermissionSettingsEffect

/** Android Settings intent adapter. Recovery decisions and permission state remain in Core. */
class AndroidNaviampConnectPermissionSettingsEffect(private val context: Context) : NaviampConnectPermissionSettingsEffect {
    override fun open(): Boolean = try {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
