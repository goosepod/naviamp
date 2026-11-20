package app.naviamp.storage

import app.naviamp.host.StorageRecoveryDocumentPort
import app.naviamp.host.StorageRecoveryLock
import app.naviamp.host.StorageSecretBox

/** Invoked only by the shared confirmed-reset action; never as an unreadable-state fallback. */
fun resetStorageRecovery(document: StorageRecoveryDocumentPort, box: StorageSecretBox, lock: StorageRecoveryLock): Boolean {
    lock.lock()
    return try { document.remove() && box.clearKey() } finally { lock.unlock() }
}
