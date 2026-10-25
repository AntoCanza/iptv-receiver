package io.github.antocanza.iptvreceiver.data

import java.io.File
import java.nio.file.Files

/** Only app-owned cache temporary directories are supplied here; account/database directories are excluded. */
internal fun pruneExpiredTempFiles(directory: File, cutoffMillis: Long): Int {
    if (!directory.exists() || Files.isSymbolicLink(directory.toPath())) return 0
    val root = directory.canonicalFile.toPath()
    var removed = 0
    directory.walkTopDown().onEnter { folder ->
        !Files.isSymbolicLink(folder.toPath()) && folder.canonicalFile.toPath().startsWith(root)
    }.forEach { file ->
        if (file.isFile && !Files.isSymbolicLink(file.toPath()) && file.canonicalFile.toPath().startsWith(root) &&
            file.lastModified() < cutoffMillis && file.delete()) removed++
    }
    return removed
}
