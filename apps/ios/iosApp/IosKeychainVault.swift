import Foundation
import Security

/// Irreducible Apple Security-framework effect used by the shared credential contract adapter.
final class IosKeychainVault {
    private let referencePrefix = "keychain:"
    private let service = "app.naviamp.ios.provider-credentials"

    func isReference(_ value: String?) -> Bool {
        value?.hasPrefix(referencePrefix) == true
    }

    func store(_ value: String?) -> String? {
        guard let value, !value.isEmpty else { return value }
        if isReference(value) { return value }
        let account = UUID().uuidString
        let status = SecItemAdd([
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: Data(value.utf8),
        ] as CFDictionary, nil)
        return status == errSecSuccess ? referencePrefix + account : nil
    }

    func load(_ reference: String?) -> String? {
        guard let reference, isReference(reference) else { return reference }
        let account = String(reference.dropFirst(referencePrefix.count))
        var result: CFTypeRef?
        let status = SecItemCopyMatching([
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ] as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func remove(_ reference: String) {
        guard isReference(reference) else { return }
        let account = String(reference.dropFirst(referencePrefix.count))
        SecItemDelete([
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ] as CFDictionary)
    }
}
