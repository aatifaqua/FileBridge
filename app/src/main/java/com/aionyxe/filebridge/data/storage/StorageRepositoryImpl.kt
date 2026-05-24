package com.aionyxe.filebridge.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.documentfile.provider.DocumentFile
import com.aionyxe.filebridge.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : StorageRepository {

    private val storageManager: StorageManager
        get() = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

    override fun listStorageRoots(): List<StorageLocation> {
        val locations = mutableListOf<StorageLocation>()
        Environment.getExternalStorageDirectory()?.let { primary ->
            locations += StorageLocation(
                displayName = context.getString(R.string.storage_label_internal),
                path = primary.absolutePath,
                isRemovable = false,
            )
        }
        for (volume in storageManager.storageVolumes) {
            if (!volume.isRemovable) continue
            val path = volume.resolvePath() ?: continue
            locations += StorageLocation(
                displayName = volume.getDescription(context) ?: "SD card",
                path = path,
                isRemovable = true,
            )
        }
        return locations
    }

    override fun isSdCardPresent(): Boolean =
        storageManager.storageVolumes.any {
            it.isRemovable && it.state == Environment.MEDIA_MOUNTED
        }

    override fun resolveRootDir(uriOrPath: String): File? {
        if (uriOrPath.isBlank()) {
            return Environment.getExternalStorageDirectory()
        }
        if (!uriOrPath.startsWith("content://")) {
            return File(uriOrPath).takeIf { it.exists() }
        }
        return resolveTreeUri(Uri.parse(uriOrPath))
    }

    override fun persistTreeUriPermission(treeUri: String) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(Uri.parse(treeUri), flags)
        }
    }

    /**
     * Best-effort mapping of an `org.android.externalstorage` tree URI to a real [File] path so the
     * java.io.File-based FTP engine can serve it.
     */
    private fun resolveTreeUri(uri: Uri): File? {
        val docId = DocumentFile.fromTreeUri(context, uri)?.let { uri.lastDocId() } ?: return null
        val parts = docId.split(':', limit = 2)
        if (parts.size < 2) return null
        val (volumeId, relativePath) = parts
        val base: File? = if (volumeId.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else {
            storageManager.storageVolumes
                .firstOrNull { it.uuid == volumeId }
                ?.resolvePath()
                ?.let { File(it) }
        }
        return base?.let { File(it, relativePath) }?.takeIf { it.exists() }
    }

    private fun Uri.lastDocId(): String? = runCatching {
        android.provider.DocumentsContract.getTreeDocumentId(this)
    }.getOrNull()

    private fun StorageVolume.resolvePath(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            directory?.absolutePath
        } else {
            runCatching {
                StorageVolume::class.java.getMethod("getPath").invoke(this) as? String
            }.getOrNull()
        }
}
