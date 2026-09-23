package app.naviamp.android

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import app.naviamp.app.NaviampCastSessionController
import app.naviamp.app.NaviampCastMediaByteSource
import app.naviamp.app.NaviampCastMediaEndpointController
import app.naviamp.app.NaviampCastMediaKind
import app.naviamp.app.NaviampCastMediaLeaseController
import app.naviamp.app.NaviampCastMediaResource
import app.naviamp.app.NaviampCastRequestedRange
import app.naviamp.app.NaviampPlaybackOutputSelectionController
import app.naviamp.app.resolve
import app.naviamp.domain.provider.ProviderMediaByteResponse
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastStateListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug build only: verifies SDK discovery and native route selection before product UI is wired. */
class CastDiscoveryProbeActivity : FragmentActivity() {
    private lateinit var castContext: CastContext
    private val outputs = NaviampPlaybackOutputSelectionController()
    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var sessions: NaviampCastSessionController
    private lateinit var endpoint: NaviampCastMediaEndpointController
    private val castStateListener = CastStateListener { state ->
        Log.i("NaviampCastProbe", "Cast state=$state")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        castContext = CastContext.getSharedInstance(applicationContext)
        sessions = NaviampCastSessionController(AndroidNaviampCastSessionEffect(this), outputs)
        endpoint = NaviampCastMediaEndpointController(
            server = AndroidNaviampCastHttpServerEffect(this),
            leases = NaviampCastMediaLeaseController(
                tokens = AndroidNaviampCastSecureTokenSource(),
                nowEpochMillis = System::currentTimeMillis,
            ),
            source = ProbeByteSource,
        )
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
        probeScope.launch {
            runCatching {
                endpoint.start()
                endpoint.issue(NaviampCastMediaResource(NaviampCastMediaKind.Track, "probe", "probe"))
            }.onSuccess { url -> Log.i("NaviampCastProbe", "Endpoint probe URL=$url") }
                .onFailure { error -> Log.e("NaviampCastProbe", "Endpoint start failed", error) }
        }
    }

    override fun onStop() {
        sessions.stop()
        castContext.removeCastStateListener(castStateListener)
        probeScope.launch { endpoint.stop() }
        super.onStop()
    }

}

private object ProbeByteSource : NaviampCastMediaByteSource {
    private val body = "naviamp-cast-endpoint-probe".encodeToByteArray()

    override suspend fun stream(
        resource: NaviampCastMediaResource,
        range: NaviampCastRequestedRange?,
        headOnly: Boolean,
        onResponse: suspend (ProviderMediaByteResponse) -> Unit,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean {
        val resolved = range?.resolve(body.size.toLong())
        if (range != null && resolved == null) {
            onResponse(ProviderMediaByteResponse(416, "text/plain", 0, "bytes */${body.size}"))
            return true
        }
        val start = resolved?.firstByte?.toInt() ?: 0
        val end = (resolved?.lastByteInclusive?.toInt() ?: body.lastIndex) + 1
        onResponse(ProviderMediaByteResponse(
            statusCode = if (resolved == null) 200 else 206,
            contentType = "text/plain",
            contentLength = (end - start).toLong(),
            contentRange = resolved?.contentRange,
        ))
        if (!headOnly) writeChunk(body.copyOfRange(start, end), end - start)
        return true
    }
}
