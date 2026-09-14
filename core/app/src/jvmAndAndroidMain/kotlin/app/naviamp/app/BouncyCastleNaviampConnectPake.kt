package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectPairingHandshake
import org.bouncycastle.crypto.CryptoException
import org.bouncycastle.crypto.agreement.jpake.JPAKEParticipant
import org.bouncycastle.crypto.agreement.jpake.JPAKEPrimeOrderGroups
import org.bouncycastle.crypto.agreement.jpake.JPAKERound1Payload
import org.bouncycastle.crypto.agreement.jpake.JPAKERound2Payload
import org.bouncycastle.crypto.agreement.jpake.JPAKERound3Payload
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.util.BigIntegers
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/** Shared Android/Desktop J-PAKE adapter. Bouncy Castle owns the cryptographic operations. */
object BouncyCastleNaviampConnectPakeFactory : NaviampConnectPakeFactory {
    override fun create(
        pairingSessionId: String,
        protocolVersion: Int,
        localRole: NaviampConnectPakeRole,
        localDeviceId: String,
        remoteDeviceId: String,
        pairingCode: CharArray,
    ): NaviampConnectPakeSession {
        require(pairingSessionId.isNotBlank()) { "A pairing session ID is required." }
        require(protocolVersion > 0) { "The protocol version must be positive." }
        require(localDeviceId.isNotBlank()) { "A local device ID is required." }
        require(remoteDeviceId.isNotBlank()) { "A remote device ID is required." }
        require(localDeviceId != remoteDeviceId) { "Pairing devices must have distinct IDs." }
        require(pairingCode.isNotEmpty()) { "A pairing code is required." }
        require(pairingSessionId.length <= MAX_IDENTIFIER_CHARACTERS)
        require(localDeviceId.length <= MAX_IDENTIFIER_CHARACTERS)
        require(remoteDeviceId.length <= MAX_IDENTIFIER_CHARACTERS)
        require(pairingCode.size <= MAX_PAIRING_CODE_CHARACTERS)

        return try {
            BouncyCastleNaviampConnectPakeSession(
                pairingSessionId = pairingSessionId,
                protocolVersion = protocolVersion,
                localRole = localRole,
                localDeviceId = localDeviceId,
                remoteDeviceId = remoteDeviceId,
                participant = JPAKEParticipant(
                    participantId(localRole, localDeviceId),
                    pairingCode,
                    JPAKEPrimeOrderGroups.NIST_3072,
                ),
            )
        } finally {
            pairingCode.fill('\u0000')
        }
    }

    private fun participantId(role: NaviampConnectPakeRole, deviceId: String): String =
        "${role.protocolId}:$deviceId"

    private const val MAX_IDENTIFIER_CHARACTERS = 256
    private const val MAX_PAIRING_CODE_CHARACTERS = 64
}

