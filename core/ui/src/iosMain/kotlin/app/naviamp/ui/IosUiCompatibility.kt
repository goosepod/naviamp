package app.naviamp.ui

import androidx.compose.runtime.Composable
import platform.QuartzCore.CALayer

/** Keep the original iOS host API while sharing the native Apple effects with tvOS. */
@Composable
fun NaviampIosRasterHost(rootLayer: () -> CALayer?, content: @Composable () -> Unit) =
    NaviampAppleRasterHost(rootLayer, content)

fun setIosPlatformCoverArtByteLoader(loader: suspend (String) -> ByteArray?) = setNaviampCoverArtByteLoader(loader)
fun resetIosPlatformCoverArtByteLoader() = resetNaviampCoverArtByteLoader()
