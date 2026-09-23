package app.naviamp.android

import android.content.Context
import app.naviamp.app.NaviampCastSessionEffect
import app.naviamp.app.NaviampCastSessionListener
import app.naviamp.app.NaviampCastTarget
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener

/** Translates main-thread Cast SDK session callbacks into the shared session contract. */
class AndroidNaviampCastSessionEffect(context: Context) : NaviampCastSessionEffect {
    private val castContext = CastContext.getSharedInstance(context.applicationContext)
    private val sessionManager = castContext.sessionManager
    private var listener: NaviampCastSessionListener? = null
    private var activeSession: CastSession? = null
    private var activeSelectionId: Long? = null

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            begin(session)?.let { listener?.onConnecting(it) }
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            val id = selectionFor(session) ?: begin(session) ?: return
            listener?.onConnected(id, session.castDevice?.friendlyName.orEmpty())
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            finish(session)
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            selectionFor(session)?.let { listener?.onUnavailable(it) }
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            val id = selectionFor(session) ?: begin(session) ?: return
            listener?.onConnecting(id)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            val id = selectionFor(session) ?: begin(session) ?: return
            listener?.onConnected(id, session.castDevice?.friendlyName.orEmpty())
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            finish(session)
        }

        override fun onSessionEnding(session: CastSession) = Unit

        override fun onSessionEnded(session: CastSession, error: Int) {
            val id = selectionFor(session) ?: return
            if (error == CastStatusCodes.SUCCESS) listener?.onStopped(id)
            else listener?.onDisconnected(id)
            activeSession = null
            activeSelectionId = null
        }
    }

    override fun start(listener: NaviampCastSessionListener) {
        check(this.listener == null) { "Cast session listener is already attached." }
        this.listener = listener
        sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        sessionManager.currentCastSession?.let { session ->
            val id = begin(session) ?: return@let
            if (session.isConnected) listener.onConnected(id, session.castDevice?.friendlyName.orEmpty())
            else listener.onConnecting(id)
        }
    }

    override fun stop() {
        if (listener == null) return
        sessionManager.removeSessionManagerListener(sessionListener, CastSession::class.java)
        listener = null
        activeSession = null
        activeSelectionId = null
    }

    override fun disconnect() {
        sessionManager.endCurrentSession(true)
    }

    private fun begin(session: CastSession): Long? {
        val device = session.castDevice ?: return null
        val id = listener?.onTargetSelected(NaviampCastTarget(
            id = device.deviceId,
            displayName = device.friendlyName,
        )) ?: return null
        activeSession = session
        activeSelectionId = id
        return id
    }

    private fun selectionFor(session: CastSession): Long? =
        activeSelectionId?.takeIf { activeSession === session }

    private fun finish(session: CastSession) {
        val id = selectionFor(session) ?: return
        listener?.onDisconnected(id)
        activeSession = null
        activeSelectionId = null
    }
}
