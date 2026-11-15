package app.naviamp.ios

import app.naviamp.app.NaviampScreenAwakeEffect
import app.naviamp.presentation.AppleScreenAwakeEffect

/** UIKit's application idle timer is the only native operation; Core supplies visible-surface policy. */
class IosScreenAwakeEffect : NaviampScreenAwakeEffect by AppleScreenAwakeEffect()
