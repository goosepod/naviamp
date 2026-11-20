package app.naviamp.bootstrap

import app.naviamp.presentation.NaviampExternalPlaybackSurface
import app.naviamp.presentation.NaviampExternalPlaybackSurfaceSession
import app.naviamp.presentation.attachSurface
import app.naviamp.presentation.externalPlaybackBridge
import kotlinx.coroutines.CoroutineScope

/** Complete shared media assembly; hosts supply only the native surface and resource registration. */
fun <Artwork> NaviampStoredApplication.attachPlaybackSurface(
    scope: CoroutineScope,
    surface: NaviampExternalPlaybackSurface<Artwork>,
    registerClose: (() -> Unit) -> Unit,
    onFailure: (Throwable) -> Unit,
): NaviampExternalPlaybackSurfaceSession<Artwork> = core.externalPlaybackBridge().attachSurface(
    scope, surface, ::loadProviderArtwork, onFailure, registerClose,
)
