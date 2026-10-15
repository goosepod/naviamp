package app.naviamp.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.naviamp.presentation.NaviampCertificateFileMaxBytes
import app.naviamp.presentation.NaviampConnectionCertificateKind
import app.naviamp.presentation.NaviampCoreCertificateFilePort
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ContentResolver document access and a durable app-private file copy. */
class AndroidCoreCertificateFilePort(
    context: Context,
    private val documents: AndroidCoreUriPicker,
    private val directory: File = File(context.filesDir, "certificates"),
) : NaviampCoreCertificateFilePort {
    private val appContext = context.applicationContext
    override suspend fun chooseAndCopy(kind: NaviampConnectionCertificateKind): String? {
        val selected = documents.choose(null, "") ?: return null
        val uri = Uri.parse(selected)
        return try { withContext(Dispatchers.IO) {
            val bytes = checkNotNull(appContext.contentResolver.openInputStream(uri)).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16_384)
                val limit = NaviampCertificateFileMaxBytes
                while (output.size() <= limit) {
                    val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                require(output.size() in 1..limit)
                output.toByteArray()
            }
            check(directory.isDirectory || directory.mkdirs())
            val file = File(directory, UUID.randomUUID().toString())
            try { file.writeBytes(bytes); file.absolutePath } catch (failure: Throwable) { file.delete(); throw failure }
        } } finally {
            runCatching { appContext.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }
}
