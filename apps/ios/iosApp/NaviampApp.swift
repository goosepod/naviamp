import SwiftUI

@main
struct NaviampApp: App {
    init() {
        let version = NaviampBassProbeVersion()
        let initialized = NaviampBassProbeInitialize()
        let error = NaviampBassProbeLastError()
        NSLog(
            "Naviamp BASS simulator probe version=%u.%u.%u.%u initialized=%@ error=%d",
            (version >> 24) & 0xff,
            (version >> 16) & 0xff,
            (version >> 8) & 0xff,
            version & 0xff,
            initialized ? "true" : "false",
            error
        )
        if initialized {
            NaviampBassProbeFree()
        }
    }

    var body: some Scene {
        WindowGroup {
            NaviampRootView()
                .ignoresSafeArea()
        }
    }
}
