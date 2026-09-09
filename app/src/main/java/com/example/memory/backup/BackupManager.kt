package com.example.memory.backup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.example.memory.data.MemoryRepository
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS_NAME = "backup_prefs"
private const val KEY_FOLDER_URI = "folder_uri"
private const val KEY_UNHEALTHY = "backup_unhealthy"
private const val BACKUP_FILE_NAME = "memory-backup.json"
private const val TEMP_FILE_NAME = "memory-backup.json.tmp"
private const val SNAPSHOT_RETENTION_DAYS = 14
private val SNAPSHOT_NAME_PATTERN = Regex("""^memory-(\d{4}-\d{2}-\d{2})\.json$""")

// ignoreUnknownKeys: a file written by a newer app version may carry fields this build doesn't
// know about yet (R3.3) - they're dropped on read rather than failing the whole import.
private val backupJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

enum class BackupHealth { OK, UNHEALTHY }

class BackupManager(
    private val context: Context,
    private val repository: MemoryRepository
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Persisted, not just in-memory: a destination that broke should keep showing as broken
    // across app restarts until the user re-picks a folder or a write succeeds again - a silently
    // stopped backup the user never finds out about is worse than no backup (R2.8).
    private val _health = MutableStateFlow(
        if (prefs.getBoolean(KEY_UNHEALTHY, false)) BackupHealth.UNHEALTHY else BackupHealth.OK
    )
    val health: StateFlow<BackupHealth> = _health.asStateFlow()

    fun getSavedFolderUri(): Uri? =
        prefs.getString(KEY_FOLDER_URI, null)?.let { Uri.parse(it) }

    fun saveFolderUri(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
        setHealth(BackupHealth.OK)
    }

    suspend fun exportNow(folderUri: Uri) = withContext(Dispatchers.IO) {
        try {
            val treeDoc = DocumentFile.fromTreeUri(context, folderUri)
            if (treeDoc == null || !treeDoc.exists() || !treeDoc.isDirectory || !treeDoc.canWrite()) {
                error("Backup folder is missing or not writable")
            }

            val export = BackupExport(
                formatVersion = CURRENT_BACKUP_FORMAT_VERSION,
                appVersion = currentAppVersion(),
                exportedAt = System.currentTimeMillis(),
                lists = repository.getAllListsWithItems().map { it.toExport() }
            )
            val json = backupJson.encodeToString(export)

            writeAtomically(treeDoc, json)
            writeRollingSnapshot(treeDoc, json)
            setHealth(BackupHealth.OK)
        } catch (e: Exception) {
            setHealth(BackupHealth.UNHEALTHY)
            throw e
        }
    }

    // Decodes and imports a backup file's contents. Used today by the export/import round-trip
    // test; a user-facing "restore from file" flow is a later step in this series.
    suspend fun restoreFrom(json: String) = withContext(Dispatchers.IO) {
        val export = backupJson.decodeFromString<BackupExport>(json)
        if (export.formatVersion > CURRENT_BACKUP_FORMAT_VERSION) {
            throw UnsupportedBackupVersionException(export.formatVersion)
        }
        repository.importBackup(export)
    }

    private fun setHealth(health: BackupHealth) {
        _health.value = health
        prefs.edit().putBoolean(KEY_UNHEALTHY, health == BackupHealth.UNHEALTHY).apply()
    }

    private fun currentAppVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (e: PackageManager.NameNotFoundException) {
        "unknown"
    }

    // Writes to a temp file in the same SAF tree, flushes and fsyncs it, then renames it over the
    // live file name - a process kill mid-write leaves either the old complete file or the new
    // one, never a half-written one at the well-known name (R2.5).
    private fun writeAtomically(treeDoc: DocumentFile, content: String) {
        treeDoc.findFile(TEMP_FILE_NAME)?.delete()
        val tempFile = treeDoc.createFile("application/json", TEMP_FILE_NAME)
            ?: error("Could not create temp backup file")
        writeAndSync(tempFile.uri, content)

        treeDoc.findFile(BACKUP_FILE_NAME)?.delete()
        DocumentsContract.renameDocument(context.contentResolver, tempFile.uri, BACKUP_FILE_NAME)
            ?: error("Could not finalize backup file")
    }

    private fun writeAndSync(uri: Uri, content: String) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "rwt")
            ?: error("Could not open backup file for writing")
        pfd.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
                descriptor.fileDescriptor.sync()
            }
        }
    }

    // The always-current file mirrors data loss (e.g. a mis-tapped cascading list delete) within
    // milliseconds, so it isn't disaster recovery by itself. One dated snapshot per calendar day,
    // written only the first time that date is seen (never overwritten again the same day) so a
    // same-day disaster can't also wipe out today's recovery point; snapshots older than the
    // retention window are pruned (R2.7).
    private fun writeRollingSnapshot(treeDoc: DocumentFile, content: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val snapshotName = "memory-$today.json"
        if (treeDoc.findFile(snapshotName) == null) {
            treeDoc.createFile("application/json", snapshotName)?.let { writeAndSync(it.uri, content) }
        }
        pruneOldSnapshots(treeDoc)
    }

    private fun pruneOldSnapshots(treeDoc: DocumentFile) {
        val byDateDescending = treeDoc.listFiles()
            .mapNotNull { file ->
                val match = file.name?.let { SNAPSHOT_NAME_PATTERN.matchEntire(it) } ?: return@mapNotNull null
                match.groupValues[1] to file
            }
            .sortedByDescending { (date, _) -> date }
        byDateDescending.drop(SNAPSHOT_RETENTION_DAYS).forEach { (_, file) -> file.delete() }
    }
}
