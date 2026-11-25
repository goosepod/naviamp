package app.naviamp.spike.localfolder

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Desktop effect: the grant token is an absolute path. Windows, Linux and directly distributed
 * macOS builds need nothing else; a sandboxed macOS build would resolve a bookmark here instead.
 */
class JvmFolderTreeAccess : FolderTreeAccess {
    var listingCalls = 0
        private set

    override suspend fun resolve(grant: FolderGrant): FolderGrantState {
        val root = Paths.get(grant.token)
        return when {
            Files.isDirectory(root) && Files.isReadable(root) -> FolderGrantState.Available(grant)
            Files.exists(root) -> FolderGrantState.NeedsReselection
            else -> FolderGrantState.Unreachable
        }
    }

    override fun root(grant: FolderGrant) = FolderEntry("", Paths.get(grant.token).fileName?.toString() ?: grant.token, true, null, null)

    override suspend fun children(grant: FolderGrant, directory: FolderEntry): List<FolderEntry> {
        listingCalls++
        val base = Paths.get(grant.token)
        val dir = if (directory.id.isEmpty()) base else base.resolve(directory.id)
        return Files.newDirectoryStream(dir).use { stream ->
            stream.map { child ->
                // Attributes come from the directory read on Windows; one stat per entry elsewhere.
                val attributes = Files.readAttributes(child, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                FolderEntry(
                    id = base.relativize(child).toString().replace('\\', '/'),
                    name = child.fileName.toString(),
                    isDirectory = attributes.isDirectory,
                    sizeBytes = if (attributes.isRegularFile) attributes.size() else null,
                    modifiedEpochMillis = attributes.lastModifiedTime().toMillis(),
                )
            }
        }
    }

    override fun open(grant: FolderGrant, file: FolderEntry): LocalByteSource =
        JvmFileByteSource(Paths.get(grant.token).resolve(file.id))
}

class JvmFileByteSource(path: Path) : LocalByteSource {
    private val channel = FileChannel.open(path, StandardOpenOption.READ)
    override val lengthBytes: Long = channel.size()

    override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int =
        channel.read(ByteBuffer.wrap(buffer, offset, count), position)

    override fun close() = channel.close()
}
