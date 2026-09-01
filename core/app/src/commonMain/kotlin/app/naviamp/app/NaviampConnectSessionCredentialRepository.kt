package app.naviamp.app

/** Native secure-value boundary for the secret established by an approved Connect pairing. */
interface NaviampConnectSessionCredentialStorageEffect {
    fun read(peerDeviceId: String): ByteArray?
    fun write(peerDeviceId: String, value: ByteArray)
    fun remove(peerDeviceId: String)
    fun contains(peerDeviceId: String): Boolean = read(peerDeviceId) != null
}

/** Shared ownership and defensive-copy policy for Connect session-resumption credentials. */
class NaviampConnectSessionCredentialRepository(
    private val storage: NaviampConnectSessionCredentialStorageEffect,
) {
    fun read(peerDeviceId: String): ByteArray? = storage.read(peerDeviceId)?.takeIf(ByteArray::isNotEmpty)?.copyOf()

    fun write(peerDeviceId: String, value: ByteArray) {
        require(peerDeviceId.isNotBlank()) { "A Connect credential requires a peer device ID." }
        require(value.isNotEmpty()) { "A Connect credential must not be empty." }
        val copy = value.copyOf()
        try {
            storage.write(peerDeviceId, copy)
        } finally {
            copy.fill(0)
        }
    }

    fun remove(peerDeviceId: String) = storage.remove(peerDeviceId)

    fun contains(peerDeviceId: String): Boolean = storage.contains(peerDeviceId)
}
