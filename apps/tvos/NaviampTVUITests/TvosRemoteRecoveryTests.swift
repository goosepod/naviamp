import XCTest

/// Drives the shipped scene through the tvOS remote and system keyboard, without a test-only app entry point.
@MainActor
final class TvosRemoteRecoveryTests: XCTestCase {
    private let app = XCUIApplication(bundleIdentifier: "app.naviamp.tvos")
    private let remote = XCUIRemote.shared

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
        if (testRun?.failureCount ?? 0) > 0 { record("failure") }
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
            XCTAssertEqual(field(identifier).value as? String, text)
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
        XCTAssertTrue(app.staticTexts["Choose something to play"].exists)
        let mainMix = app.staticTexts["Recovered main library Mix"]
        XCTAssertTrue(mainMix.waitForExistence(timeout: 20))
        let companion = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Aurral companion,")).firstMatch
        XCTAssertTrue(companion.waitForExistence(timeout: 15))
        record(stage + "-home")
        remote.press(.down)
        remote.press(.down)
        XCTAssertTrue(mainMix.wait(for: \.isHittable, toEqual: true, timeout: 5))
        record(stage + "-main-library")
        remote.press(.up)
        remote.press(.select)
        record(stage + "-discovery-open")
        XCTAssertTrue(app.staticTexts["Recommendations"].waitForExistence(timeout: 10))
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
        XCTAssertTrue(app.staticTexts["Recommendations"].waitForExistence(timeout: 5))
        record(stage + "-back")
    }

    private func createAccountsThroughRemote() {
        XCTAssertTrue(app.staticTexts["Set up Naviamp"].waitForExistence(timeout: 20))
        record("initial")
        remote.press(.right)
        remote.press(.right)
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

    func testMainAndCompanionRemoteRecovery() async throws {
        let fresh = try await control("create")
        XCTAssertEqual(fresh["databaseExists"] as? Bool, false)
        app.launch()
        createAccountsThroughRemote()
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
        app.terminate()
        XCTAssertEqual(app.state, .notRunning)
        let rebuilt = try await control("finish")
        XCTAssertEqual(rebuilt["databaseExists"] as? Bool, true)
    }

}
