import CarPlay
import XCTest

@MainActor
private final class RecordingCarPlayStack: NaviampCarPlayTemplateStack {
    enum Effect: Equatable { case root, push, pop, popToRoot }
    struct Request {
        let effect: Effect
        let target: CPTemplate
        let completion: ((Bool, Error?) -> Void)?
    }

    var templates: [CPTemplate] = []
    private(set) var requests: [Request] = []

    func setRootTemplate(_ template: CPTemplate, animated: Bool, completion: ((Bool, Error?) -> Void)?) {
        requests.append(Request(effect: .root, target: template, completion: completion))
    }

    func pushTemplate(_ template: CPTemplate, animated: Bool, completion: ((Bool, Error?) -> Void)?) {
        requests.append(Request(effect: .push, target: template, completion: completion))
    }

    func popToRootTemplate(animated: Bool, completion: ((Bool, Error?) -> Void)?) {
        guard let root = templates.first else {
            XCTFail("Cannot pop to the root of an empty stack")
            completion?(false, nil)
            return
        }
        requests.append(Request(effect: .popToRoot, target: root, completion: completion))
    }

    func pop(to template: CPTemplate, animated: Bool, completion: ((Bool, Error?) -> Void)?) {
        XCTAssertFalse(template is CPTabBarTemplate, "CarPlay rejects a tab bar as a pop(to:) target")
        requests.append(Request(effect: .pop, target: template, completion: completion))
    }

    func complete(_ index: Int, success: Bool, beforeCompletion: (() -> Void)? = nil) {
        let request = requests[index]
        if success {
            switch request.effect {
            case .root, .popToRoot: templates = [request.target]
            case .push: templates.append(request.target)
            case .pop:
                if let target = templates.firstIndex(where: { $0 === request.target }) {
                    templates = Array(templates.prefix(through: target))
                }
            }
            beforeCompletion?()
        }
        request.completion?(success, nil)
    }
}

final class NaviampCarPlayTemplateNavigationTests: XCTestCase {
    @MainActor
    func testRejectedRootReportsFailureWithoutStartingStackEffects() {
        let stack = RecordingCarPlayStack()
        let adapter = NaviampCarPlayTemplateNavigation(interface: stack)
        let root = makeRoot()
        let page = makePage()
        var rejections = 0
        var changes = 0
        adapter.onRootRejected = { rejections += 1 }
        adapter.onStackChanged = { changes += 1 }

        adapter.installRoot(root, appliedRevision: nil)
        adapter.reconcile(path: [root, page], pages: ["album": page], revision: 1)
        stack.complete(0, success: false)
        adapter.reconcile(path: [root, page], pages: ["album": page], revision: 1)

        XCTAssertEqual(stack.requests.map(\.effect), [.root])
        XCTAssertEqual(rejections, 1)
        XCTAssertEqual(changes, 0)
        XCTAssertFalse(adapter.hasInstalledRoot)
        XCTAssertFalse(adapter.operationInFlight)
    }

    @MainActor
    func testRejectedPushReportsPageAndOnlyANewRevisionCanTryAgain() {
        let stack = RecordingCarPlayStack()
        let adapter = NaviampCarPlayTemplateNavigation(interface: stack)
        let root = makeRoot()
        let page = makePage()
        var rejected: [String?] = []
        adapter.onNavigationRejected = { rejected.append($0) }
        adapter.installRoot(root, appliedRevision: 0)
        stack.complete(0, success: true)

        adapter.reconcile(path: [root, page], pages: ["album": page], revision: 1)
        stack.complete(1, success: false)
        adapter.reconcile(path: [root, page], pages: ["album": page], revision: 1)
        XCTAssertEqual(rejected, ["album"])
        XCTAssertFalse(adapter.retainsPage("album"))
        XCTAssertEqual(stack.requests.map(\.effect), [.root, .push])

        adapter.reconcile(path: [root, page], pages: ["album": page], revision: 2)
        XCTAssertEqual(stack.requests.map(\.effect), [.root, .push, .push])
    }

