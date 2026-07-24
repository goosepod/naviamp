import Foundation
import NaviampShared
import Security

/// Apple Keychain effect behind Core storage's credential-protection contract.
final class IosKeychainCredentialProtector: NSObject, StorageCredentialProtector {
    private let referencePrefix = "keychain:"
    private let service = "app.naviamp.ios.provider-credentials"

    func isProtected(value: String?) -> Bool {
        value?.hasPrefix(referencePrefix) == true
    }

    func protect(value: String?) -> String? {
        guard let value, !value.isEmpty else { return value }
        if isProtected(value: value) { return value }

        let account = UUID().uuidString
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: Data(value.utf8),
        ]
        guard SecItemAdd(query as CFDictionary, nil) == errSecSuccess else { return nil }
        return referencePrefix + account
    }

    func reveal(value: String?) -> String? {
        guard let value, isProtected(value: value) else { return value }
        let account = String(value.dropFirst(referencePrefix.count))
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data
        else { return nil }
        return String(data: data, encoding: .utf8)
    }
}
