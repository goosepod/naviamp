import XCTest

/// Drives the shipped scene through the tvOS remote and system keyboard, without a test-only app entry point.
@MainActor
final class TvosRemoteRecoveryTests: XCTestCase {
    private let app = XCUIApplication(bundleIdentifier: "app.naviamp.tvos")
    private let remote = XCUIRemote.shared

    private var recommendationsCard: XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Recommendations,")).firstMatch
    }

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func control(_ action: String) async throws -> [String: Any] {
        var request = URLRequest(url: URL(string: "http://127.0.0.1:18790/_test/control")!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["action": action])
        let (data, response) = try await URLSession.shared.data(for: request)
        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 200)
        let probe = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.json")
        attachment.name = action + "-cache-probe"
        attachment.lifetime = .keepAlways
        add(attachment)
        return probe
    }

    override func tearDownWithError() throws {
        if (testRun?.failureCount ?? 0) > 0 {
            record("failure")
            let screen = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            screen.name = "failure-system-screen"
            screen.lifetime = .keepAlways
            add(screen)
            print("TV590 failure application state: \(app.state.rawValue)")
        }
    }

    private func record(_ name: String) {
        let tree = XCTAttachment(string: app.debugDescription)
        tree.name = name + "-accessibility"
        tree.lifetime = .keepAlways
        add(tree)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
        print("TV590 \(name)\n\(app.debugDescription)")
    }

    private func field(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func enter(_ identifier: String, _ text: String) {
        XCTAssertTrue(field(identifier).waitForExistence(timeout: 5))
        if !app.keyboards.firstMatch.exists { remote.press(.select) }
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5), "System keyboard for \(identifier)")
        app.typeText(text)
        if !identifier.contains("password") {
            let updatedField = XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "value == %@", text), object: field(identifier)
            )
            XCTAssertEqual(XCTWaiter.wait(for: [updatedField], timeout: 5), .completed,
                           "The native keyboard must publish the typed value for \(identifier)")
        }
        if identifier == "connection-server-url" {
            remote.press(.menu)
            XCTAssertTrue(app.keyboards.firstMatch.waitForNonExistence(timeout: 5))
            XCTAssertEqual(field(identifier).value as? String, text)
            remote.press(.select)
            XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        }
        app.typeText("\n")
    }

    private func verifyBothAccounts(_ stage: String) {
        XCTAssertTrue(app.buttons["Home"].waitForExistence(timeout: 30))
        XCTAssertTrue(app.buttons["Accounts: primary590"].exists)
        let miniPlayerTitle = app.staticTexts["Choose something to play"]
        XCTAssertTrue(miniPlayerTitle.exists)
        let mainMix = app.staticTexts["Recovered main library Mix"]
        XCTAssertTrue(mainMix.waitForExistence(timeout: 20))
        let companion = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Aurral companion,")).firstMatch
        XCTAssertTrue(companion.waitForExistence(timeout: 15))
        record(stage + "-home")
        remote.press(.down)
        remote.press(.down)
        // Relaunch can begin at a different remote-focus position than account creation.
        // The label is not a touch target. Check that the remote has scrolled it
        // into the window above the mini player before navigating from that row.
        func mixIsVisible() -> Bool {
            let frame = mainMix.frame
            return !frame.isEmpty && app.windows.firstMatch.frame.contains(frame) &&
                frame.maxY <= miniPlayerTitle.frame.minY
        }
        for _ in 0..<2 {
            if mixIsVisible() { break }
            remote.press(.down)
        }
        let visibleMix = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in mixIsVisible() }, object: mainMix)
        XCTAssertEqual(XCTWaiter.wait(for: [visibleMix], timeout: 5), .completed)
        record(stage + "-main-library")
        remote.press(.up)
        remote.press(.select)
        record(stage + "-discovery-open")
        XCTAssertTrue(recommendationsCard.waitForExistence(timeout: 10))
        remote.press(.down)
        remote.press(.select)
        let artist = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", "Recovered Aurral discovery")).firstMatch
        for _ in 0..<6 {
            if artist.exists { break }
            remote.press(.down)
        }
        XCTAssertTrue(artist.waitForExistence(timeout: 5))
        record(stage + "-discovery")
        remote.press(.menu)
        XCTAssertTrue(recommendationsCard.waitForExistence(timeout: 5))
        record(stage + "-back")
    }

    private func createAccountsThroughRemote() {
        XCTAssertTrue(app.staticTexts["Set up Naviamp"].waitForExistence(timeout: 20))
        record("initial")
        remote.press(.right)
        remote.press(.right)
        record("aurral-provider-focus")
        remote.press(.select)
        XCTAssertTrue(app.buttons["Aurral"].isSelected)
        remote.press(.left)
        remote.press(.select)
        XCTAssertTrue(app.buttons["Navidrome"].isSelected)
        remote.press(.down)
        remote.press(.down)
        remote.press(.select)
        enter("connection-name", "Main recovery 590")
        enter("connection-server-url", "http://127.0.0.1:18790/main")
        enter("connection-username", "primary590")
        enter("connection-password", "Main!590")
        record("main-entered")
        remote.press(.up)
        remote.press(.up)
        remote.press(.down)
        remote.press(.select)
        XCTAssertTrue(app.buttons["Aurral companion"].isSelected)
        record("companion-enabled")
        remote.press(.down)
        remote.press(.select)
        enter("aurral-companion-connection-server-url", "http://127.0.0.1:18790/companion")
        enter("aurral-companion-connection-username", "secondary590")
        enter("aurral-companion-connection-password", "Companion!590")
        record("companion-entered")
        remote.press(.down)
        remote.press(.select)
        verifyBothAccounts("created")
    }

    private func verifySystemHomeReturn(_ stage: String) {
        XCTAssertTrue(recommendationsCard.exists)
        remote.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 10) ||
                      app.wait(for: .runningBackgroundSuspended, timeout: 5), "Home backgrounds the app")
        app.activate()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        XCTAssertTrue(recommendationsCard.waitForExistence(timeout: 10), "Return retains navigation")
        XCTAssertTrue(app.buttons["Accounts: primary590"].exists)
        XCTAssertTrue(app.staticTexts["Choose something to play"].exists, "Return must not start playback")
        record(stage + "-home-return")
    }

    private func verifyRemotePlayback() throws {
        // Start from the documented launch focus instead of guessing focus after discovery/Home.
        app.terminate()
        XCTAssertEqual(app.state, .notRunning)
        app.launch()
        XCTAssertTrue(app.buttons["Home"].waitForExistence(timeout: 30))
        XCTAssertTrue(app.staticTexts["Recovered main library Mix"].waitForExistence(timeout: 20))
        remote.press(.down)
        remote.press(.down)
        record("remote-mix-focused")
        remote.press(.select)
        record("remote-play-started")
        XCTAssertTrue(app.buttons["Pause"].waitForExistence(timeout: 20), "Native BASS playback starts from the main mix")
        try verifyPlaybackClockAdvances()
        remote.press(.playPause)
        try verifyPlaybackClockRemainsPaused()
        record("remote-paused")
        remote.press(.playPause)
        try verifyPlaybackClockAdvances()
        remote.press(.playPause, forDuration: 1.2)
        try verifyPlaybackClockRemainsPaused() // A held media button toggles once.
        remote.press(.menu, forDuration: 1.5) // Apple's direct Home gesture, independent of the Now Playing banner's TV-button action.
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 10) ||
                      app.wait(for: .runningBackgroundSuspended, timeout: 5))
        app.activate()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        try verifyPlaybackClockRemainsPaused()
        // The first remote input after return is MediaPlayPause, without a directional-focus workaround.
        remote.press(.playPause)
        try verifyPlaybackClockAdvances()
        remote.press(.playPause)
        try verifyPlaybackClockRemainsPaused()
        record("remote-home-return-paused")
    }

    private func verifyPlaybackClockAdvances() throws {
        let position = field("Playback position")
        let before = try XCTUnwrap(position.value as? String)
        let changed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == true AND value != nil AND value != %@", before), object: position)
        XCTAssertEqual(XCTWaiter.wait(for: [changed], timeout: 8), .completed, "Native playback clock advances")
        let initialPercent = try XCTUnwrap(Double(before.replacingOccurrences(of: "%", with: "")))
        let after = try XCTUnwrap(position.value as? String)
        let finalPercent = try XCTUnwrap(Double(after.replacingOccurrences(of: "%", with: "")))
        XCTAssertGreaterThan(finalPercent, initialPercent, "Clock moves forward, rather than resetting")
    }

    private func verifyPlaybackClockRemainsPaused() throws {
        let position = field("Playback position")
        var last = try XCTUnwrap(position.value as? String)
        var stableSince = Date()
        // Accessibility may first publish a final cursor sample after pause/foreground return.
        // Require three continuous seconds of stability. An advancing 180-second fixture changes
        // its integer percentage every 1.8 seconds, so continued playback cannot satisfy this.
        let stable = XCTNSPredicateExpectation(predicate: NSPredicate { element, _ in
            guard let element = element as? XCUIElement, element.exists,
                  let value = element.value as? String else { return false }
            if value != last { last = value; stableSince = Date() }
            return Date().timeIntervalSince(stableSince) >= 3
        }, object: position)
        XCTAssertEqual(XCTWaiter.wait(for: [stable], timeout: 8), .completed, "Paused clock stays still")
    }

    private func playbackPercent() throws -> Double {
        let value = try XCTUnwrap(field("Playback position").value as? String)
        return try XCTUnwrap(Double(value.replacingOccurrences(of: "%", with: "")))
    }

    private func waitForForegroundClockRefresh(_ description: String, matching predicate: @escaping (Double) -> Bool) {
        let refreshed = XCTNSPredicateExpectation(predicate: NSPredicate { element, _ in
            guard let value = (element as? XCUIElement)?.value as? String,
                  let current = Double(value.replacingOccurrences(of: "%", with: "")) else { return false }
            return predicate(current)
        }, object: field("Playback position"))
        XCTAssertEqual(XCTWaiter.wait(for: [refreshed], timeout: 8), .completed,
                       description)
    }

    private func openSystemNowPlaying() -> XCUIApplication {
        remote.press(.home, forDuration: 1.5)
        let system = XCUIApplication(bundleIdentifier: "com.apple.TVSystemUIService")
        let settingsTab = system.buttons["com.apple.status.controlCenterItem"]
        let nowPlayingTab = system.buttons["com.apple.TVSystemUIService.nowPlaying.menuitem"]
        // Some runtimes open the general settings tab. Now Playing is immediately to its left.
        if settingsTab.exists && settingsTab.hasFocus && nowPlayingTab.exists {
            remote.press(.left)
        }
        return system
    }

    private func verifySystemMediaInBackground() async throws {
        // This path cannot reach the Compose media-key fallback: PineBoard owns foreground input.
        remote.press(.playPause)
        try verifyPlaybackClockAdvances()
        let beforeHome = try playbackPercent()
        remote.press(.menu, forDuration: 1.5)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 10) ||
                      app.wait(for: .runningBackgroundSuspended, timeout: 5))
        try await Task.sleep(nanoseconds: 4_000_000_000)
        remote.press(.playPause) // Native command center pauses the background player.
        try await Task.sleep(nanoseconds: 1_000_000_000)
        app.activate()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        // Listening mode may keep transport controls hidden after a background return. The
        // accessible clock proves pause directly, without sending another foreground input.
        waitForForegroundClockRefresh("Foreground accessibility catches up with background playback") { $0 > beforeHome }
        try verifyPlaybackClockRemainsPaused()
        XCTAssertGreaterThan(try playbackPercent(), beforeHome, "Playback advanced while the app was backgrounded")
        record("system-background-playback-paused")

        let beforeResume = try playbackPercent()
        remote.press(.menu, forDuration: 1.5)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 10) ||
                      app.wait(for: .runningBackgroundSuspended, timeout: 5))
        let system = openSystemNowPlaying()
        print("TV590 control-center\n" + system.debugDescription)
        let systemScreen = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        systemScreen.name = "system-control-center"
        systemScreen.lifetime = .keepAlways
        add(systemScreen)
        XCTAssertTrue(system.buttons["Play"].waitForExistence(timeout: 10))
        remote.press(.playPause)
        try await Task.sleep(nanoseconds: 4_000_000_000)
        XCTAssertNotEqual(app.state, .runningForeground)
        XCTAssertTrue(system.buttons["Pause"].waitForExistence(timeout: 10))
        remote.press(.playPause)
        try await Task.sleep(nanoseconds: 1_000_000_000)
        app.activate()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        waitForForegroundClockRefresh("Foreground accessibility catches up with background playback") { $0 > beforeResume }
        try verifyPlaybackClockRemainsPaused()
        XCTAssertGreaterThan(try playbackPercent(), beforeResume, "System media commands resume background audio")
        record("system-background-resume-paused")
    }

    private func verifySystemTrackNavigation() throws {
        let beforeNavigation = try playbackPercent()
        // Only Control Center receives the directional/select events while Naviamp is backgrounded.
        remote.press(.menu, forDuration: 1.5)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 10) ||
                      app.wait(for: .runningBackgroundSuspended, timeout: 5))
        let system = openSystemNowPlaying()
        let title = system.buttons["track-info"]
        XCTAssertTrue(title.waitForExistence(timeout: 10))
        let initialTitle = title.label
        let first = "Recovery proof"
        let second = "Recovery second track"
        XCTAssertTrue(initialTitle.hasPrefix(first + ",") || initialTitle.hasPrefix(second + ","))
        let nextTitle = initialTitle.hasPrefix(first + ",") ? second : first

        func button(_ name: String) -> XCUIElement {
            system.buttons["com.apple.TVSystemUIService.nowPlaying." + name + "Button"]
        }

        func select(_ target: XCUIElement, via directions: [XCUIRemote.Button]) {
            for direction in directions {
                if target.hasFocus { break }
                remote.press(direction)
            }
            XCTAssertTrue(target.hasFocus, "Control Center must focus \(target.identifier)")
            XCTAssertTrue(target.isEnabled)
            XCTAssertTrue(app.state == .runningBackground || app.state == .runningBackgroundSuspended)
            remote.press(.select)
        }

        let timeline = system.otherElements["transport-bar"]
        func waitForElapsed(_ condition: @escaping (Int) -> Bool, description: String) {
            let elapsed = XCTNSPredicateExpectation(predicate: NSPredicate { element, _ in
                guard let clock = (element as? XCUIElement)?.label.split(separator: " ").first else { return false }
                let components = clock.split(separator: ":").compactMap { Int($0) }
                return components.count == 2 && condition(components[0] * 60 + components[1])
            }, object: timeline)
            XCTAssertEqual(XCTWaiter.wait(for: [elapsed], timeout: 12), .completed, description)
        }

        select(button("next"), via: [.down, .down, .right, .right])
        XCTAssertTrue(title.wait(for: \.label, toEqual: nextTitle + ", Recovered main library", timeout: 12))
        XCTAssertNotEqual(app.state, .runningForeground)
        print("TV590 system-next delivered: \(title.label)")

        // Previous restarts a track after three seconds. Pause at a known late position
        // and verify the rewind before selecting Previous from the start of that track.
        let playPause = button("playPause")
        XCTAssertTrue(playPause.wait(for: \.label, toEqual: "Pause", timeout: 10))
        waitForElapsed({ $0 >= 8 }, description: "The new track must play past the restart threshold")
        select(playPause, via: [.left])
        XCTAssertTrue(playPause.wait(for: \.label, toEqual: "Play", timeout: 10))
        remote.press(.up)
        print("TV590 Control Center paused, timeline focused: \(timeline.hasFocus)\n\(system.debugDescription)")
        let previous = button("previous")
        select(previous, via: [.down, .left, .down, .left])
        waitForElapsed({ $0 == 0 }, description: "System Previous rewinds the paused track")
        XCTAssertEqual(playPause.label, "Play")
        XCTAssertTrue(title.label.hasPrefix(nextTitle + ","))
        print("TV590 system-previous restarted: \(title.label)")
        select(previous, via: [])
        XCTAssertTrue(title.wait(for: \.label, toEqual: initialTitle, timeout: 12))
        XCTAssertNotEqual(app.state, .runningForeground)
        print("TV590 system-previous delivered: \(title.label)")
        app.activate()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        // Previous selected a fresh stream. The accessibility clock may still expose the
        // pre-background cursor until the new track's first foreground sample is published.
        waitForForegroundClockRefresh("Foreground clock reflects the restarted track") { $0 < beforeNavigation }
        try verifyPlaybackClockAdvances()
        record("system-track-navigation-return")
    }

    private func press(_ button: XCUIRemote.Button, _ count: Int) {
        for _ in 0..<count { remote.press(button) }
    }

    /// Compose focus is not exposed to XCTest, so this follows the fixed TV layout: the top
    /// navigation (Home ... Search) and the editor's three letter rows above its action row.
    private func openLiveSearchEditor() {
        app.terminate()
        app.launch()
        let search = app.buttons["Search"].firstMatch
        if !search.waitForExistence(timeout: 5) { remote.press(.menu) }
        XCTAssertTrue(search.waitForExistence(timeout: 20), "Shell navigation")
        press(.up, 3)
        press(.left, 6)
        press(.right, 5)
        remote.press(.select)
        XCTAssertTrue(field("television-search-query").waitForExistence(timeout: 10), "Search query field")
        remote.press(.select)
        XCTAssertTrue(app.buttons["Backspace"].firstMatch.waitForExistence(timeout: 5), "Search editor")
    }

    /// Opt-in live check of the Search editor's remote actions on a configured simulator
    /// (TEST_RUNNER_TV590_LIVE=1). XCTest cannot type a hardware Return into the Compose editor
    /// because no UIKit text input holds keyboard focus; the shared TV workflow test covers Return.
    func testLiveSearchEditorRemoteActions() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["TV590_LIVE"] == "1", "Live configured simulator only")
        openLiveSearchEditor()
        let draft = { (text: String) in self.app.staticTexts[text].firstMatch }
        let editor = app.buttons["Backspace"].firstMatch
        press(.select, 3)
        XCTAssertTrue(draft("aaa").waitForExistence(timeout: 5), "Remote Select types the focused letter")
        record("live-search-remote-letters")
        press(.down, 3) // Space
        remote.press(.right) // Backspace
        remote.press(.select)
        XCTAssertTrue(draft("aa").waitForExistence(timeout: 5), "Backspace deletes a character")
        XCTAssertTrue(editor.exists, "Backspace keeps the editor open")
        record("live-search-backspace")
        press(.right, 2) // System keyboard, Cancel
        remote.press(.select)
        XCTAssertTrue(editor.waitForNonExistence(timeout: 5), "Cancel closes the editor")
        XCTAssertFalse(draft("aa").exists, "Cancel does not submit the draft")
        record("live-search-cancel")
        remote.press(.select)
        XCTAssertTrue(editor.waitForExistence(timeout: 5), "Select reopens the editor")
        remote.press(.select)
        press(.down, 3)
        press(.right, 4) // Search
        remote.press(.select)
        XCTAssertTrue(editor.waitForNonExistence(timeout: 5), "Search submits the draft")
        record("live-search-submitted")
    }

    /// Opt-in live helper that toggles playback and opens Now Playing for animation measurements (TEST_RUNNER_TV590_LIVE=1).
    func testLiveTogglePlayback() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["TV590_LIVE"] == "1", "Live configured simulator only")
        app.activate()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        remote.press(.playPause)
        if app.buttons["Now Playing"].firstMatch.exists { // Otherwise the full-screen player is already open.
            press(.up, 3)
            press(.left, 6)
            remote.press(.right) // Now Playing
            remote.press(.select)
        }
        record("live-toggle-playback")
    }

    func testMainAndCompanionRemoteRecovery() async throws {
        let fresh = try await control("create")
        XCTAssertEqual(fresh["databaseExists"] as? Bool, false)
        app.launch()
        createAccountsThroughRemote()
        verifySystemHomeReturn("created")
        app.terminate()
        XCTAssertEqual(app.state, .notRunning)

        let saved = try await control("relaunch")
        XCTAssertEqual(saved["databaseExists"] as? Bool, true)
        app.launch()
        verifyBothAccounts("relaunched")
        app.terminate()
        XCTAssertEqual(app.state, .notRunning)

        let removed = try await control("purge")
        XCTAssertEqual(removed["databaseExists"] as? Bool, false)
        let purged = try await control("purged")
        XCTAssertEqual(purged["databaseExists"] as? Bool, false)
        XCTAssertEqual(purged["recoveryRecordExists"] as? Bool, true)
        app.launch()
        verifyBothAccounts("purged")
        verifySystemHomeReturn("purged")
        try verifyRemotePlayback()
        try await verifySystemMediaInBackground()
        try verifySystemTrackNavigation()
        app.terminate()
        XCTAssertEqual(app.state, .notRunning)
        let rebuilt = try await control("finish")
        XCTAssertEqual(rebuilt["databaseExists"] as? Bool, true)
    }

}
