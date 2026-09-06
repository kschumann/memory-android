package com.example.memory.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    indices = [Index("listId")]
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
    val createdAt: Long
)
