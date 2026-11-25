package app.naviamp.spike.localfolder

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.system.Os
import java.io.FileNotFoundException

/**
 * Android effect over a persisted SAF tree grant. Listing uses one DocumentsContract children query
 * per directory with every needed column in the projection; reads use pread on the document's file
 * descriptor so BASS can seek without copying.
 */
class SafFolderTreeAccess(private val resolver: ContentResolver) : FolderTreeAccess {
    var listingCalls = 0
        private set

    override suspend fun resolve(grant: FolderGrant): FolderGrantState {
        val tree = Uri.parse(grant.token)
        val persisted = resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
        if (!persisted) return FolderGrantState.NeedsReselection
        return try {
            resolver.query(rootDocument(tree), arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { if (it.moveToFirst()) FolderGrantState.Available(grant) else FolderGrantState.Unreachable }
                ?: FolderGrantState.Unreachable
        } catch (_: FileNotFoundException) {
            FolderGrantState.Unreachable
        } catch (_: SecurityException) {
            FolderGrantState.NeedsReselection
        }
    }

    override fun root(grant: FolderGrant): FolderEntry {
        val tree = Uri.parse(grant.token)
        return FolderEntry(DocumentsContract.getTreeDocumentId(tree), grant.displayName, true, null, null, Document.MIME_TYPE_DIR)
    }

    override suspend fun children(grant: FolderGrant, directory: FolderEntry): List<FolderEntry> {
        listingCalls++
        val tree = Uri.parse(grant.token)
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, directory.id)
        val cursor = resolver.query(uri, ChildProjection, null, null, null) ?: return emptyList()
        return cursor.use {
            val id = it.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
            val name = it.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
            val mime = it.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
            val size = it.getColumnIndexOrThrow(Document.COLUMN_SIZE)
            val modified = it.getColumnIndexOrThrow(Document.COLUMN_LAST_MODIFIED)
            buildList(it.count) {
                while (it.moveToNext()) {
                    val type = it.getString(mime)
                    add(FolderEntry(
                        id = it.getString(id),
                        name = it.getString(name) ?: "",
                        isDirectory = type == Document.MIME_TYPE_DIR,
                        sizeBytes = if (it.isNull(size)) null else it.getLong(size),
                        modifiedEpochMillis = if (it.isNull(modified)) null else it.getLong(modified).takeIf { value -> value > 0 },
                        mimeType = type,
                    ))
                }
            }
        }
    }

    override fun open(grant: FolderGrant, file: FolderEntry): LocalByteSource {
        val uri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(grant.token), file.id)
        val descriptor = resolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException(file.id)
        // Cloud and virtual providers may hand back a pipe; BASS needs a seekable descriptor.
        if (descriptor.statSize < 0) {
            descriptor.close()
            throw UnsupportedOperationException("Document is not seekable: ${file.id}")
        }
        return SafByteSource(descriptor)
    }

    private fun rootDocument(tree: Uri) = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private companion object {
        val ChildProjection = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
        )
    }
}

class SafByteSource(private val descriptor: ParcelFileDescriptor) : LocalByteSource {
    override val lengthBytes: Long = descriptor.statSize

    override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int {
        if (position >= lengthBytes) return -1
        return Os.pread(descriptor.fileDescriptor, buffer, offset, count, position)
    }

    override fun close() = descriptor.close()
}

/**
 * Device probe for the owner checklist: compares the projection query above with the per-property
 * pattern DocumentFile uses (one query per name, type, size and time). Not wired into the app.
 */
object SafEnumerationProbe {
    data class Result(val label: String, val files: Int, val directories: Int, val queries: Int, val millis: Long)

    suspend fun projection(resolver: ContentResolver, grant: FolderGrant): Result {
        val access = SafFolderTreeAccess(resolver)
        val started = SystemClock.elapsedRealtime()
        val scan = access.scan(grant)
        return Result("DocumentsContract projection", scan.files.size, scan.directories, access.listingCalls,
            SystemClock.elapsedRealtime() - started)
    }

    fun perProperty(resolver: ContentResolver, grant: FolderGrant): Result {
        val tree = Uri.parse(grant.token)
        val started = SystemClock.elapsedRealtime()
        var queries = 0
        var files = 0
        var directories = 0
        val pending = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(tree)))
        while (pending.isNotEmpty()) {
            val parent = pending.removeLast()
            directories++
            val ids = mutableListOf<String>()
            queries++
            resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent), arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { while (it.moveToNext()) ids += it.getString(0) }
            ids.forEach { id ->
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                fun column(name: String): String? {
                    queries++
                    return resolver.query(uri, arrayOf(name), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
                }
                val type = column(Document.COLUMN_MIME_TYPE)
                column(Document.COLUMN_DISPLAY_NAME)
                if (type == Document.MIME_TYPE_DIR) pending.addLast(id) else {
                    column(Document.COLUMN_SIZE)
                    column(Document.COLUMN_LAST_MODIFIED)
                    files++
                }
            }
        }
        return Result("DocumentFile-style per-property", files, directories, queries, SystemClock.elapsedRealtime() - started)
    }
}
