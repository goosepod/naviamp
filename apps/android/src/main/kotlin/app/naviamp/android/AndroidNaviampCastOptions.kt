package app.naviamp.android

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/** Cast SDK manifest entry point; receiver choice is shared product policy. */
class AndroidNaviampCastOptions : OptionsProvider {
    override fun getCastOptions(appContext: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        .build()

    override fun getAdditionalSessionProviders(appContext: Context): List<SessionProvider>? = null
}
