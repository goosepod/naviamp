import Foundation
import CryptoKit
import Security
import NaviampTvShared

/// Native synchronization primitive. Core owns transaction boundaries and reset policy.
final class TvosStorageLock: NSObject, StorageRecoveryLock {
    private let native = NSRecursiveLock()
    func lock() { native.lock() }
    func unlock() { native.unlock() }
}

/// Exactly one encrypted record in the app's standard persistent preferences domain.
final class TvosRecoveryDocument: NSObject, StorageRecoveryDocumentPort {
    static let recordKey = "naviamp.recovery.v1"
    private let defaults: UserDefaults
    private let domain: String

    init(defaults: UserDefaults = .standard, domain: String = Bundle.main.bundleIdentifier!) {
        self.defaults = defaults
        self.domain = domain
        super.init()
    }

    func read() -> StorageRecoveryDocument {
        let value = defaults.persistentDomain(forName: domain)?[Self.recordKey]
        return StorageRecoveryDocument(exists: value != nil, encoded: value as? String)
    }

    func projectedDomainBytes(encoded: String) -> Int64 {
        var values = defaults.persistentDomain(forName: domain) ?? [:]
        values[Self.recordKey] = encoded
        do {
            // Count the complete domain, including unrelated keys and plist overhead.
            let binary = try PropertyListSerialization.data(fromPropertyList: values, format: .binary, options: 0)
            let xml = try PropertyListSerialization.data(fromPropertyList: values, format: .xml, options: 0)
            return Int64(max(binary.count, xml.count))
        } catch { return -1 }
    }

    func compareAndSet(expected: String?, replacement: String?) -> StorageRecoveryWriteResult {
        let current = read()
        guard current.encoded == expected, expected != nil || !current.exists else { return .conflict }
        if let replacement { defaults.set(replacement, forKey: Self.recordKey) }
        else { defaults.removeObject(forKey: Self.recordKey) }
        let after = read()
        return after.encoded == replacement && after.exists == (replacement != nil) ? .committed : .failed
    }

    func remove() -> Bool {
        defaults.removeObject(forKey: Self.recordKey)
        return !read().exists
    }
}

/// CryptoKit AEAD plus one non-synchronizing, device-only Keychain encryption key.
final class TvosStorageSecretBox: NSObject, StorageSecretBox {
    private let service: String
    private let reference = "primary-v1"

    init(namespace: String = Bundle.main.bundleIdentifier!) {
        service = namespace + ".storage-recovery.aes-gcm.v1"
        super.init()
    }

    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service,
         kSecAttrAccount as String: reference,
         kSecAttrSynchronizable as String: false]
    }

    private func key(create: Bool) -> SymmetricKey? {
        var lookup = query
        lookup[kSecReturnData as String] = true
        lookup[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(lookup as CFDictionary, &result)
        if status == errSecSuccess {
            guard let bytes = result as? Data, bytes.count == 32 else { return nil }
            return SymmetricKey(data: bytes)
        }
        guard status == errSecItemNotFound, create else { return nil }
        let key = SymmetricKey(size: .bits256)
        var item = query
        item[kSecValueData as String] = key.withUnsafeBytes { Data($0) }
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        guard SecItemAdd(item as CFDictionary, nil) == errSecSuccess else { return nil }
        return key
    }

    func seal(plaintext: KotlinByteArray) -> StorageSealedBytes? {
        guard let key = key(create: true),
              let sealed = try? AES.GCM.seal(plaintext.nativeData, using: key, authenticating: Data(service.utf8)),
              let combined = sealed.combined else { return nil }
        return StorageSealedBytes(keyReference: reference, ciphertext: combined.kotlinBytes)
    }

    func open(keyReference: String, ciphertext: KotlinByteArray) -> KotlinByteArray? {
        guard keyReference == reference, let key = key(create: false),
              let sealed = try? AES.GCM.SealedBox(combined: ciphertext.nativeData),
              let bytes = try? AES.GCM.open(sealed, using: key, authenticating: Data(service.utf8)) else { return nil }
        return bytes.kotlinBytes
    }

    func clearKey() -> Bool {
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
}

extension KotlinByteArray {
    var nativeData: Data { Data((0..<size).map { UInt8(bitPattern: get(index: $0)) }) }
}

extension Data {
    var kotlinBytes: KotlinByteArray {
        let result = KotlinByteArray(size: Int32(count))
        for (index, value) in enumerated() { result.set(index: Int32(index), value: Int8(bitPattern: value)) }
        return result
    }
}
