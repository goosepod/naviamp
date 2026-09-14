package app.naviamp.desktop.connect

import app.naviamp.app.NaviampConnectDeviceIdentity
import app.naviamp.app.NaviampConnectDeviceIdentityEffect
import app.naviamp.app.NaviampConnectAdvertisingEffect
import app.naviamp.app.NaviampConnectAdvertisingListener
import app.naviamp.app.NaviampConnectAdvertisingStartResult
import app.naviamp.app.NaviampConnectDiscoveryEffect
import app.naviamp.app.NaviampConnectDiscoveryListener
import app.naviamp.app.NaviampConnectDiscoveryStartResult
import app.naviamp.app.NaviampConnectResolvedService
import app.naviamp.app.NaviampConnectRegistrationService
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
import javax.jmdns.ServiceInfo
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

/** Shared lifetime for Desktop DNS-SD browsing and registration on JmDNS's singleton instance. */
class DesktopNaviampConnectNetwork(
    private val createJmDns: () -> JmmDNS = { JmmDNS.Factory.getInstance() },
    private val closeJmDns: (JmmDNS) -> Unit = { JmmDNS.Factory.close() },
) {
    private var jmDns: JmmDNS? = null
    private var discoveryListener: NaviampConnectDiscoveryListener? = null
    private var discoveryActive = false
    private var registeredService: ServiceInfo? = null

    private val serviceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            jmDns?.requestServiceInfo(event.type, event.name, true)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            discoveryListener?.onServiceLost(event.name)
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
            }.getOrNull()?.let { discoveryListener?.onServiceResolved(it) }
        }
    }

    @Synchronized
    fun startDiscovery(listener: NaviampConnectDiscoveryListener): NaviampConnectDiscoveryStartResult {
        discoveryListener = listener
        if (discoveryActive) return NaviampConnectDiscoveryStartResult.Started
        return runCatching {
            dns().addServiceListener(DesktopServiceType, serviceListener)
            discoveryActive = true
            NaviampConnectDiscoveryStartResult.Started
        }.getOrElse { error ->
            discoveryListener = null
            closeIfIdle()
            NaviampConnectDiscoveryStartResult.Unavailable(
                error.message ?: "Desktop network service discovery is unavailable.",
            )
        }
    }

    @Synchronized
    fun stopDiscovery() {
        discoveryListener = null
        if (discoveryActive) runCatching { jmDns?.removeServiceListener(DesktopServiceType, serviceListener) }
        discoveryActive = false
        closeIfIdle()
    }

    @Synchronized
    fun startAdvertising(
        service: NaviampConnectRegistrationService,
        listener: NaviampConnectAdvertisingListener,
    ): NaviampConnectAdvertisingStartResult {
        if (registeredService != null) return NaviampConnectAdvertisingStartResult.Started
        return runCatching {
            service.toDesktopJmDnsServiceInfo().also { info ->
                dns().registerService(info)
                registeredService = info
                listener.onServiceRegistered(info.name)
            }
            NaviampConnectAdvertisingStartResult.Started
        }.getOrElse { error ->
            registeredService = null
            closeIfIdle()
            NaviampConnectAdvertisingStartResult.Unavailable(
                error.message ?: "Desktop network service registration is unavailable.",
            )
        }
    }

    @Synchronized
    fun stopAdvertising() {
        val service = registeredService
        registeredService = null
        if (service != null) runCatching { jmDns?.unregisterService(service) }
        closeIfIdle()
    }

    private fun dns(): JmmDNS = jmDns ?: createJmDns().also { jmDns = it }

    private fun closeIfIdle() {
        if (discoveryActive || registeredService != null) return
        val active = jmDns
        jmDns = null
        if (active != null) runCatching { closeJmDns(active) }
    }

    private companion object {
        val DesktopServiceType = "$NaviampConnectServiceType.local."
    }
}

/** Desktop DNS-SD browse adapter backed by the shared JmDNS lifetime. */
class DesktopNaviampConnectDiscoveryEffect(
    private val network: DesktopNaviampConnectNetwork = DesktopNaviampConnectNetwork(),
) : NaviampConnectDiscoveryEffect {
    override fun start(listener: NaviampConnectDiscoveryListener): NaviampConnectDiscoveryStartResult {
        return network.startDiscovery(listener)
    }

    override fun stop() = network.stopDiscovery()
}

/** Desktop DNS-SD registration adapter backed by the shared JmDNS lifetime. */
class DesktopNaviampConnectAdvertisingEffect(
    private val network: DesktopNaviampConnectNetwork = DesktopNaviampConnectNetwork(),
) : NaviampConnectAdvertisingEffect {
    override fun start(
        service: NaviampConnectRegistrationService,
        listener: NaviampConnectAdvertisingListener,
    ): NaviampConnectAdvertisingStartResult = network.startAdvertising(service, listener)

    override fun stop() = network.stopAdvertising()
}

internal fun NaviampConnectRegistrationService.toDesktopJmDnsServiceInfo(): ServiceInfo =
    ServiceInfo.create(
        "$NaviampConnectServiceType.local.",
        serviceName,
        port,
        0,
        0,
        textAttributes,
    )

private fun ByteArray.toHexString(): String = buildString(size * 2) {
    this@toHexString.forEach { byte ->
        val value = byte.toInt() and 0xff
        append(HexDigits[value ushr 4])
        append(HexDigits[value and 0x0f])
    }
}

private const val HexDigits = "0123456789abcdef"
