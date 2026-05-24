package com.aionyxe.filebridge.data.storage

import java.io.File

interface StorageRepository {
    fun listStorageRoots(): List<StorageLocation>

    fun isSdCardPresent(): Boolean

    /**
     * Resolves a setting value (either a plain filesystem path or a persisted `content://` tree URI)
     * to a [File], or null when it cannot be resolved to a real path. An empty string resolves to
     * the primary internal storage root.
     */
    fun resolveRootDir(uriOrPath: String): File?

    /** Persists read/write permission for a tree URI selected via the system document picker. */
    fun persistTreeUriPermission(treeUri: String)
}