private class BouncyCastleNaviampConnectPakeSession(
    private val pairingSessionId: String,
    private val protocolVersion: Int,
    private val localRole: NaviampConnectPakeRole,
    private val localDeviceId: String,
    private val remoteDeviceId: String,
    participant: JPAKEParticipant,
) : NaviampConnectPakeSession {
    private val payloads = mutableMapOf<Pair<NaviampConnectPakeRole, Int>, ByteArray>()
    private var participant: JPAKEParticipant? = participant
    private var phase = Phase.Ready
    private var keyingMaterial: BigInteger? = null

    override fun start(): NaviampConnectPairingHandshake {
        ensurePhase(Phase.Ready)
        return runSafely {
            val bytes = PakePayloadCodec.encode(activeParticipant().createRound1PayloadToSend())
            payloads[localRole to 1] = bytes.copyOf()
            phase = Phase.AwaitingRound1
            handshake(step = 1, bytes = bytes)
        }
    }

    override fun receive(handshake: NaviampConnectPairingHandshake): NaviampConnectPakeProgress {
        if (handshake.pairingSessionId != pairingSessionId) {
            fail(NaviampConnectPakeFailure.InvalidSession)
        }
        return when (phase) {
            Phase.AwaitingRound1 -> receiveRound1(handshake)
            Phase.AwaitingRound2 -> receiveRound2(handshake)
            Phase.AwaitingRound3 -> receiveRound3(handshake)
            else -> fail(NaviampConnectPakeFailure.InvalidState)
        }
    }

    override fun destroy() {
        phase = Phase.Destroyed
        participant = null
        keyingMaterial = null
        payloads.values.forEach { it.fill(0) }
        payloads.clear()
    }

    private fun receiveRound1(handshake: NaviampConnectPairingHandshake): NaviampConnectPakeProgress =
        runSafely {
            requireStep(handshake, 1)
            val decoded = PakePayloadCodec.decodeRound1(handshake.payloadBase64)
            requireRemoteParticipant(decoded.participantId)
            activeParticipant().validateRound1PayloadReceived(decoded)
            rememberRemote(1, handshake.payloadBase64)

            val bytes = PakePayloadCodec.encode(activeParticipant().createRound2PayloadToSend())
            payloads[localRole to 2] = bytes.copyOf()
            phase = Phase.AwaitingRound2
            NaviampConnectPakeProgress.Send(handshake(step = 2, bytes = bytes))
        }

    private fun receiveRound2(handshake: NaviampConnectPairingHandshake): NaviampConnectPakeProgress =
        runSafely {
            requireStep(handshake, 2)
            val decoded = PakePayloadCodec.decodeRound2(handshake.payloadBase64)
            requireRemoteParticipant(decoded.participantId)
            activeParticipant().validateRound2PayloadReceived(decoded)
            rememberRemote(2, handshake.payloadBase64)

            val material = activeParticipant().calculateKeyingMaterial()
            keyingMaterial = material
            val bytes = PakePayloadCodec.encode(activeParticipant().createRound3PayloadToSend(material))
            payloads[localRole to 3] = bytes.copyOf()
            phase = Phase.AwaitingRound3
            NaviampConnectPakeProgress.Send(handshake(step = 3, bytes = bytes))
        }

    private fun receiveRound3(handshake: NaviampConnectPairingHandshake): NaviampConnectPakeProgress =
        runSafely {
            requireStep(handshake, 3)
            val decoded = PakePayloadCodec.decodeRound3(handshake.payloadBase64)
            requireRemoteParticipant(decoded.participantId)
            val material = keyingMaterial ?: fail(NaviampConnectPakeFailure.InvalidState)
            activeParticipant().validateRound3PayloadReceived(decoded, material)
            rememberRemote(3, handshake.payloadBase64)

            val derived = deriveSessionSecret(material)
            val secret = try {
                NaviampConnectSessionSecret(derived)
            } finally {
                derived.fill(0)
            }
            participant = null
            keyingMaterial = null
            payloads.values.forEach { it.fill(0) }
            payloads.clear()
            phase = Phase.Complete
            NaviampConnectPakeProgress.Complete(secret)
        }

    private fun deriveSessionSecret(material: BigInteger): ByteArray {
        val controllerId = if (localRole == NaviampConnectPakeRole.Controller) localDeviceId else remoteDeviceId
        val targetId = if (localRole == NaviampConnectPakeRole.Target) localDeviceId else remoteDeviceId
        val transcript = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeCanonicalString(KEY_CONTEXT)
                output.writeInt(protocolVersion)
                output.writeCanonicalString(pairingSessionId)
                output.writeCanonicalString(controllerId)
                output.writeCanonicalString(targetId)
                for (step in 1..3) {
                    output.writeCanonicalBytes(payloads.getValue(NaviampConnectPakeRole.Controller to step))
                    output.writeCanonicalBytes(payloads.getValue(NaviampConnectPakeRole.Target to step))
                }
            }
            buffer.toByteArray()
        }
        val transcriptHash = MessageDigest.getInstance("SHA-256").digest(transcript)
        transcript.fill(0)
        val inputKeyMaterial = BigIntegers.asUnsignedByteArray(material)
        return try {
            val generator = HKDFBytesGenerator(SHA256Digest())
            generator.init(
                HKDFParameters(
                    inputKeyMaterial,
                    transcriptHash,
                    KEY_CONTEXT.toByteArray(StandardCharsets.UTF_8),
                ),
            )
            ByteArray(SESSION_KEY_BYTES).also { generator.generateBytes(it, 0, it.size) }
        } finally {
            inputKeyMaterial.fill(0)
            transcriptHash.fill(0)
        }
    }

    private fun rememberRemote(step: Int, payloadBase64: String) {
        payloads[remoteRole() to step] = PakePayloadCodec.decodeBase64(payloadBase64)
    }

    private fun requireRemoteParticipant(actual: String) {
        val expected = "${remoteRole().protocolId}:$remoteDeviceId"
        if (actual != expected) fail(NaviampConnectPakeFailure.InvalidPayload)
    }

    private fun remoteRole(): NaviampConnectPakeRole = when (localRole) {
        NaviampConnectPakeRole.Controller -> NaviampConnectPakeRole.Target
        NaviampConnectPakeRole.Target -> NaviampConnectPakeRole.Controller
    }

    private fun requireStep(handshake: NaviampConnectPairingHandshake, expected: Int) {
        if (handshake.step != expected) fail(NaviampConnectPakeFailure.InvalidState)
    }

    private fun handshake(step: Int, bytes: ByteArray) = NaviampConnectPairingHandshake(
        pairingSessionId = pairingSessionId,
        step = step,
        payloadBase64 = Base64.getEncoder().withoutPadding().encodeToString(bytes),
    )

    private fun ensurePhase(expected: Phase) {
        if (phase != expected) fail(NaviampConnectPakeFailure.InvalidState)
    }

    private fun activeParticipant(): JPAKEParticipant = participant
        ?: fail(NaviampConnectPakeFailure.InvalidState)

    private inline fun <T> runSafely(block: () -> T): T = try {
        block()
    } catch (failure: NaviampConnectPakeException) {
        throw failure
    } catch (cause: CryptoException) {
        fail(NaviampConnectPakeFailure.AuthenticationFailed, cause)
    } catch (cause: IllegalArgumentException) {
        fail(NaviampConnectPakeFailure.InvalidPayload, cause)
    } catch (cause: IllegalStateException) {
        fail(NaviampConnectPakeFailure.InvalidState, cause)
    }

    private fun fail(failure: NaviampConnectPakeFailure, cause: Throwable? = null): Nothing {
        destroy()
        throw NaviampConnectPakeException(failure, cause)
    }

    private enum class Phase {
        Ready,
        AwaitingRound1,
        AwaitingRound2,
        AwaitingRound3,
        Complete,
        Destroyed,
    }

    private companion object {
        const val KEY_CONTEXT = "Naviamp Connect J-PAKE session key v1"
        const val SESSION_KEY_BYTES = 32
    }
}

