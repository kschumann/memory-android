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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS_NAME = "backup_prefs"
private const val KEY_FOLDER_URI = "folder_uri"
private const val KEY_UNHEALTHY = "backup_unhealthy"
private const val BACKUP_FILE_NAME = "memory-backup.json"
private const val TEMP_FILE_NAME = "memory-backup.json.tmp"
// Karl only wants disk-space-you-can-see, not a deep history: one dated snapshot and one
// pre-restore snapshot is enough headroom for him, not the fuller 7-14 day window R2.7 originally
// suggested. Trade-off: this shortens how far back a same-day-unnoticed disaster can be recovered
// from - see writeRollingSnapshot below.
private const val SNAPSHOT_RETENTION_DAYS = 1
private const val PRE_RESTORE_RETENTION_COUNT = 1
private val SNAPSHOT_NAME_PATTERN = Regex("""^memory-(\d{4}-\d{2}-\d{2})\.json$""")
private val PRE_RESTORE_NAME_PATTERN = Regex("""^memory-pre-restore-(\d+)\.json$""")

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
            val treeDoc = requireWritableTree(folderUri)
            val json = backupJson.encodeToString(buildCurrentExport())

            writeAtomically(treeDoc, json)
            writeRollingSnapshot(treeDoc, json)
            setHealth(BackupHealth.OK)
        } catch (e: Exception) {
            setHealth(BackupHealth.UNHEALTHY)
            throw e
        }
    }

    // R4.2/R4.3/R4.4: read and fully validate an arbitrary user-picked file before anything is
    // written anywhere. Any failure comes back as a specific, user-facing reason rather than a
    // stack trace - "which check failed" per R4.4 - and nothing is touched on failure.
    suspend fun validatePickedFile(uri: Uri): RestorePreflight = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext RestorePreflight.Rejected("Could not open the selected file")
        } catch (e: Exception) {
            return@withContext RestorePreflight.Rejected("Could not read the selected file: ${e.message}")
        }

        val export = try {
            backupJson.decodeFromString<BackupExport>(text)
        } catch (e: SerializationException) {
            return@withContext RestorePreflight.Rejected("This isn't a valid backup file (${e.message ?: "malformed JSON"})")
        } catch (e: IllegalArgumentException) {
            return@withContext RestorePreflight.Rejected("This isn't a valid backup file (${e.message ?: "malformed JSON"})")
        }

        if (export.formatVersion !in 0..CURRENT_BACKUP_FORMAT_VERSION) {
            return@withContext RestorePreflight.Rejected(
                "This backup was made with a newer version of the app (format ${export.formatVersion}, " +
                    "this version supports up to $CURRENT_BACKUP_FORMAT_VERSION)"
            )
        }

        val current = repository.getAllListsWithItems()
        RestorePreflight.Ready(
            export = export,
            fileListCount = export.lists.size,
            fileItemCount = export.lists.sumOf { it.items.size },
            currentListCount = current.size,
            currentItemCount = current.sumOf { it.items.size }
        )
    }

    // R4.6/R4.7/R4.11: snapshot current state (the undo copy) before touching anything, replace
    // in one transaction, then immediately re-export so the live file reflects the new state.
    // Requires a configured, writable backup destination - restore's only safety net is that
    // undo snapshot, so restore never proceeds without being able to write one.
    suspend fun performRestore(export: BackupExport) = withContext(Dispatchers.IO) {
        val folderUri = getSavedFolderUri() ?: error("No backup destination is configured")
        val treeDoc = requireWritableTree(folderUri)

        val undoJson = backupJson.encodeToString(buildCurrentExport())
        val undoName = "memory-pre-restore-${System.currentTimeMillis()}.json"
        val undoFile = treeDoc.createFile("application/json", undoName)
            ?: error("Could not write the pre-restore safety snapshot")
        writeAndSync(undoFile.uri, undoJson)
        pruneOldPreRestoreSnapshots(treeDoc)

        repository.replaceAllWithBackup(export)

        exportNow(folderUri)
    }

    private suspend fun buildCurrentExport(): BackupExport = BackupExport(
        formatVersion = CURRENT_BACKUP_FORMAT_VERSION,
        appVersion = currentAppVersion(),
        exportedAt = System.currentTimeMillis(),
        lists = repository.getAllListsWithItems().map { it.toExport() }
    )

    private fun requireWritableTree(folderUri: Uri): DocumentFile {
        val treeDoc = DocumentFile.fromTreeUri(context, folderUri)
        if (treeDoc == null || !treeDoc.exists() || !treeDoc.isDirectory || !treeDoc.canWrite()) {
            error("Backup folder is missing or not writable")
        }
        return treeDoc
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
    // same-day disaster can't also wipe out that day's recovery point; snapshots beyond the
    // retention window are pruned (R2.7). With SNAPSHOT_RETENTION_DAYS = 1, that recovery point
    // only reaches back to the most recent previous day - a same-day-unnoticed disaster is not
    // recoverable this way. Karl's deliberate call: he wants the footprint minimal, not the fuller
    // multi-day window R2.7 originally described.
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

    // Keeps only the most recent pre-restore safety snapshot; a restore is a rare, deliberate
    // action, not something that needs its own history the way daily snapshots do.
    private fun pruneOldPreRestoreSnapshots(treeDoc: DocumentFile) {
        val byEpochDescending = treeDoc.listFiles()
            .mapNotNull { file ->
                val match = file.name?.let { PRE_RESTORE_NAME_PATTERN.matchEntire(it) } ?: return@mapNotNull null
                match.groupValues[1].toLongOrNull()?.let { it to file }
            }
            .sortedByDescending { (epochMs, _) -> epochMs }
        byEpochDescending.drop(PRE_RESTORE_RETENTION_COUNT).forEach { (_, file) -> file.delete() }
    }
}
