package app.naviamp.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.naviamp.presentation.NaviampCoreExternalUriPort

/** Android ACTION_VIEW effect for Core-owned external-link intent. */
class AndroidCoreExternalUriPort(context: Context) : NaviampCoreExternalUriPort {
    private val appContext = context.applicationContext

    override fun open(uri: String) {
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