    @MainActor
    func testRejectedPopPreservesVisiblePagesAndReportsTheTarget() {
        let stack = RecordingCarPlayStack()
        let adapter = NaviampCarPlayTemplateNavigation(interface: stack)
        let root = makeRoot()
        let album = makePage()
        let queue = makePage()
        let pages: [String: CPTemplate] = ["album": album, "queue": queue]
        var rejected: [String?] = []
        var backEvents: [[String]] = []
        adapter.onNavigationRejected = { rejected.append($0) }
        adapter.onBack = { backEvents.append($0) }
        adapter.installRoot(root, appliedRevision: 0)
        stack.complete(0, success: true)
        adapter.reconcile(path: [root, album, queue], pages: pages, revision: 1)
        stack.complete(1, success: true)
        adapter.reconcile(path: [root, album, queue], pages: pages, revision: 1)
        stack.complete(2, success: true)
        adapter.reconcile(path: [root, album], pages: pages, revision: 2)
        stack.complete(3, success: false)
        adapter.reconcile(path: [root, album], pages: pages, revision: 2)

        XCTAssertEqual(stack.requests.map(\.effect), [.root, .push, .push, .pop])
        XCTAssertEqual(rejected, ["album"])
        XCTAssertTrue(adapter.retainsPage("queue"))
        XCTAssertTrue(backEvents.isEmpty)
    }

    @MainActor
    func testBackReportsRemovedPagesOnceAndIgnoresUnrelatedAppearances() {
        let stack = RecordingCarPlayStack()
        let adapter = NaviampCarPlayTemplateNavigation(interface: stack)
        let root = makeRoot()
        let album = makePage()
        let queue = makePage()
        let pages: [String: CPTemplate] = ["album": album, "queue": queue]
        var removed: [[String]] = []
        adapter.onBack = { removed.append($0) }
        adapter.installRoot(root, appliedRevision: 0)
        stack.complete(0, success: true)
        adapter.reconcile(path: [root, album, queue], pages: pages, revision: 1)
        stack.complete(1, success: true)
        adapter.reconcile(path: [root, album, queue], pages: pages, revision: 1)
        stack.complete(2, success: true)
        adapter.reconcile(path: [root, album, queue], pages: pages, revision: 1)

        stack.templates = [root, album]
        adapter.templateDidAppear(queue, animated: true)
        XCTAssertTrue(removed.isEmpty)
        adapter.templateDidAppear(album, animated: true)
        adapter.templateDidAppear(album, animated: true)
        adapter.reconcile(path: [root, album], pages: ["album": album], revision: 1)

        XCTAssertEqual(removed, [["queue"]])
        XCTAssertTrue(adapter.retainsPage("album"))
        XCTAssertFalse(adapter.retainsPage("queue"))
        XCTAssertEqual(stack.requests.map(\.effect), [.root, .push, .push])

        stack.templates = [root]
        adapter.templateDidAppear(root.templates[0], animated: true)
        XCTAssertEqual(removed, [["queue"], ["album"]])
    }

    @MainActor
    func testReplacingPathAboveTabBarUsesTheNativeRootPop() {
        let stack = RecordingCarPlayStack()
        let adapter = NaviampCarPlayTemplateNavigation(interface: stack)
        let root = makeRoot()
        let queue = makePage()
        let album = makePage()
        let nowPlaying = CPNowPlayingTemplate.shared
        adapter.onBack = { _ in XCTFail("An adapter-driven pop must not report user Back") }
        adapter.installRoot(root, appliedRevision: 0)
        stack.complete(0, success: true)
        adapter.reconcile(path: [root, album, nowPlaying], pages: ["album": album], revision: 1)
        stack.complete(1, success: true)
        adapter.reconcile(path: [root, album, nowPlaying], pages: ["album": album], revision: 1)
        stack.complete(2, success: true)

        adapter.reconcile(path: [root, queue], pages: ["queue": queue], revision: 2)
        stack.complete(3, success: true) { adapter.templateDidAppear(root, animated: false) }
        adapter.reconcile(path: [root, queue], pages: ["queue": queue], revision: 2)
        stack.complete(4, success: true)

        XCTAssertEqual(stack.requests.map(\.effect), [.root, .push, .push, .popToRoot, .push])
        XCTAssertTrue(stack.templates.last === queue)
    }

