package app.naviamp.presentation

import app.naviamp.app.NaviampPlaybackOutputSelection
import app.naviamp.app.NaviampRemoteOutputKind
import app.naviamp.app.NaviampRemoteOutputPhase
import app.naviamp.ui.NaviampCastOutputUi

/** Cast and UPnP share the output indicator; Connect retains its existing remote presentation. */
internal fun naviampCoreRemoteOutputUi(available: Boolean, selection: NaviampPlaybackOutputSelection): NaviampCastOutputUi {
    val remote = (selection as? NaviampPlaybackOutputSelection.Remote)
        ?.takeIf { it.target.kind in setOf(NaviampRemoteOutputKind.Cast, NaviampRemoteOutputKind.Upnp) }
    return NaviampCastOutputUi(available = available, selectedTargetName = remote?.target?.displayName,
        selected = remote != null, playbackActive = remote?.playbackAuthorityActive == true,
        unavailable = remote?.phase == NaviampRemoteOutputPhase.Unavailable)
}
