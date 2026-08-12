import XCTest

final class IosKeychainCredentialProtectorTests: XCTestCase {
    func testProtectRevealAndTamperFailureUseTheRealKeychain() throws {
        let protector = IosKeychainVault()
        let secret = "naviamp-keychain-test-\(UUID().uuidString)"
        let first = try XCTUnwrap(protector.store(secret))
        let second = try XCTUnwrap(protector.store(secret))
        defer {
            protector.remove(first)
            protector.remove(second)
        }

        XCTAssertTrue(protector.isReference(first))
        XCTAssertFalse(first.contains(secret))
        XCTAssertNotEqual(first, second)
        XCTAssertEqual(secret, protector.load(first))
        XCTAssertEqual(secret, protector.load(second))
        XCTAssertNil(protector.load("keychain:missing-\(UUID().uuidString)"))
    }
}
