@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.ios.settings

import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncFileName
import app.naviamp.domain.settings.SettingsSyncJson
import app.naviamp.presentation.NaviampCoreSettingsSyncConfiguration
import app.naviamp.presentation.NaviampCoreSettingsSyncPort
import app.naviamp.presentation.NaviampCoreSettingsValueStore
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.fsync
import platform.posix.ftell
import platform.posix.fileno
import platform.posix.fwrite
import platform.posix.remove
import platform.posix.rename
import kotlin.coroutines.resume

/** UIKit document selection and security-scoped URL access for Core-owned settings transactions. */
class IosCoreSettingsSyncPort(
    private val settingsStore: NaviampCoreSettingsValueStore,
    private val presenter: () -> UIViewController?,
) : NaviampCoreSettingsSyncPort {
    private var activePickerDelegate: IosDocumentPickerDelegate? = null

    override fun configuration(): NaviampCoreSettingsSyncConfiguration =
        NaviampCoreSettingsSyncConfiguration(
            directoryPath = settingsStore.read(KeyDirectoryReference),
            autoExportEnabled = settingsStore.read(KeyAutoExportEnabled)?.toBooleanStrictOrNull() ?: false,
        ).normalized()

    override fun saveConfiguration(configuration: NaviampCoreSettingsSyncConfiguration) {
        val normalized = configuration.normalized()
        settingsStore.write(KeyDirectoryReference, normalized.directoryPath.orEmpty())
        settingsStore.write(KeyAutoExportEnabled, normalized.autoExportEnabled.toString())
    }

    override suspend fun readDocument(directoryPath: String): SettingsSyncDocument? =
        withResolvedReference(directoryPath) { directory ->
            val file = requireNotNull(directory.URLByAppendingPathComponent(SettingsSyncFileName))
            readSettingsDocument(file, missingIsNull = true)
        }

    override suspend fun readDocumentFile(filePath: String): SettingsSyncDocument? =
        withResolvedReference(filePath) { file -> readSettingsDocument(file, missingIsNull = false) }

    override suspend fun writeDocument(
        directoryPath: String,
        document: SettingsSyncDocument,
    ): String = withResolvedReference(directoryPath) { directory ->
        val file = requireNotNull(directory.URLByAppendingPathComponent(SettingsSyncFileName))
        writeUtf8Atomically(file, SettingsSyncJson.encode(document))
        SettingsSyncFileName
    }

    override suspend fun chooseDirectory(currentPath: String?, title: String): String? =
        chooseReference(
            contentTypes = listOf(FolderContentType),
            currentPath = currentPath,
            title = title,
        )

    override suspend fun chooseDocument(currentPath: String?, title: String): String? =
        chooseReference(
            contentTypes = listOf(JsonContentType, PlainTextContentType),
            currentPath = currentPath,
            title = title,
        )

    override fun defaultDirectory(): String = configuration().directoryPath.orEmpty()

    override val available: Boolean = true

    private suspend fun chooseReference(
        contentTypes: List<String>,
        currentPath: String?,
        title: String,
    ): String? = suspendCancellableCoroutine { continuation ->
        check(activePickerDelegate == null) { "A settings document picker is already open." }
        val host = presenter() ?: error("The iOS window is not ready to present a document picker.")
        val picker = UIDocumentPickerViewController(
            documentTypes = contentTypes,
            inMode = UIDocumentPickerMode.UIDocumentPickerModeOpen,
        ).apply {
            allowsMultipleSelection = false
            shouldShowFileExtensions = true
            currentPath?.takeIf(String::isNotBlank)?.let(::resolveReference)?.let { directoryURL = it }
            this.title = title
        }
        val delegate = IosDocumentPickerDelegate { url ->
            activePickerDelegate = null
            continuation.takeIf { it.isActive }?.resume(url?.toPersistentReference())
        }
        activePickerDelegate = delegate
        picker.delegate = delegate
        continuation.invokeOnCancellation {
            activePickerDelegate = null
            picker.dismissViewControllerAnimated(true, completion = null)
        }
        host.presentViewController(picker, animated = true, completion = null)
    }

    private fun <T> withResolvedReference(reference: String, block: (NSURL) -> T): T {
        val url = resolveReference(reference)
            ?: error("The selected settings location is no longer available. Choose it again.")
        val scoped = url.startAccessingSecurityScopedResource()
        return try {
            block(url)
        } finally {
            if (scoped) url.stopAccessingSecurityScopedResource()
        }
    }

    private fun NSURL.toPersistentReference(): String {
        val bookmark = bookmarkDataWithOptions(
            options = 0uL,
            includingResourceValuesForKeys = null,
            relativeToURL = null,
            error = null,
        ) ?: error("iOS could not retain access to the selected settings location.")
        return BookmarkPrefix + bookmark.base64EncodedStringWithOptions(0uL)
    }

    private fun resolveReference(reference: String): NSURL? {
        if (!reference.startsWith(BookmarkPrefix)) {
            return if (reference.startsWith('/')) NSURL.fileURLWithPath(reference) else NSURL.URLWithString(reference)
        }
        val encoded = reference.removePrefix(BookmarkPrefix)
        val bookmark = NSData.create(base64EncodedString = encoded, options = 0uL) ?: return null
        return NSURL.URLByResolvingBookmarkData(
            bookmarkData = bookmark,
            options = 0uL,
            relativeToURL = null,
            bookmarkDataIsStale = null,
            error = null,
        )
    }
}

