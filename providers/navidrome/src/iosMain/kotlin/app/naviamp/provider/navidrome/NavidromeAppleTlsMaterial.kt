@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package app.naviamp.provider.navidrome

import cnames.structs.__SecIdentity
import cnames.structs.__CFDictionary
import cnames.structs.__CFArray
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*
import platform.posix.memcpy

/** Apple Security/NSURLSession effects only; parsing and challenge decisions are common. */
internal class NavidromeAppleTlsMaterial(settings: NavidromeTlsSettings) {
    val credential: NSURLCredential? = if (settings.hasClientCertificate) {
        importIdentity(readAppleTlsFile(checkNotNull(settings.clientCertificateKeyStorePath)), settings.clientCertificateKeyStorePassword.orEmpty())
    } else null
    private val anchors = if (settings.hasCustomCertificate && !settings.insecureSkipTlsVerification) {
        navidromeCertificateDerChain(readAppleTlsFile(checkNotNull(settings.customCertificatePath)))
    } else emptyList()

    init {
        // Validate every configured certificate at client creation, rather than silently ignoring it.
        anchors.forEach { der -> withAppleTlsData(der) { data ->
            val certificate = checkNotNull(SecCertificateCreateWithData(null, data))
            CFRelease(certificate)
        } }
    }

    fun evaluateServerTrust(trust: SecTrustRef, usePeerAnchors: Boolean): Boolean {
        if (usePeerAnchors) {
            val peerChain = SecTrustCopyCertificateChain(trust) ?: return false
            return try {
                SecTrustSetAnchorCertificates(trust, peerChain) == errSecSuccess && SecTrustEvaluateWithError(trust, null)
            } finally { CFRelease(peerChain) }
        }
        val array = checkNotNull(CFArrayCreateMutable(null, 0, kCFTypeArrayCallBacks.ptr))
        try {
            anchors.forEach { der -> withAppleTlsData(der) { data ->
                val certificate = checkNotNull(SecCertificateCreateWithData(null, data))
                try { CFArrayAppendValue(array, certificate) } finally { CFRelease(certificate) }
            } }
            // Preserve NSURLSession's SSL policy, including the requested server hostname.
            return SecTrustSetAnchorCertificates(trust, array) == errSecSuccess &&
                SecTrustSetAnchorCertificatesOnly(trust, true) == errSecSuccess &&
                SecTrustEvaluateWithError(trust, null)
        } finally { CFRelease(array) }
    }
}

private fun readAppleTlsFile(path: String): ByteArray {
    val data = checkNotNull(NSData.dataWithContentsOfFile(path))
    require(data.length in 1uL..16_777_216uL)
    return ByteArray(data.length.toInt()).also { bytes -> bytes.usePinned {
        memcpy(it.addressOf(0), data.bytes, data.length)
    } }
}

private fun <T> withAppleTlsData(bytes: ByteArray, block: (CFDataRef) -> T): T = bytes.usePinned {
    require(bytes.isNotEmpty())
    val data = checkNotNull(CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong()))
    try { block(data) } finally { CFRelease(data) }
}

private fun importIdentity(bytes: ByteArray, password: String): NSURLCredential = withAppleTlsData(bytes) { data -> memScoped {
    val options = checkNotNull(CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr))
    val passphrase = checkNotNull(CFStringCreateWithCString(null, password, kCFStringEncodingUTF8))
    val items = alloc<CFArrayRefVar>().apply { value = null }
    try {
        CFDictionarySetValue(options, kSecImportExportPassphrase, passphrase)
        val status = SecPKCS12Import(data, options, items.ptr)
        if (status != errSecSuccess) error(NSError.errorWithDomain(NSOSStatusErrorDomain, status.toLong(), null).localizedDescription)
        val imported = checkNotNull(items.value)
        require(CFArrayGetCount(imported) > 0)
        val dictionary = checkNotNull(CFArrayGetValueAtIndex(imported, 0)).reinterpret<__CFDictionary>()
        val identity = checkNotNull(CFDictionaryGetValue(dictionary, kSecImportItemIdentity)).reinterpret<__SecIdentity>()
        val chain = CFDictionaryGetValue(dictionary, kSecImportItemCertChain)?.reinterpret<__CFArray>()
        val intermediates = chain?.let { array ->
            (1 until CFArrayGetCount(array)).map { index ->
                interpretObjCPointer<Any>(checkNotNull(CFArrayGetValueAtIndex(array, index)).rawValue)
            }
        }?.takeIf { it.isNotEmpty() }
        NSURLCredential.credentialWithIdentity(identity, intermediates, NSURLCredentialPersistence.NSURLCredentialPersistenceForSession)
    } finally {
        items.value?.let { CFRelease(it) }
        CFRelease(passphrase)
        CFRelease(options)
    }
} }
