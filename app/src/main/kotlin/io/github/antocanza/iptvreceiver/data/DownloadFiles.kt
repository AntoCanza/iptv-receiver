package io.github.antocanza.iptvreceiver.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/** Only the app's dedicated media directory and explicitly granted local document trees are accepted. */
class DownloadFiles(private val context: Context) {
    private val root get() = context.filesDir.resolve("download-media")

    fun create(id: String, extension: String, folder: String?): String {
        require(id.matches(Regex("[a-f0-9]{64}")) && extension.matches(Regex("[a-z0-9]{1,8}")))
        if (folder == null) {
            if (!root.isDirectory && !root.mkdirs()) throw IOException("Download storage unavailable")
            return Uri.fromFile(root.resolve("$id.$extension")).toString()
        }
        val tree = folder.toUri()
        requireLocalTree(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return (DocumentsContract.createDocument(context.contentResolver, parent, "application/octet-stream", "$id.$extension")
            ?: throw IOException("Download folder unavailable")).toString()
    }

    fun output(location: String): DownloadOutput {
        val uri = trustedUri(location)
        return object : DownloadOutput {
            override fun length(): Long = if (uri.scheme == "file") File(checkNotNull(uri.path)).length() else
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize.takeIf { size -> size >= 0 } }
                    ?: throw IOException("Download file unavailable")

            override fun open(offset: Long): OutputStream {
                if (uri.scheme == "file") return FileOutputStream(File(checkNotNull(uri.path)), true).apply {
                    channel.truncate(offset); channel.position(offset)
                }
                val descriptor = context.contentResolver.openFileDescriptor(uri, "rw") ?: throw IOException("Download folder unavailable")
                return object : FileOutputStream(descriptor.fileDescriptor) {
                    init { try { channel.truncate(offset); channel.position(offset) } catch (error: Exception) { descriptor.close(); throw error } }
                    override fun close() { try { super.close() } finally { descriptor.close() } }
                }
            }
        }
    }

    fun exists(location: String, expectedBytes: Long): Boolean = try {
        val uri = trustedUri(location)
        if (uri.scheme == "file") File(checkNotNull(uri.path)).let { it.isFile && it.length() == expectedBytes }
        else context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize == expectedBytes } == true
    } catch (_: Exception) { false }

    fun delete(location: String) {
        val uri = trustedUri(location)
        if (uri.scheme == "file") {
            val file = File(checkNotNull(uri.path))
            if (file.exists() && !file.delete()) throw IOException("Cannot remove download")
        } else {
            try { if (DocumentsContract.deleteDocument(context.contentResolver, uri)) return }
            catch (_: Exception) { /* Confirm absence through the still-accessible parent before forgetting this entry. */ }
            val document = DocumentsContract.getDocumentId(uri)
            val parent = document.substringBeforeLast('/')
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, parent)
            val present = context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { cursor ->
                var found = false
                while (cursor.moveToNext()) if (cursor.getString(0) == document) { found = true; break }
                found
            } ?: throw IOException("Download folder unavailable")
            if (present) throw IOException("Cannot remove download")
        }
    }

    fun trustedUri(location: String): Uri {
        val uri = location.toUri()
        when (uri.scheme) {
            "file" -> {
                val file = File(checkNotNull(uri.path))
                if (java.nio.file.Files.isSymbolicLink(root.toPath()) || java.nio.file.Files.isSymbolicLink(file.toPath()) ||
                    file.canonicalFile.parentFile != root.canonicalFile) throw IOException("Invalid download location")
            }
            "content" -> {
                requireLocalTree(uri)
                val treeId = DocumentsContract.getTreeDocumentId(uri)
                val documentId = DocumentsContract.getDocumentId(uri)
                if (!documentId.startsWith("${treeId.trimEnd('/')}/")) throw IOException("Invalid download location")
            }
            else -> throw IOException("Invalid download location")
        }
        return uri
    }

    fun requireLocalTree(uri: Uri) {
        require(uri.scheme == "content" && uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isTreeUri(uri))
        val grants = context.contentResolver.persistedUriPermissions
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        require(grants.any { it.isReadPermission && it.isWritePermission && it.uri.authority == uri.authority &&
            DocumentsContract.getTreeDocumentId(it.uri) == treeId })
    }
}
