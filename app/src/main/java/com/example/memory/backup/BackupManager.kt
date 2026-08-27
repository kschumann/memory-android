package com.example.memory.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.memory.data.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS_NAME = "backup_prefs"
private const val KEY_FOLDER_URI = "folder_uri"
private const val BACKUP_FILE_NAME = "memory-backup.json"

private val backupJson = Json { prettyPrint = true }

class BackupManager(
    private val context: Context,
    private val repository: MemoryRepository
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedFolderUri(): Uri? =
        prefs.getString(KEY_FOLDER_URI, null)?.let { Uri.parse(it) }

    fun saveFolderUri(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
    }

    suspend fun exportNow(folderUri: Uri) = withContext(Dispatchers.IO) {
        val export = BackupExport(
            exportedAt = System.currentTimeMillis(),
            lists = repository.getAllListsWithItems().map { it.toExport() }
        )
        val json = backupJson.encodeToString(export)

        val treeDoc = DocumentFile.fromTreeUri(context, folderUri)
            ?: error("Could not open backup folder")
        treeDoc.findFile(BACKUP_FILE_NAME)?.delete()
        val file = treeDoc.createFile("application/json", BACKUP_FILE_NAME)
            ?: error("Could not create backup file")
        val stream = context.contentResolver.openOutputStream(file.uri)
            ?: error("Could not open backup file for writing")
        stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }
}
