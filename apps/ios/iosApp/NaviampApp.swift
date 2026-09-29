import Intents
import NaviampShared
import SwiftUI

@main
struct NaviampApp: App {
    @UIApplicationDelegateAdaptor(NaviampSiriDelegate.self) private var siriDelegate

    var body: some Scene {
        WindowGroup {
            NaviampRootView(application: siriDelegate.coreApplication)
                .ignoresSafeArea()
        }
    }
}

/** Apple application lifecycle and SiriKit routing. Core owns search and playback. */
final class NaviampSiriDelegate: NSObject, UIApplicationDelegate {
    lazy var coreApplication = NaviampIosApplication(
        applicationSupportDirectory: IosApplicationDirectories.supportDirectory(),
        credentialProtector: IosKeychainCredentialProtector()
    )

    func application(_ application: UIApplication, handlerFor intent: INIntent) -> Any? {
        guard intent is INPlayMediaIntent else { return nil }
        return NaviampSiriMediaHandler(application: coreApplication)
    }
}

/** Translates SiriKit media types and results at the native API boundary. */
final class NaviampSiriMediaHandler: NSObject, INPlayMediaIntentHandling {
    private let application: NaviampIosApplication

    init(application: NaviampIosApplication) {
        self.application = application
    }

    func handle(intent: INPlayMediaIntent, completion: @escaping (INPlayMediaIntentResponse) -> Void) {
        let search = intent.mediaSearch
        let item = intent.mediaContainer ?? intent.mediaItems?.first
        let name = search?.mediaName ?? item?.title
        let type = search?.mediaType == .unknown ? item?.type : search?.mediaType
        guard let name, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let kind = namedKind(for: type ?? .unknown) else {
            completion(INPlayMediaIntentResponse(code: .failureUnknownMediaType, userActivity: nil))
            return
        }
        application.playNamedMedia(kind: kind, name: name) { result in
            let code: INPlayMediaIntentResponseCode
            if result.status == PresentationNaviampNamedMediaStatus.started {
                code = .success
            } else if result.status == PresentationNaviampNamedMediaStatus.unsupported {
                code = .failureUnknownMediaType
            } else if result.status == PresentationNaviampNamedMediaStatus.empty {
                code = .failureNoUnplayedContent
            } else {
                code = .failure
            }
            completion(INPlayMediaIntentResponse(code: code, userActivity: nil))
        }
    }

    private func namedKind(for type: INMediaItemType) -> DomainNamedMediaKind? {
        switch type {
        case .artist: return .artist
        case .album: return .album
        case .playlist: return .playlist
        case .musicStation, .algorithmicRadioStation: return .artistradio
        default: return nil
        }
    }
}
