package app.naviamp.desktop.connect

import app.naviamp.app.NaviampConnectDeviceIdentity
import app.naviamp.app.NaviampConnectDeviceIdentityEffect
import app.naviamp.app.NaviampConnectDiscoveryEffect
import app.naviamp.app.NaviampConnectDiscoveryListener
import app.naviamp.app.NaviampConnectDiscoveryStartResult
import app.naviamp.app.NaviampConnectResolvedService
import app.naviamp.app.NaviampConnectSessionCredentialStorageEffect
import app.naviamp.app.NaviampConnectTrustStorageEffect
import app.naviamp.desktop.security.DesktopCredentialProtector
import app.naviamp.domain.connect.NaviampConnectServiceType
import app.naviamp.presentation.NaviampCoreMutableSettingsValueStore
import java.net.InetAddress
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.jmdns.JmmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

/** Desktop filesystem plus native secure-store adapters; Core owns all Connect behavior and schemas. */
class DesktopNaviampConnectStorageEffects(
    private val values: NaviampCoreMutableSettingsValueStore,
    private val protector: DesktopCredentialProtector = DesktopCredentialProtector(),
) : NaviampConnectDeviceIdentityEffect,
    NaviampConnectTrustStorageEffect,
    NaviampConnectSessionCredentialStorageEffect {
    private var cachedKeyPair: KeyPair? = null

    override fun loadOrCreate(): NaviampConnectDeviceIdentity {
        val publicKey = keyPair().public.encoded
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(publicKey).toHexString()
        return NaviampConnectDeviceIdentity(
            deviceId = fingerprint.take(32),
            identityFingerprint = fingerprint,
            publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey),
        )
    }

    override fun sign(payload: ByteArray): ByteArray = Signature.getInstance(SignatureAlgorithm).run {
        initSign(keyPair().private)
        update(payload)
        sign()
    }

    override fun read(): String? = values.read(TrustKey)

    override fun write(value: String) = values.write(TrustKey, value)

    override fun read(peerDeviceId: String): ByteArray? {
        val stored = values.read(credentialKey(peerDeviceId)) ?: return null
        if (!protector.isProtected(stored)) return null
        return protector.reveal(stored)?.let { encoded ->
            runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
        }
    }

    override fun write(peerDeviceId: String, value: ByteArray) {
        val encoded = Base64.getEncoder().encodeToString(value)
        val protected = requireNotNull(protector.protect(encoded)) {
            "Desktop could not protect the Naviamp Connect session credential."
        }
        values.write(credentialKey(peerDeviceId), protected)
    }

    override fun remove(peerDeviceId: String) = values.remove(credentialKey(peerDeviceId))

    override fun contains(peerDeviceId: String): Boolean = read(peerDeviceId) != null

    @Synchronized
    private fun keyPair(): KeyPair {
        cachedKeyPair?.let { return it }
        loadStoredKeyPair()?.let { stored ->
            cachedKeyPair = stored
            return stored
        }
        val generated = KeyPairGenerator.getInstance(KeyAlgorithm).run {
            initialize(ECGenParameterSpec(EcCurve))
            generateKeyPair()
        }
        val privateValue = Base64.getEncoder().encodeToString(generated.private.encoded)
        values.write(
            IdentityPrivateKey,
            requireNotNull(protector.protect(privateValue)) {
                "Desktop could not protect the Naviamp Connect identity."
            },
        )
        values.write(IdentityPublicKey, Base64.getEncoder().encodeToString(generated.public.encoded))
        cachedKeyPair = generated
        return generated
    }

    private fun loadStoredKeyPair(): KeyPair? = runCatching {
        val protectedPrivate = values.read(IdentityPrivateKey)
            ?.takeIf(protector::isProtected)
            ?: return null
        val privateBytes = protector.reveal(protectedPrivate)
            ?.let(Base64.getDecoder()::decode)
            ?: return null
        val publicBytes = values.read(IdentityPublicKey)
            ?.let(Base64.getDecoder()::decode)
            ?: return null
        val factory = KeyFactory.getInstance(KeyAlgorithm)
        KeyPair(
            factory.generatePublic(X509EncodedKeySpec(publicBytes)),
            factory.generatePrivate(PKCS8EncodedKeySpec(privateBytes)),
        )
    }.getOrNull()

    private fun credentialKey(peerDeviceId: String) = "$CredentialPrefix$peerDeviceId"

    private companion object {
        const val TrustKey = "naviamp.connect.trusted-devices.v1"
        const val CredentialPrefix = "naviamp.connect.session-credential.v1."
        const val IdentityPrivateKey = "naviamp.connect.identity.private.v1"
        const val IdentityPublicKey = "naviamp.connect.identity.public.v1"
        const val KeyAlgorithm = "EC"
        const val EcCurve = "secp256r1"
        const val SignatureAlgorithm = "SHA256withECDSA"
    }
}

/** Cross-platform JVM DNS-SD adapter backed by multicast DNS. */
class DesktopNaviampConnectDiscoveryEffect(
    private val createJmDns: () -> JmmDNS = { JmmDNS.Factory.getInstance() },
) : NaviampConnectDiscoveryEffect {
    @Volatile
    private var listener: NaviampConnectDiscoveryListener? = null
    private var jmDns: JmmDNS? = null

    private val serviceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            jmDns?.requestServiceInfo(event.type, event.name, true)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            listener?.onServiceLost(event.name)
        }

        override fun serviceResolved(event: ServiceEvent) {
            val info = event.info ?: return
            val addresses = info.inetAddresses
                .mapNotNull(InetAddress::getHostAddress)
                .distinct()
            if (addresses.isEmpty()) return
            val attributes = buildMap {
                val names = info.propertyNames
                while (names.hasMoreElements()) {
                    val name = names.nextElement()
                    info.getPropertyString(name)?.let { put(name, it) }
                }
            }
            runCatching {
                NaviampConnectResolvedService(
                    serviceName = event.name,
                    addresses = addresses,
                    port = info.port,
                    textAttributes = attributes,
                )
            }.getOrNull()?.let { listener?.onServiceResolved(it) }
        }
    }

    @Synchronized
    override fun start(listener: NaviampConnectDiscoveryListener): NaviampConnectDiscoveryStartResult {
        if (jmDns != null) return NaviampConnectDiscoveryStartResult.Started
        this.listener = listener
        return runCatching {
            createJmDns().also { created ->
                jmDns = created
                created.addServiceListener(DesktopServiceType, serviceListener)
            }
            NaviampConnectDiscoveryStartResult.Started
        }.getOrElse { error ->
            this.listener = null
            jmDns = null
            NaviampConnectDiscoveryStartResult.Unavailable(
                error.message ?: "Desktop network service discovery is unavailable.",
            )
        }
    }

    @Synchronized
    override fun stop() {
        val active = jmDns
        jmDns = null
        listener = null
        if (active != null) {
            runCatching { active.removeServiceListener(DesktopServiceType, serviceListener) }
            runCatching { active.close() }
        }
    }

    private companion object {
        val DesktopServiceType = "$NaviampConnectServiceType.local."
    }
}

private fun ByteArray.toHexString(): String = buildString(size * 2) {
    this@toHexString.forEach { byte ->
        val value = byte.toInt() and 0xff
        append(HexDigits[value ushr 4])
        append(HexDigits[value and 0x0f])
    }
}

private const val HexDigits = "0123456789abcdef"
