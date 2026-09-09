package com.example.memory.backup

import com.example.memory.data.ListWithItems
import kotlinx.serialization.Serializable

// Bump this when a field is removed, renamed, or changes meaning - i.e. anything an old reader
// would misinterpret. Adding a new optional field does NOT require a bump (readers ignore unknown
// keys; missing keys fall back to their declared default). Readers must keep handling every prior
// version indefinitely, including version 0 (no formatVersion key at all) - old backup files persist
// in users' cloud storage forever and are never force-migrated.
const val CURRENT_BACKUP_FORMAT_VERSION = 1

class UnsupportedBackupVersionException(val fileVersion: Int) : Exception(
    "Backup file is format version $fileVersion, but this app only understands up to $CURRENT_BACKUP_FORMAT_VERSION"
)

@Serializable
data class ItemExport(
    // Absent on a version-0 file (written before uid existed) - the importer mints a fresh one
    // for those rows rather than failing. Never absent for anything this app writes now.
    val uid: String? = null,
    val text: String,
    val sortOrder: Int,
    val globalSortOrder: Int,
    val createdAt: Long,
    val archived: Boolean = false,
    val archivedAt: Long? = null
)

@Serializable
data class ListExport(
    val uid: String? = null,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    val items: List<ItemExport>
)

@Serializable
data class BackupExport(
    // Both default so a version-0 file (neither key present) still decodes: formatVersion reads
    // as 0, appVersion as "" (that file predates appVersion too).
    val formatVersion: Int = 0,
    val appVersion: String = "",
    val exportedAt: Long,
    val lists: List<ListExport>
)

fun ListWithItems.toExport(): ListExport = ListExport(
    uid = list.uid,
    name = list.name,
    sortOrder = list.sortOrder,
    createdAt = list.createdAt,
    items = items.sortedBy { it.sortOrder }.map { item ->
        ItemExport(
            uid = item.uid,
            text = item.text,
            sortOrder = item.sortOrder,
            globalSortOrder = item.globalSortOrder,
            createdAt = item.createdAt,
            archived = item.archived,
            archivedAt = item.archivedAt
        )
    }
)
