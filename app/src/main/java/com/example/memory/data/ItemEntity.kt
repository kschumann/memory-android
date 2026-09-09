package com.example.memory.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

const val ITEM_TEXT_MAX_LENGTH = 2000

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = ListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("listId"), Index(value = ["uid"], unique = true)]
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val text: String,
    val sortOrder: Int,
    // Rank across every list's items combined, independent from sortOrder (rank within just
    // this item's own list). The flattened cross-list List View reorders this field only, so
    // dragging there never changes an item's position within its own list.
    val globalSortOrder: Int = 0,
    val createdAt: Long,
    // Archived notes are hidden from the active list (and the flattened Home List View) but kept
    // in place: sortOrder/globalSortOrder are left untouched so restoring just un-hides the row
    // where it already sorts, rather than re-inserting it somewhere new.
    val archived: Boolean = false,
    val archivedAt: Long? = null,
    // Stable identity for the export/import layer, independent of the Room-local `id`. Assigned
    // once at creation and never regenerated - not on edit, export, or re-import of a file that
    // already has one - so a restored row can be recognized as "the same row" across DB rebuilds.
    val uid: String = UUID.randomUUID().toString()
)
