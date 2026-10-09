package com.datadragon.app.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Whether the selected folder can currently be used. */
enum class AutoBackupFolderStatus { AVAILABLE, PERMISSION_LOST, MISSING }

/** One file inside the backup folder. [id] is provider-specific. */
data class AutoBackupFolderFile(val name: String, val id: String)

/** Access to one chosen folder. Only the operations automatic backup needs. */
interface AutoBackupFolder {
    fun listFiles(): List<AutoBackupFolderFile>

    /** Creates a new file; the returned name is the one the provider actually used. */
    fun createFile(name: String, mimeType: String): AutoBackupFolderFile

    fun openOutputStream(file: AutoBackupFolderFile): OutputStream?
    fun openInputStream(file: AutoBackupFolderFile): InputStream?
    fun delete(file: AutoBackupFolderFile): Boolean
}

/** Device folder permissions and access. Android's implementation is [SafAutoBackupFolders]. */
interface AutoBackupFolderAccess {
    /** Obtains lasting read/write permission; false when Android did not grant it. */
    fun acquirePermission(uri: String): Boolean

    fun releasePermission(uri: String)
    fun status(uri: String): AutoBackupFolderStatus
    fun open(uri: String): AutoBackupFolder

    /** A human-readable folder description, never a raw content URI. */
    fun label(uri: String): String
}

/** Result of choosing a new backup folder. */
enum class AutoBackupFolderSelection {
    SELECTED,

    /** "Data Dragon doesn't have permission to use this folder." */
    PERMISSION_DENIED,

    /** "Data Dragon can't save backups to this folder." */
    CANNOT_SAVE,
}

/**
 * Internal folder check before a folder is accepted: create, write, read back,
 * and delete a small file. The file name never matches an automatic backup, so
 * rotation can never see it. Nothing about it is shown to the user.
 */
object AutoBackupFolderProbe {
    private const val PROBE_PREFIX = "datadragon_folder_check_"
    private const val PROBE_MIME = "application/octet-stream"

    fun verify(folder: AutoBackupFolder): Boolean {
        val expected = "Data Dragon folder check ${UUID.randomUUID()}".toByteArray(Charsets.UTF_8)
        var created: AutoBackupFolderFile? = null
        return try {
            val file = folder.createFile("$PROBE_PREFIX${UUID.randomUUID()}.tmp", PROBE_MIME)
            created = file
            folder.openOutputStream(file)?.use { it.write(expected); it.flush() } ?: return false
            val actual = folder.openInputStream(file)?.use { it.readBytes() } ?: return false
            if (!actual.contentEquals(expected)) return false
            val deleted = folder.delete(file)
            if (deleted) created = null
            deleted
        } catch (_: Exception) {
            false
        } finally {
            created?.let { runCatching { folder.delete(it) } }
        }
    }
}

/** Storage Access Framework implementation over a tree URI from Android's folder picker. */
class SafAutoBackupFolders(context: Context) : AutoBackupFolderAccess {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override fun acquirePermission(uri: String): Boolean {
        val tree = Uri.parse(uri)
        return try {
            resolver.takePersistableUriPermission(tree, FLAGS)
            hasPersistedPermission(tree)
        } catch (_: SecurityException) {
            false
        }
    }

    override fun releasePermission(uri: String) {
        runCatching { resolver.releasePersistableUriPermission(Uri.parse(uri), FLAGS) }
    }

    override fun status(uri: String): AutoBackupFolderStatus {
        val tree = Uri.parse(uri)
        if (!hasPersistedPermission(tree)) return AutoBackupFolderStatus.PERMISSION_LOST
        return try {
            val root = rootDocument(tree)
            resolver.query(root, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) AutoBackupFolderStatus.AVAILABLE else AutoBackupFolderStatus.MISSING }
                ?: AutoBackupFolderStatus.MISSING
        } catch (_: SecurityException) {
            AutoBackupFolderStatus.PERMISSION_LOST
        } catch (_: FileNotFoundException) {
            AutoBackupFolderStatus.MISSING
        } catch (_: IllegalArgumentException) {
            AutoBackupFolderStatus.MISSING
        } catch (_: Exception) {
            AutoBackupFolderStatus.MISSING
        }
    }

    override fun open(uri: String): AutoBackupFolder = SafFolder(Uri.parse(uri))

    override fun label(uri: String): String {
        val tree = Uri.parse(uri)
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
        if (tree.authority == EXTERNAL_STORAGE_AUTHORITY && documentId != null) {
            val path = documentId.substringAfter(':', "")
            if (path.isNotBlank()) return path
        }
        val displayName = runCatching {
            resolver.query(
                rootDocument(tree),
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        return displayName?.takeIf { it.isNotBlank() }
            ?: documentId?.substringAfterLast('/')?.substringAfter(':')?.takeIf { it.isNotBlank() }
            ?: tree.lastPathSegment.orEmpty()
    }

    private fun hasPersistedPermission(tree: Uri): Boolean =
        resolver.persistedUriPermissions.any { permission ->
            permission.uri == tree && permission.isReadPermission && permission.isWritePermission
        }

    private fun rootDocument(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private inner class SafFolder(private val tree: Uri) : AutoBackupFolder {
        private val rootId: String = DocumentsContract.getTreeDocumentId(tree)

        override fun listFiles(): List<AutoBackupFolderFile> {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            )
            return resolver.query(children, projection, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0) ?: continue
                        val id = cursor.getString(1) ?: continue
                        add(AutoBackupFolderFile(name, id))
                    }
                }
            } ?: throw FileNotFoundException("The backup folder could not be listed.")
        }

        override fun createFile(name: String, mimeType: String): AutoBackupFolderFile {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            val created = DocumentsContract.createDocument(resolver, parent, mimeType, name)
                ?: throw FileNotFoundException("The backup file could not be created.")
            val id = DocumentsContract.getDocumentId(created)
            val actualName = resolver.query(
                created,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: name
            return AutoBackupFolderFile(actualName, id)
        }

        override fun openOutputStream(file: AutoBackupFolderFile): OutputStream? =
            resolver.openOutputStream(documentUri(file), "wt")

        override fun openInputStream(file: AutoBackupFolderFile): InputStream? =
            resolver.openInputStream(documentUri(file))

        override fun delete(file: AutoBackupFolderFile): Boolean =
            DocumentsContract.deleteDocument(resolver, documentUri(file))

        private fun documentUri(file: AutoBackupFolderFile): Uri =
            DocumentsContract.buildDocumentUriUsingTree(tree, file.id)
    }

    private companion object {
        const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    }
}
