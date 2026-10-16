@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.ios.settings

import app.naviamp.presentation.NaviampCertificateFileMaxBytes
import app.naviamp.presentation.NaviampConnectionCertificateKind
import app.naviamp.presentation.NaviampCoreCertificateFilePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*

/** UIKit selection, security-scoped file access and an app-private durable file copy. */
class IosCoreCertificateFilePort internal constructor(
    private val directory: String,
    private val choose: suspend () -> NSURL?,
) : NaviampCoreCertificateFilePort {
    constructor(documents: IosCoreSettingsSyncPort, directory: String) : this(directory, { documents.chooseCertificateUrl() })

    override suspend fun chooseAndCopy(kind: NaviampConnectionCertificateKind): String? {
        val url = choose() ?: return null
        return withContext(Dispatchers.Default) {
            val scoped = url.startAccessingSecurityScopedResource()
            try {
                val data = checkNotNull(NSData.dataWithContentsOfURL(url, NSDataReadingMappedIfSafe, null))
                require(data.length in 1uL..NaviampCertificateFileMaxBytes.toULong())
                check(NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null))
                val destination = "$directory/${NSUUID.UUID().UUIDString}"
                check(data.writeToFile(destination, true))
                destination
            } finally { if (scoped) url.stopAccessingSecurityScopedResource() }
        }
    }
}
