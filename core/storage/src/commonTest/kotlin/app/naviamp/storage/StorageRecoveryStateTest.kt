package app.naviamp.storage

import app.naviamp.host.*
import kotlin.test.*

class StorageRecoveryStateTest {
    @Test fun settingsRoundTripInOneProtectedDocumentWithoutPlaintext() {
        val fixture = RecoveryFixture()
        val state = fixture.open()
        state.transaction { state.write("naviamp.playback", "portable settings"); state.write("naviamp.secret", "sensitive URL") }
        assertEquals(1, fixture.document.writes)
        assertFalse(requireNotNull(fixture.document.value).contains("sensitive URL"))
        val reopened = fixture.open()
        assertEquals("portable settings", reopened.read("naviamp.playback"))
        assertEquals("sensitive URL", reopened.read("naviamp.secret"))
    }

    @Test fun utf8AndTotalNativeDomainLimitsRejectBeforeReplacingThePreviousState() {
        val fixture = RecoveryFixture(StorageRecoveryLimits(plaintextBytes = 512))
        val state = fixture.open()
        state.write("key", "old")
        val previous = fixture.document.value
        assertEquals(StorageRecoveryFailure.Capacity, assertFailsWith<StorageRecoveryException> { state.write("key", "界".repeat(200)) }.reason)
        assertEquals(previous, fixture.document.value)
        fixture.document.otherBytes = Long.MAX_VALUE
        assertEquals(StorageRecoveryFailure.Capacity, assertFailsWith<StorageRecoveryException> { state.write("key", "new") }.reason)
        assertEquals("old", state.read("key"))
        assertEquals(previous, fixture.document.value)
    }

    @Test fun nativeWriteFailureRollsBackInMemoryAndDoesNotClaimACommit() {
        val fixture = RecoveryFixture()
        val state = fixture.open()
        state.write("key", "old")
        fixture.document.fail = true
        assertEquals(StorageRecoveryFailure.NativeWrite, assertFailsWith<StorageRecoveryException> { state.write("key", "new") }.reason)
        assertEquals("old", state.read("key"))
        assertEquals("old", fixture.open().read("key"))
    }

    @Test fun staleWriterCannotReplaceAnotherCommittedDocument() {
        val fixture = RecoveryFixture()
        val first = fixture.open()
        val stale = fixture.open()
        first.write("key", "first")
        assertEquals(StorageRecoveryFailure.Conflict, assertFailsWith<StorageRecoveryException> { stale.write("key", "stale") }.reason)
        assertEquals("first", fixture.open().read("key"))
    }

    @Test fun caughtNestedFailureStillAbortsAllWrites() {
        val fixture = RecoveryFixture(StorageRecoveryLimits(plaintextBytes = 512))
        val state = fixture.open()
        assertEquals(StorageRecoveryFailure.TransactionAborted, assertFailsWith<StorageRecoveryException> {
            state.transaction {
                state.write("one", "new")
                runCatching { state.write("two", "x".repeat(1024)) }
            }
        }.reason)
        assertNull(state.read("one"))
        assertEquals(0, fixture.document.writes)
    }

    @Test fun unreadableMissingKeyAndFutureDocumentsArePreservedInsteadOfReset() {
        val fixture = RecoveryFixture()
        fixture.document.exists = true
        assertEquals(StorageRecoveryFailure.Unreadable, assertFailsWith<StorageRecoveryException> { fixture.open() }.reason)
        fixture.document.value = "not encrypted"
        assertEquals(StorageRecoveryFailure.Unreadable, assertFailsWith<StorageRecoveryException> { fixture.open() }.reason)
        fixture.document.value = fixture.protector.protect("{\"version\":2}")
        assertEquals(StorageRecoveryFailure.UnsupportedVersion, assertFailsWith<StorageRecoveryException> { fixture.open() }.reason)
        fixture.document.value = fixture.protector.protect("{\"version\":1}")
        fixture.box.available = false
        assertEquals(StorageRecoveryFailure.Unreadable, assertFailsWith<StorageRecoveryException> { fixture.open() }.reason)
        assertEquals(0, fixture.document.writes)
        assertNotNull(fixture.document.value)
    }

    @Test fun cipherFramingRejectsUnprotectedMalformedAndWrongPrincipalValues() {
        val box = RecoveryTestBox()
        val protector = StorageAeadCredentialProtector(box)
        val encoded = requireNotNull(protector.protect("credential"))
        assertEquals("credential", protector.reveal(encoded))
        assertEquals(encoded, protector.protect(encoded))
        assertNull(protector.reveal("credential"))
        assertNull(protector.reveal("naviamp-keychain-aead-v1:broken"))
        val literal = "naviamp-keychain-aead-v1:{\"keyReference\":\"test-key\",\"ciphertext\":\"Zm9yZ2Vk\"}"
        assertEquals(literal, protector.reveal(protector.protect(literal)))
        assertNull(StorageAeadCredentialProtector(RecoveryTestBox()).reveal(encoded))
        box.available = false
        assertNull(protector.protect("replacement"))
        assertNull(protector.reveal(encoded))
    }
}

/** Opaque test tokens, not cryptographic evidence; the Apple tests exercise real AEAD/Keychain. */
internal class RecoveryTestBox : StorageSecretBox {
    var available = true
    private val values = mutableMapOf<String, ByteArray>()
    private var nextToken = 0L
    override fun seal(plaintext: ByteArray): StorageSealedBytes? {
        if (!available) return null
        val token = "sealed-${nextToken++}"
        values[token] = plaintext.copyOf()
        return StorageSealedBytes("test-key", token.encodeToByteArray())
    }
    override fun open(keyReference: String, ciphertext: ByteArray): ByteArray? =
        if (available && keyReference == "test-key") values[ciphertext.decodeToString()]?.copyOf() else null
    override fun clearKey(): Boolean { values.clear(); return true }
}

internal class RecoveryTestDocument : StorageRecoveryDocumentPort {
    var value: String? = null
    var exists = false
    var writes = 0
    var fail = false
    var otherBytes = 0L
    override fun read() = StorageRecoveryDocument(exists, value)
    override fun projectedDomainBytes(encoded: String) = if (otherBytes == Long.MAX_VALUE) otherBytes else otherBytes + encoded.encodeToByteArray().size
    override fun compareAndSet(expected: String?, replacement: String?): StorageRecoveryWriteResult {
        if (value != expected || (expected == null && exists)) return StorageRecoveryWriteResult.Conflict
        if (fail) return StorageRecoveryWriteResult.Failed
        value = replacement; exists = replacement != null; writes++
        return StorageRecoveryWriteResult.Committed
    }
    override fun remove(): Boolean { value = null; exists = false; return true }
}

internal class RecoveryFixture(private val limits: StorageRecoveryLimits = StorageRecoveryLimits()) {
    val document = RecoveryTestDocument()
    val box = RecoveryTestBox()
    val protector = StorageAeadCredentialProtector(box)
    fun open(files: StorageRecoveryFiles? = null) = StorageRecoveryState(document, protector, object : StorageRecoveryLock {
        override fun lock() = Unit
        override fun unlock() = Unit
    }, limits, files)
}
