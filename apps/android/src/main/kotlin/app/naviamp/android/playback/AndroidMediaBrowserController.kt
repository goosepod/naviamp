package app.naviamp.android.playback

import android.content.Context
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import androidx.media.MediaSessionManager
import androidx.media.utils.MediaConstants

/** Owns Android MediaBrowser trust and callback delegation; browse content remains separately composed. */
internal class AndroidMediaBrowserController(
    private val context: Context,
    private val applicationUid: Int,
    private val hydrateSession: () -> Unit,
    private val browse: AndroidAutoBrowseController,
) {
    fun root(clientPackageName: String, clientUid: Int): MediaBrowserServiceCompat.BrowserRoot? {
        val remoteUser = MediaSessionManager.RemoteUserInfo(clientPackageName, -1, clientUid)
        val trusted = clientUid == applicationUid ||
            MediaSessionManager.getSessionManager(context).isTrustedForMediaControl(remoteUser)
        if (!trusted) {
            Log.w("NaviampAutoCommand", "Rejecting untrusted media browser client=$clientPackageName uid=$clientUid")
            return null
        }
        hydrateSession()
        return MediaBrowserServiceCompat.BrowserRoot(
            AndroidAutoPlaybackControls.MediaIdRoot,
            Bundle().apply { putBoolean(MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, true) },
        )
    }

    fun loadChildren(
        parentId: String,
        result: MediaBrowserServiceCompat.Result<MutableList<MediaBrowserCompat.MediaItem>>,
        options: Bundle? = null,
    ) {
        hydrateSession()
        if (options == null) browse.loadChildren(parentId, result) else browse.loadChildren(parentId, result, options)
    }

    fun search(
        query: String,
        extras: Bundle?,
        result: MediaBrowserServiceCompat.Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        hydrateSession()
        browse.search(query, extras, result)
    }
}
