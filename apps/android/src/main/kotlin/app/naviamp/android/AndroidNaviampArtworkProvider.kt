package app.naviamp.android

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.naviamp.ui.androidPlatformCoverArtFile
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Android Auto/AAOS local-artwork URI boundary for Core-selected authenticated artwork. */
class AndroidNaviampArtworkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Naviamp artwork is read-only.")
        val artworkUrl = ArtworkRegistry.url(uri.lastPathSegment)
            ?: throw FileNotFoundException("Unknown Naviamp artwork URI.")
        val providerContext = context ?: throw FileNotFoundException("Naviamp context is unavailable.")
        val file = runBlocking(Dispatchers.IO) {
            androidPlatformCoverArtFile(providerContext, artworkUrl)
        } ?: throw FileNotFoundException("Naviamp artwork is unavailable.")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/*"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        fun uriFor(context: Context, artworkUrl: String): Uri {
            val token = ArtworkRegistry.register(artworkUrl)
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority("${context.packageName}.artwork")
                .appendPath(token)
                .build()
        }
    }
}

private object ArtworkRegistry {
    private const val MaximumEntries = 512
    private val urls = object : LinkedHashMap<String, String>(32, 0.75f, true) {}

    fun register(url: String): String = synchronized(urls) {
        val token = url.sha256()
        urls[token] = url
        while (urls.size > MaximumEntries) urls.remove(urls.keys.first())
        token
    }

    fun url(token: String?): String? = synchronized(urls) { token?.let(urls::get) }
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
