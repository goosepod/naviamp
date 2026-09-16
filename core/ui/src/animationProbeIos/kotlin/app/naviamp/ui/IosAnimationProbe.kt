package app.naviamp.ui

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.*
import platform.posix.*
import platform.UIKit.UIViewController

/** Simulator/OS clock and UIViewController boundary only. Probe behavior is shared. */
@OptIn(ExperimentalForeignApi::class)
fun animationProbeViewController(): UIViewController {
    lateinit var controller: UIViewController
    controller = ComposeUIViewController {
        NaviampIosRasterHost(rootLayer = { controller.view.layer }) {
            NaviampMobileAnimationProbe(cpuNanos = {
                memScoped {
                    val usage = alloc<rusage>()
                    getrusage(RUSAGE_SELF, usage.ptr)
                    (usage.ru_utime.tv_sec + usage.ru_stime.tv_sec) * 1_000_000_000L +
                        (usage.ru_utime.tv_usec + usage.ru_stime.tv_usec) * 1_000L
                }
            }, report = { println("NAVIAMP_ANIMATION_PROBE $it") },
                finished = { println("NAVIAMP_ANIMATION_PROBE COMPLETE"); exit(0) })
        }
    }
    return controller
}
