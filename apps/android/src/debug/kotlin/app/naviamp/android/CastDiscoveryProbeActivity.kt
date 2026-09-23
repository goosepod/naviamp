package app.naviamp.android

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/** Debug build only: verifies SDK discovery and native route selection before product UI is wired. */
class CastDiscoveryProbeActivity : FragmentActivity() {
    private lateinit var castContext: CastContext
    private val castStateListener = CastStateListener { state ->
        Log.i("NaviampCastProbe", "Cast state=$state")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        castContext = CastContext.getSharedInstance(applicationContext)
        val button = MediaRouteButton(this)
        CastButtonFactory.setUpMediaRouteButton(applicationContext, button)
        val size = (72 * resources.displayMetrics.density).toInt()
        setContentView(FrameLayout(this).apply {
            addView(button, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        })
        Log.i("NaviampCastProbe", "Cast SDK initialized; state=${castContext.castState}")
    }

    override fun onStart() {
        super.onStart()
        castContext.addCastStateListener(castStateListener)
    }

    override fun onStop() {
        castContext.removeCastStateListener(castStateListener)
        super.onStop()
    }
}

class CastDiscoveryProbeOptions : OptionsProvider {
    override fun getCastOptions(appContext: android.content.Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .build()

    override fun getAdditionalSessionProviders(appContext: android.content.Context): List<SessionProvider>? = null
}
