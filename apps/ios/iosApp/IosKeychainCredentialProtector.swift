import NaviampShared

/// Apple Keychain effect behind Core storage's credential-protection contract.
final class IosKeychainCredentialProtector: NSObject, StorageCredentialProtector {
    private let vault = IosKeychainVault()

    func isProtected(value: String?) -> Bool {
        vault.isReference(value)
    }

    func protect(value: String?) -> String? {
        vault.store(value)
    }

    func reveal(value: String?) -> String? {
        vault.load(value)
    }
}
