import SwiftUI

@main
struct NaviampApp: App {
    @UIApplicationDelegateAdaptor(NaviampApplicationDelegate.self) private var applicationDelegate

    var body: some Scene {
        WindowGroup {
            NaviampRootView()
                .ignoresSafeArea()
        }
    }
}
