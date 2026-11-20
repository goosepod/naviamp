import XCTest
import Security
import NaviampTvShared

final class TvosRecoveryEffectsTests: XCTestCase {
    func testAuthenticatedEncryptionSurvivesAdapterRecreationAndRejectsTamperingAndWrongNamespace() throws {
        let namespace = "app.naviamp.590.tests." + UUID().uuidString
        let box = TvosStorageSecretBox(namespace: namespace)
        defer { XCTAssertTrue(box.clearKey()) }
        let clear = Data("main-password and Aurral-companion-password".utf8)
        let first = try XCTUnwrap(box.seal(plaintext: clear.kotlinBytes))
        let second = try XCTUnwrap(box.seal(plaintext: clear.kotlinBytes))
        var keyItems: CFTypeRef?
        let keyQuery: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: namespace + ".storage-recovery.aes-gcm.v1",
            kSecReturnAttributes as String: true, kSecMatchLimit as String: kSecMatchLimitAll]
        XCTAssertEqual(SecItemCopyMatching(keyQuery as CFDictionary, &keyItems), errSecSuccess)
        XCTAssertEqual((keyItems as? [[String: Any]])?.count, 1)
        XCTAssertNotEqual(first.ciphertext.nativeData, second.ciphertext.nativeData)
        XCTAssertEqual(TvosStorageSecretBox(namespace: namespace).open(keyReference: first.keyReference, ciphertext: first.ciphertext)?.nativeData, clear)
        var tampered = first.ciphertext.nativeData
        tampered[tampered.startIndex] ^= 1
        XCTAssertNil(box.open(keyReference: first.keyReference, ciphertext: tampered.kotlinBytes))
        XCTAssertNil(box.open(keyReference: "unknown-key", ciphertext: first.ciphertext))
        let other = TvosStorageSecretBox(namespace: namespace + ".other")
        XCTAssertNil(other.open(keyReference: first.keyReference, ciphertext: first.ciphertext))
        XCTAssertTrue(box.clearKey())
        XCTAssertNil(box.open(keyReference: first.keyReference, ciphertext: first.ciphertext))
        // A failed decrypt must not manufacture a new key.
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: namespace + ".storage-recovery.aes-gcm.v1",
            kSecAttrAccount as String: "primary-v1"]
        XCTAssertEqual(SecItemCopyMatching(query as CFDictionary, nil), errSecItemNotFound)
    }

    func testPreferenceReadbackConflictMalformedValueWholeDomainSizeAndScopedReset() throws {
        let name = "app.naviamp.590.preferences.tests." + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        let port = TvosRecoveryDocument(defaults: defaults, domain: name)
        XCTAssertFalse(port.read().exists)
        let emptySize = port.projectedDomainBytes(encoded: "ciphertext")
        defaults.set(String(repeating: "x", count: 20_000), forKey: "unrelated")
        XCTAssertGreaterThan(port.projectedDomainBytes(encoded: "ciphertext"), emptySize + 19_000)
        XCTAssertEqual(port.compareAndSet(expected: nil, replacement: "ciphertext"), .committed)
        XCTAssertEqual(TvosRecoveryDocument(defaults: defaults, domain: name).read().encoded, "ciphertext")
        XCTAssertEqual(port.compareAndSet(expected: nil, replacement: "stale"), .conflict)
        defaults.set(42, forKey: TvosRecoveryDocument.recordKey)
        XCTAssertTrue(port.read().exists)
        XCTAssertNil(port.read().encoded)
        XCTAssertEqual(port.compareAndSet(expected: nil, replacement: "overwrite"), .conflict)
        XCTAssertTrue(port.remove())
        XCTAssertEqual(defaults.string(forKey: "unrelated")?.count, 20_000)
    }
}