    @MainActor
    func testUpNextPushesAboveSystemNowPlayingAndBackRemovesOnlyTheQueue() {
        let stack = RecordingCarPlayStack()
        let adapter = NaviampCarPlayTemplateNavigation(interface: stack)
        let root = makeRoot()
        let nowPlaying = CPNowPlayingTemplate.shared
        let queue = makePage()
        var upNextRequests = 0
        var removedPages: [[String]] = []
        adapter.onUpNext = { upNextRequests += 1 }
        adapter.onBack = { removedPages.append($0) }
        adapter.installRoot(root, appliedRevision: 0)
        stack.complete(0, success: true)

        // CarPlay can present Now Playing itself before Core issues a navigation request.
        stack.templates.append(nowPlaying)
        adapter.nowPlayingTemplateUpNextButtonTapped(nowPlaying)
        XCTAssertEqual(upNextRequests, 1)
        adapter.reconcile(path: [root, nowPlaying, queue], pages: ["queue": queue], revision: 1)
        XCTAssertEqual(stack.requests.map(\.effect), [.root, .push])
        XCTAssertTrue(stack.requests.last?.target === queue)
        stack.complete(1, success: true)
        adapter.reconcile(path: [root, nowPlaying, queue], pages: ["queue": queue], revision: 1)

        stack.templates.removeLast()
        adapter.templateDidAppear(nowPlaying, animated: true)
        adapter.reconcile(path: [root, nowPlaying], pages: [:], revision: 1)
        XCTAssertEqual(removedPages, [["queue"]])
        XCTAssertTrue(stack.templates.last === nowPlaying)
        XCTAssertEqual(stack.requests.map(\.effect), [.root, .push])
    }

    @MainActor
    func testReconnectOnTheSameInterfaceIgnoresOldEffectsAndObserverCalls() {
        for success in [false, true] {
            let stack = RecordingCarPlayStack()
            let old = NaviampCarPlayTemplateNavigation(interface: stack)
            let oldRoot = makeRoot()
            let album = makePage()
            old.installRoot(oldRoot, appliedRevision: 0)
            stack.complete(0, success: true)
            old.reconcile(path: [oldRoot, album], pages: ["album": album], revision: 1)
            stack.complete(1, success: true)
            old.reconcile(path: [oldRoot], pages: ["album": album], revision: 2)
            old.onStackChanged = { XCTFail("Stale stack completion triggered a render") }
            old.onNavigationRejected = { _ in XCTFail("Stale rejection reached Core") }
            old.onBack = { _ in XCTFail("Stale Back reached Core") }
            old.onUpNext = { XCTFail("Stale Up Next reached Core") }
            old.invalidate()

            let new = NaviampCarPlayTemplateNavigation(interface: stack)
            let newRoot = makeRoot()
            var upNext = 0
            new.onUpNext = { upNext += 1 }
            new.installRoot(newRoot, appliedRevision: 0)
            stack.complete(2, success: success)
            old.templateDidAppear(oldRoot, animated: true)
            old.nowPlayingTemplateUpNextButtonTapped(.shared)

            XCTAssertTrue(new.operationInFlight)
            stack.complete(3, success: true)
            new.nowPlayingTemplateUpNextButtonTapped(.shared)
            XCTAssertTrue(stack.templates.last === newRoot)
            XCTAssertEqual(upNext, 1)
        }
    }

    @MainActor
    private func makeRoot() -> CPTabBarTemplate {
        CPTabBarTemplate(templates: [makePage()])
    }

    @MainActor
    private func makePage() -> CPListTemplate {
        CPListTemplate(title: "Library", sections: [])
    }
}
