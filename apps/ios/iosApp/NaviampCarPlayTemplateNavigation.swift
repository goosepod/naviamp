import CarPlay

/// The stack effects exposed by CPInterfaceController. Core still supplies the desired path.
@MainActor
protocol NaviampCarPlayTemplateStack: AnyObject {
    var templates: [CPTemplate] { get }
    func setRootTemplate(_ template: CPTemplate, animated: Bool, completion: ((Bool, Error?) -> Void)?)
    func pushTemplate(_ template: CPTemplate, animated: Bool, completion: ((Bool, Error?) -> Void)?)
    func popToRootTemplate(animated: Bool, completion: ((Bool, Error?) -> Void)?)
    func pop(to template: CPTemplate, animated: Bool, completion: ((Bool, Error?) -> Void)?)
}

extension CPInterfaceController: NaviampCarPlayTemplateStack {}

/// One observer and stack adapter per native attachment, including when an interface is reused.
@MainActor
final class NaviampCarPlayTemplateNavigation: NSObject, CPInterfaceControllerDelegate,
    @MainActor CPNowPlayingTemplateObserver {
    private let interface: any NaviampCarPlayTemplateStack
    private var active = true
    private var visiblePages: [String: CPTemplate] = [:]
    private(set) var hasInstalledRoot = false
    private var appliedRevision: Int64?
    var operationInFlight = false

    var onStackChanged: (() -> Void)?
    var onRootRejected: (() -> Void)?
    var onNavigationRejected: ((String?) -> Void)?
    var onBack: (([String]) -> Void)?
    var onUpNext: (() -> Void)?
    var onAlbumArtist: (() -> Void)?

    init(interface: any NaviampCarPlayTemplateStack) {
        self.interface = interface
    }

    func invalidate() {
        active = false
        visiblePages.removeAll()
    }

    func retainsPage(_ id: String) -> Bool {
        visiblePages[id] != nil
    }

    func installRoot(_ root: CPTabBarTemplate, appliedRevision: Int64?) {
        guard active, !operationInFlight, !hasInstalledRoot else { return }
        operationInFlight = true
        interface.setRootTemplate(root, animated: false) { [weak self] success, _ in
            guard let self, self.active else { return }
            self.operationInFlight = false
            if success {
                self.hasInstalledRoot = true
                self.appliedRevision = appliedRevision
                self.onStackChanged?()
            } else {
                self.onRootRejected?()
            }
        }
    }

    func reconcile(path: [CPTemplate], pages: [String: CPTemplate], revision: Int64) {
        guard active, hasInstalledRoot, !operationInFlight, appliedRevision != revision, !path.isEmpty else { return }
        let stack = interface.templates
        let prefix = zip(stack, path).prefix { $0.0 === $0.1 }.count
        if prefix == stack.count, prefix == path.count {
            appliedRevision = revision
            return
        }
        let popping = stack.count > prefix
        let target = popping ? path[max(prefix - 1, 0)] : path[stack.count]
        operationInFlight = true
        let complete: (Bool, Error?) -> Void = { [weak self] success, _ in
            guard let self, self.active else { return }
            self.operationInFlight = false
            if success {
                let stack = self.interface.templates
                self.visiblePages = pages.filter { _, template in stack.contains { $0 === template } }
            } else {
                self.appliedRevision = revision
                self.onNavigationRejected?(pages.first { $0.value === target }?.key)
            }
            self.onStackChanged?()
        }
        if popping && target === path[0] {
            // CarPlay raises an Objective-C exception if pop(to:) receives a tab bar template.
            interface.popToRootTemplate(animated: false, completion: complete)
        } else if popping {
            interface.pop(to: target, animated: false, completion: complete)
        } else {
            interface.pushTemplate(target, animated: true, completion: complete)
        }
    }

    func templateDidAppear(_ template: CPTemplate, animated: Bool) {
        guard active, !operationInFlight, !visiblePages.isEmpty else { return }
        let stack = interface.templates
        let topTab = stack.last as? CPTabBarTemplate
        guard stack.last === template || topTab?.templates.contains(where: { $0 === template }) == true else { return }
        let removed = visiblePages.filter { _, page in !stack.contains { $0 === page } }
        for id in removed.keys { visiblePages.removeValue(forKey: id) }
        if !removed.isEmpty { onBack?(removed.keys.sorted()) }
    }

    func nowPlayingTemplateUpNextButtonTapped(_ nowPlayingTemplate: CPNowPlayingTemplate) {
        if active { onUpNext?() }
    }

    func nowPlayingTemplateAlbumArtistButtonTapped(_ nowPlayingTemplate: CPNowPlayingTemplate) {
        if active { onAlbumArtist?() }
    }
}
