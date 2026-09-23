package app.naviamp.android

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import app.naviamp.app.NaviampCastSessionController
import app.naviamp.app.NaviampPlaybackOutputSelectionController
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastStateListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Debug build only: verifies SDK discovery and native route selection before product UI is wired. */
class CastDiscoveryProbeActivity : FragmentActivity() {
    private lateinit var castContext: CastContext
    private val outputs = NaviampPlaybackOutputSelectionController()
    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var sessions: NaviampCastSessionController
    private val castStateListener = CastStateListener { state ->
        Log.i("NaviampCastProbe", "Cast state=$state")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        castContext = CastContext.getSharedInstance(applicationContext)
        sessions = NaviampCastSessionController(AndroidNaviampCastSessionEffect(this), outputs)
        probeScope.launch {
            outputs.state.collect { Log.i("NaviampCastProbe", "Output=$it") }
        }
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
        sessions.start()
    }

    override fun onStop() {
        sessions.stop()
        castContext.removeCastStateListener(castStateListener)
        super.onStop()
    }

    override fun onDestroy() {
        probeScope.cancel()
        super.onDestroy()
    }
}
