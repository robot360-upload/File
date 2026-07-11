package com.example.dualfilemanager

import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SearchEngine {

    /**
     * A single match. [ancestors] is the chain of folders from the search root down to
     * (and including) the folder that directly contains [doc] — everything needed to
     * rebuild a pane's "Up" history if the user jumps straight to this result.
     */
    data class SearchResult(val doc: DocumentFile, val ancestors: List<DocumentFile>) {
        /** Path of doc relative to the search root, for display (e.g. "Notes/todo.txt"). */
        fun relativePath(): String {
            val folderNames = ancestors.drop(1).map { it.name ?: "?" }
            return (folderNames + (doc.name ?: "?")).joinToString("/")
        }
    }

    /**
     * Walks [root] depth-first, calling [onResult] (on Dispatchers.IO) for every child whose
     * name contains [query] (case-insensitive). Checks [isCancelled] between every directory
     * read so a user-triggered cancel takes effect quickly on large trees.
     */
    suspend fun search(
        root: DocumentFile,
        query: String,
        isCancelled: () -> Boolean,
        onResult: suspend (SearchResult) -> Unit
    ) = withContext(Dispatchers.IO) {

        suspend fun walk(dir: DocumentFile, chain: List<DocumentFile>) {
            if (isCancelled()) return
            val children = try {
                dir.listFiles()
            } catch (_: Exception) {
                return
            }
            for (child in children) {
                if (isCancelled()) return
                val name = child.name ?: continue
                if (name.contains(query, ignoreCase = true)) {
                    onResult(SearchResult(child, chain))
                }
                if (child.isDirectory) {
                    walk(child, chain + child)
                }
            }
        }

        walk(root, listOf(root))
    }
}
