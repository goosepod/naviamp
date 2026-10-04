package app.naviamp.android

import android.content.Context
import app.naviamp.app.NaviampCastReceiver
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/** Cast SDK manifest entry point; receiver choice is shared product policy. */
class AndroidNaviampCastOptions : OptionsProvider {
    override fun getCastOptions(appContext: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(NaviampCastReceiver.ApplicationId)
        .build()

    override fun getAdditionalSessionProviders(appContext: Context): List<SessionProvider>? = null
}
