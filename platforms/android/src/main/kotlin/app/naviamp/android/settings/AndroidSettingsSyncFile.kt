package app.naviamp.android

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncFileName
import app.naviamp.domain.settings.SettingsSyncJson

object AndroidSettingsSyncFile {
    fun read(context: Context, treeUri: Uri): SettingsSyncDocument? {
        val fileUri = findSyncFile(context, treeUri)?.uri ?: return null
        val text = context.contentResolver.openInputStream(fileUri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Settings sync file is unavailable. Open it in your sync provider and try Sync now.")
        return runCatching {
            SettingsSyncJson.decode(text)
        }.getOrElse {
            error("Settings sync file is not valid Naviamp settings JSON.")
        }
    }

    fun write(context: Context, treeUri: Uri, document: SettingsSyncDocument) {
        val file = findSyncFile(context, treeUri)
        if (file != null && !file.supportsWrite) {
            error("Settings sync file is read-only in this provider.")
        }
        val fileUri = file?.uri
            ?: createSyncFileUri(context, treeUri)
            ?: error("Could not create the settings sync file.")
        context.contentResolver.openOutputStream(fileUri, "wt")
            ?.bufferedWriter()
            ?.use { it.write(SettingsSyncJson.encode(document)) }
            ?: error("Settings sync file is unavailable for writing. Open it in your sync provider and try Sync now.")
    }

    private fun findSyncFile(context: Context, treeUri: Uri): ProviderSyncFile? {
        val resolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val flagsIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == SettingsSyncFileName) {
                    val flags = cursor.getInt(flagsIndex)
                    return ProviderSyncFile(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex)),
                        supportsWrite = flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0,
                    )
                }
            }
        }
        return null
    }

    private fun createSyncFileUri(context: Context, treeUri: Uri): Uri? {
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        if (!syncFolderSupportsCreate(context, parentUri)) {
            error("Selected sync folder does not allow Naviamp to create naviamp-settings.json.")
        }
        return DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            "application/json",
            SettingsSyncFileName,
        )
    }

    private fun syncFolderSupportsCreate(context: Context, folderUri: Uri): Boolean {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_FLAGS)
        context.contentResolver.query(folderUri, projection, null, null, null)?.use { cursor ->
            val flagsIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)
            if (cursor.moveToFirst()) {
                return cursor.getInt(flagsIndex) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
            }
        }
        return false
    }
}

private data class ProviderSyncFile(
    val uri: Uri,
    val supportsWrite: Boolean,
)
