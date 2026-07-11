package com.example.dualfilemanager

import android.content.ContentResolver
import android.content.Context
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * All operations run through the SAF (DocumentFile / DocumentsContract) so they work
 * under Android's scoped storage rules, using only the tree permissions the user granted
 * via ACTION_OPEN_DOCUMENT_TREE.
 */
object FileOps {

    suspend fun createFolder(parent: DocumentFile, name: String): DocumentFile? =
        withContext(Dispatchers.IO) {
            parent.createDirectory(name)
        }

    suspend fun rename(doc: DocumentFile, newName: String): Boolean =
        withContext(Dispatchers.IO) {
            doc.renameTo(newName)
        }

    suspend fun delete(doc: DocumentFile): Boolean =
        withContext(Dispatchers.IO) {
            doc.delete()
        }

    /** Copies (recursively for folders) into destParent. Source is left untouched. */
    suspend fun copyInto(context: Context, source: DocumentFile, destParent: DocumentFile): Boolean =
        withContext(Dispatchers.IO) {
            copyRecursive(context.contentResolver, source, destParent)
        }

    /**
     * Moves source into destParent. Uses the fast DocumentsContract.moveDocument path when
     * both documents live under the same tree/authority; otherwise falls back to copy+delete.
     */
    suspend fun moveInto(
        context: Context,
        source: DocumentFile,
        sourceParent: DocumentFile,
        destParent: DocumentFile
    ): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val sameAuthority = source.uri.authority == destParent.uri.authority
        if (sameAuthority) {
            try {
                val moved = DocumentsContract.moveDocument(
                    resolver, source.uri, sourceParent.uri, destParent.uri
                )
                if (moved != null) return@withContext true
            } catch (_: Exception) {
                // fall through to copy+delete
            }
        }
        val copied = copyRecursive(resolver, source, destParent)
        if (copied) source.delete() else false
    }

    private fun copyRecursive(resolver: ContentResolver, source: DocumentFile, destParent: DocumentFile): Boolean {
        val name = source.name ?: return false
        return if (source.isDirectory) {
            val newDir = destParent.createDirectory(name) ?: return false
            source.listFiles().all { child -> copyRecursive(resolver, child, newDir) }
        } else {
            val mime = source.type ?: "application/octet-stream"
            val newFile = destParent.createFile(mime, name) ?: return false
            try {
                resolver.openInputStream(source.uri)?.use { input ->
                    resolver.openOutputStream(newFile.uri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } catch (_: Exception) {
                newFile.delete()
                false
            }
        }
    }
}
