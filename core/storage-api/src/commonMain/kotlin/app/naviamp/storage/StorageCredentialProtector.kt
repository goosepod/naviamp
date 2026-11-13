package app.naviamp.storage

/** Protects persisted credential values without exposing a platform security API to shared storage. */
interface StorageCredentialProtector {
    fun protect(value: String?): String?

    fun reveal(value: String?): String?

    fun isProtected(value: String?): Boolean
}
