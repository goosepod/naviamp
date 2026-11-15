package app.naviamp.ios.platform

import app.naviamp.presentation.NaviampCoreExternalUriPort
import app.naviamp.presentation.AppleCoreExternalUriPort

/** Executes Core-approved external URI navigation through UIApplication. */
class IosCoreExternalUriPort : NaviampCoreExternalUriPort by AppleCoreExternalUriPort()
