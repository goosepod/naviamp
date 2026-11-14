import CarPlay
import NaviampShared

/// CarPlay scene and template boundary. Playback stays in the application's existing Core runtime.
@MainActor
final class NaviampCarPlaySceneDelegate: NSObject, CPTemplateApplicationSceneDelegate {
    private var application: NaviampIosApplication?
    private var interfaceController: CPInterfaceController?

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController
    ) {
        application = NaviampApplicationDelegate.runtime.application()
        self.interfaceController = interfaceController
        interfaceController.setRootTemplate(CPNowPlayingTemplate.shared, animated: false, completion: nil)
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnectInterfaceController interfaceController: CPInterfaceController
    ) {
        guard self.interfaceController === interfaceController else { return }
        self.interfaceController = nil
        application = nil
    }
}
