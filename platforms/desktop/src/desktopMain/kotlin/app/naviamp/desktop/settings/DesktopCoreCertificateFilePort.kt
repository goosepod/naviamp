package app.naviamp.desktop.settings

import app.naviamp.presentation.NaviampCertificateFileMaxBytes
import app.naviamp.presentation.NaviampConnectionCertificateKind
import app.naviamp.presentation.NaviampCoreCertificateFilePort
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** AWT native file dialog and private JVM filesystem copy; selection policy stays in Core. */
class DesktopCoreCertificateFilePort(
    private val directory: Path,
    private val choose: suspend () -> File? = ::chooseDesktopCertificateFile,
) : NaviampCoreCertificateFilePort {
    override suspend fun chooseAndCopy(kind: NaviampConnectionCertificateKind): String? {
        val selected = choose() ?: return null
        return withContext(Dispatchers.IO) {
            val bytes = selected.inputStream().use { it.readNBytes(NaviampCertificateFileMaxBytes + 1) }
            require(bytes.size in 1..NaviampCertificateFileMaxBytes)
            Files.createDirectories(directory)
            val posix = Files.getFileStore(directory).supportsFileAttributeView("posix")
            if (posix) Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
            val destination = directory.resolve(UUID.randomUUID().toString())
            try {
                Files.write(destination, bytes)
                if (posix) Files.setPosixFilePermissions(destination, PosixFilePermissions.fromString("rw-------"))
                destination.toAbsolutePath().toString()
            } catch (failure: Throwable) { Files.deleteIfExists(destination); throw failure }
        }
    }
}

private suspend fun chooseDesktopCertificateFile(): File? =
    suspendCancellableCoroutine { continuation ->
        EventQueue.invokeLater {
            if (!continuation.isActive) return@invokeLater
            var ownedDialog: FileDialog? = null
            try {
                val dialog = FileDialog(null as Frame?, null, FileDialog.LOAD)
                ownedDialog = dialog
                continuation.invokeOnCancellation { EventQueue.invokeLater { dialog.dispose() } }
                dialog.isVisible = true
                val file = dialog.file?.let { File(dialog.directory, it) }
                if (continuation.isActive) continuation.resume(file)
            } catch (failure: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            } finally { ownedDialog?.dispose() }
        }
    }
