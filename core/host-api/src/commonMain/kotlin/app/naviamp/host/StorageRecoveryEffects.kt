package app.naviamp.host

/** The Swift host exports these small native effects, never the application/provider graph. */
data class StorageRecoveryDocument(val exists: Boolean, val encoded: String?)

enum class StorageRecoveryWriteResult { Committed, Conflict, Failed }

interface StorageRecoveryDocumentPort {
    fun read(): StorageRecoveryDocument
    /** Size of the entire native persistent domain with this replacement, including other keys. */
    fun projectedDomainBytes(encoded: String): Long
    /** One native value replacement; expected null means absence, not an unreadable native value. */
    fun compareAndSet(expected: String?, replacement: String?): StorageRecoveryWriteResult
    /** Removes only this application's recovery record, after a shared explicit reset confirmation. */
    fun remove(): Boolean
}

/** A reentrant native lock; Core decides the transaction boundary and ordering. */
interface StorageRecoveryLock {
    fun lock()
    fun unlock()
}

data class StorageSealedBytes(val keyReference: String, val ciphertext: ByteArray)

/** Authenticated native encryption with a bounded, app/principal-scoped Keychain key. */
interface StorageSecretBox {
    fun seal(plaintext: ByteArray): StorageSealedBytes?
    fun open(keyReference: String, ciphertext: ByteArray): ByteArray?
    fun clearKey(): Boolean
}
