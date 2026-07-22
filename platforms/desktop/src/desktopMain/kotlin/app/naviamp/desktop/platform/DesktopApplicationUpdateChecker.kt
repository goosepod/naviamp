package app.naviamp.desktop

import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.ui.HttpNaviampApplicationUpdateChecker
import app.naviamp.ui.NaviampApplicationUpdateChecker

/** Desktop HTTP construction for the shared release/version policy. */
fun desktopApplicationUpdateChecker(
    client: SharedHttpClient = KtorSharedHttpClient(),
): NaviampApplicationUpdateChecker = HttpNaviampApplicationUpdateChecker(client)