private class IosDocumentPickerDelegate(
    private val completed: (NSURL?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        completed(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        completed(null)
    }
}

private fun readSettingsDocument(url: NSURL, missingIsNull: Boolean): SettingsSyncDocument? {
    val path = url.path ?: error("The selected settings document is not a local file URL.")
    val handle = fopen(path, "rb")
        ?: if (missingIsNull) return null else error("The selected settings document is unavailable.")
    val text = try {
        check(fseek(handle, 0, SEEK_END) == 0) { "Could not read the settings document." }
        val size = ftell(handle)
        check(size >= 0) { "Could not read the settings document." }
        check(size <= Int.MAX_VALUE) { "The settings document is too large." }
        check(fseek(handle, 0, SEEK_SET) == 0) { "Could not read the settings document." }
        val bytes = ByteArray(size.toInt())
        if (bytes.isNotEmpty()) {
            val read = bytes.usePinned { pinned ->
                fread(pinned.addressOf(0), 1.convert(), bytes.size.convert(), handle)
            }
            check(read.toLong() == bytes.size.toLong()) { "Could not read the settings document." }
        }
        bytes.decodeToString()
    } finally {
        fclose(handle)
    }
    return runCatching { SettingsSyncJson.decode(text) }
        .getOrElse { error("The selected file is not valid Naviamp settings JSON.") }
}

private fun writeUtf8Atomically(url: NSURL, text: String) {
    val path = url.path ?: error("The selected settings folder is not a local file URL.")
    val temporaryPath = "$path.tmp"
    val handle = fopen(temporaryPath, "wb")
        ?: error("Could not create the settings sync file in that folder.")
    var handleOpen = true
    var completed = false
    try {
        val bytes = text.encodeToByteArray()
        if (bytes.isNotEmpty()) {
            val written = bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), handle)
            }
            check(written.toLong() == bytes.size.toLong()) { "Could not write the settings sync file." }
        }
        check(fflush(handle) == 0) { "Could not write the settings sync file." }
        check(fsync(fileno(handle)) == 0) { "Could not write the settings sync file." }
        check(fclose(handle) == 0) { "Could not write the settings sync file." }
        handleOpen = false
        check(rename(temporaryPath, path) == 0) { "Could not replace the settings sync file." }
        completed = true
    } finally {
        if (handleOpen) fclose(handle)
        if (!completed) remove(temporaryPath)
    }
}

private const val KeyDirectoryReference = "settingsSyncDirectoryReference"
private const val KeyAutoExportEnabled = "settingsSyncAutoExportEnabled"
private const val BookmarkPrefix = "ios-bookmark:"
private const val FolderContentType = "public.folder"
private const val JsonContentType = "public.json"
private const val PlainTextContentType = "public.plain-text"