private object PakePayloadCodec {
    private const val FORMAT_VERSION = 1
    private const val MAX_PAYLOAD_BYTES = 8_192
    private const val MAX_STRING_BYTES = 512
    private const val MAX_INTEGER_BYTES = 512
    private const val PROOF_SIZE = 2

    fun encode(payload: JPAKERound1Payload): ByteArray = encodePayload(1, payload.participantId) {
        writePositiveInteger(payload.gx1)
        writePositiveInteger(payload.gx2)
        writeProof(payload.knowledgeProofForX1)
        writeProof(payload.knowledgeProofForX2)
    }

    fun encode(payload: JPAKERound2Payload): ByteArray = encodePayload(2, payload.participantId) {
        writePositiveInteger(payload.a)
        writeProof(payload.knowledgeProofForX2s)
    }

    fun encode(payload: JPAKERound3Payload): ByteArray = encodePayload(3, payload.participantId) {
        writeSignedInteger(payload.macTag)
    }

    fun decodeRound1(payloadBase64: String): JPAKERound1Payload = decode(payloadBase64, 1) {
        JPAKERound1Payload(readString(), readPositiveInteger(), readPositiveInteger(), readProof(), readProof())
    }

    fun decodeRound2(payloadBase64: String): JPAKERound2Payload = decode(payloadBase64, 2) {
        JPAKERound2Payload(readString(), readPositiveInteger(), readProof())
    }

