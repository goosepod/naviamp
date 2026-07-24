package app.naviamp.ios.platform

import app.naviamp.presentation.NaviampCoreExternalUriPort
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/** Executes Core-approved external URI navigation through UIApplication. */
class IosCoreExternalUriPort : NaviampCoreExternalUriPort {
    override fun open(uri: String) {
        NSURL.URLWithString(uri)?.let(UIApplication.sharedApplication::openURL)
    }
}