    fun decodeRound3(payloadBase64: String): JPAKERound3Payload = decode(payloadBase64, 3) {
        JPAKERound3Payload(readString(), readSignedInteger())
    }

    fun decodeBase64(payloadBase64: String): ByteArray {
        require(payloadBase64.length <= MAX_PAYLOAD_BYTES * 2) { "The PAKE payload is too large." }
        return Base64.getDecoder().decode(payloadBase64).also {
            require(it.isNotEmpty() && it.size <= MAX_PAYLOAD_BYTES) { "The PAKE payload size is invalid." }
        }
    }

    private fun encodePayload(
        round: Int,
        participantId: String,
        writeFields: DataOutputStream.() -> Unit,
    ): ByteArray = ByteArrayOutputStream().use { buffer ->
        DataOutputStream(buffer).use { output ->
            output.writeByte(FORMAT_VERSION)
            output.writeByte(round)
            output.writeCanonicalString(participantId)
            output.writeFields()
        }
        buffer.toByteArray().also { require(it.size <= MAX_PAYLOAD_BYTES) }
    }

    private fun <T> decode(payloadBase64: String, expectedRound: Int, reader: DataInputStream.() -> T): T {
        val bytes = decodeBase64(payloadBase64)
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readUnsignedByte() == FORMAT_VERSION)
                require(input.readUnsignedByte() == expectedRound)
                input.reader().also { require(input.available() == 0) { "The PAKE payload has trailing data." } }
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataOutputStream.writeProof(proof: Array<BigInteger>) {
        require(proof.size == PROOF_SIZE)
        proof.forEach { writePositiveInteger(it) }
    }

    private fun DataInputStream.readProof(): Array<BigInteger> =
        Array(PROOF_SIZE) { readPositiveInteger() }

    private fun DataOutputStream.writePositiveInteger(value: BigInteger) {
        require(value.signum() > 0)
        writeCanonicalBytes(BigIntegers.asUnsignedByteArray(value))
    }

    private fun DataInputStream.readPositiveInteger(): BigInteger {
        val bytes = readCanonicalBytes(MAX_INTEGER_BYTES)
        require(bytes.isNotEmpty() && bytes[0].toInt() != 0) { "The PAKE integer is not canonical." }
        return BigInteger(1, bytes).also { bytes.fill(0) }
    }

    private fun DataOutputStream.writeSignedInteger(value: BigInteger) {
        writeCanonicalBytes(value.toByteArray())
    }

    private fun DataInputStream.readSignedInteger(): BigInteger {
        val bytes = readCanonicalBytes(MAX_INTEGER_BYTES)
        return BigInteger(bytes).also { value ->
            require(value.toByteArray().contentEquals(bytes)) { "The PAKE integer is not canonical." }
            bytes.fill(0)
        }
    }

    private fun DataInputStream.readString(): String {
        val bytes = readCanonicalBytes(MAX_STRING_BYTES)
        return bytes.toString(StandardCharsets.UTF_8).also { bytes.fill(0) }
    }
}

private fun DataOutputStream.writeCanonicalString(value: String) {
    writeCanonicalBytes(value.toByteArray(StandardCharsets.UTF_8))
}

private fun DataOutputStream.writeCanonicalBytes(bytes: ByteArray) {
    writeInt(bytes.size)
    write(bytes)
}

private fun DataInputStream.readCanonicalBytes(maxBytes: Int): ByteArray {
    val size = readInt()
    require(size in 1..maxBytes) { "The encoded PAKE field size is invalid." }
    return ByteArray(size).also { readFully(it) }
}
